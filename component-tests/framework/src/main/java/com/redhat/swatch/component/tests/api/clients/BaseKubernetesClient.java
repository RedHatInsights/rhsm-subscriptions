/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.swatch.component.tests.api.clients;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.exceptions.ContainerNotFoundInPodException;
import com.redhat.swatch.component.tests.exceptions.PodsNotFoundException;
import com.redhat.swatch.component.tests.exceptions.PodsNotReadyException;
import com.redhat.swatch.component.tests.exceptions.ServiceNotFoundException;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.KeyValueEntry;
import com.redhat.swatch.component.tests.utils.SocketUtils;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class BaseKubernetesClient<
    T extends io.fabric8.kubernetes.client.KubernetesClient> {

  private final Map<String, KeyValueEntry<Service, LocalPortForwardWrapper>> portForwardsByService =
      new HashMap<>();

  private String currentNamespace;
  private T client;

  public abstract T initializeClient(Config config);

  /**
   * @return the fabric8 kubernetes client that is used internally.
   */
  public T underlyingClient() {
    return client;
  }

  /**
   * @return the current namespace
   */
  public String namespace() {
    return currentNamespace;
  }

  /** Apply the file into Kubernetes. */
  public void apply(Path file) {
    try (FileInputStream is = new FileInputStream(file.toFile())) {
      Log.info(
          "Applying file at '%s' in namespace '%s'",
          file.toAbsolutePath().toString(), currentNamespace);
      client.load(is).createOrReplace();
    } catch (Exception e) {
      throw new RuntimeException("Failed to apply resource " + file.toAbsolutePath(), e);
    }
  }

  /** Delete the file into Kubernetes. */
  public void delete(Path file) {
    try (FileInputStream is = new FileInputStream(file.toFile())) {
      Log.info(
          "Deleting file at '%s' in namespace '%s'",
          file.toAbsolutePath().toString(), currentNamespace);
      client.load(is).delete();
    } catch (Exception e) {
      throw new RuntimeException("Failed to apply resource " + file.toAbsolutePath(), e);
    }
  }

  /** Get the running pods in the current service. */
  public List<Pod> podsInService(Map<String, String> podLabels) {
    return client.pods().withLabels(podLabels).list().getItems();
  }

  /** Get all the logs for all the pods within the current namespace. */
  public Map<String, String> logs() {
    Map<String, String> logs = new HashMap<>();
    for (Pod pod : client.pods().list().getItems()) {
      String podName = pod.getMetadata().getName();
      try {
        logs.put(podName, client.pods().withName(podName).getLog());
      } catch (Exception ignored) {
        // the pod contains multiple container, and we don't support this use case yet, ignoring
        // exception.
      }
    }

    return logs;
  }

  /** Get all the logs for all the pods within one service. */
  public Map<String, String> logs(Map<String, String> podLabels, String containerName) {
    Map<String, String> logs = new HashMap<>();
    for (Pod pod : podsInService(podLabels)) {
      if (isPodRunning(pod)) {
        String podName = pod.getMetadata().getName();
        logs.put(podName, client.pods().withName(podName).inContainer(containerName).getLog());
      }
    }

    return logs;
  }

  /** Resolve the port by the service. */
  public int port(String serviceName, int port, Service service, Map<String, String> podLabels) {
    String svcPortForwardKey = serviceName + "-" + port;
    KeyValueEntry<Service, LocalPortForwardWrapper> portForwardByService =
        portForwardsByService.get(svcPortForwardKey);
    if (portForwardByService == null || portForwardByService.getValue().needsToRecreate()) {
      closePortForward(portForwardByService);
      LocalPortForward process =
          client
              .services()
              .withName(serviceName)
              .portForward(port, SocketUtils.findAvailablePort(service));
      Log.trace(service, "Opening port forward from local port " + process.getLocalPort());

      portForwardByService =
          new KeyValueEntry<>(service, new LocalPortForwardWrapper(process, podLabels));
      portForwardsByService.put(svcPortForwardKey, portForwardByService);
    }

    return portForwardByService.getValue().localPort;
  }

  /** Delete all the resources within the test. */
  public void deleteResourcesInComponentTestContext(String contextId) {
    portForwardsByService.values().forEach(this::closePortForward);
  }

  public void checkServiceExists(String serviceName) {
    var serviceModel = client.services().withName(serviceName).get();
    if (serviceModel == null
        || serviceModel.getSpec() == null
        || serviceModel.getSpec().getPorts() == null) {
      throw new ServiceNotFoundException(serviceName);
    }
  }

  public void checkPodsExists(
      String serviceName, Map<String, String> podLabels, String containerName) {
    var pods = podsInService(podLabels);
    if (pods.isEmpty()) {
      throw new PodsNotFoundException(serviceName, podLabels);
    }
    for (Pod pod : pods) {
      if (!isPodRunning(pod)) {
        throw new PodsNotReadyException(serviceName, podLabels);
      }

      if (pod.getSpec().getContainers().stream()
          .noneMatch(c -> c.getName().equals(containerName))) {
        throw new ContainerNotFoundInPodException(serviceName, containerName);
      }
    }
  }

  private boolean isPodRunning(Pod pod) {
    return pod.getStatus().getPhase().equals("Running");
  }

  private void closePortForward(KeyValueEntry<Service, LocalPortForwardWrapper> portForward) {
    if (portForward != null) {
      int localPort = portForward.getValue().localPort;
      Log.trace(portForward.getKey(), "Closing port forward using local port " + localPort);
      try {
        portForward.getValue().process.close();
        AwaitilityUtils.untilIsFalse(portForward.getValue().process::isAlive);
      } catch (IOException ex) {
        Log.warn("Failed to close port forward " + localPort, ex);
      }
    }
  }

  public void initializeClientUsingNamespace(String namespace) {
    Log.info("Using namespace '%s'", namespace);
    currentNamespace = namespace;
    Config config =
        new ConfigBuilder().withTrustCerts(true).withNamespace(currentNamespace).build();
    client = (T) initializeClient(config);
  }

  class LocalPortForwardWrapper {
    int localPort;
    LocalPortForward process;
    Map<String, String> podLabels;
    Set<String> podIds;

    LocalPortForwardWrapper(LocalPortForward process, Map<String, String> podLabels) {
      this.localPort = process.getLocalPort();
      this.process = process;
      this.podLabels = podLabels;
      this.podIds = resolvePodIds();
    }

    /** Needs to recreate the port forward if the pods have changed or the process was stopped. */
    boolean needsToRecreate() {
      if (!process.isAlive()) {
        return true;
      }

      Set<String> newPodIds = resolvePodIds();
      return !podIds.containsAll(newPodIds);
    }

    private Set<String> resolvePodIds() {
      return podsInService(podLabels).stream()
          .map(p -> p.getMetadata().getName())
          .collect(Collectors.toSet());
    }
  }
}
