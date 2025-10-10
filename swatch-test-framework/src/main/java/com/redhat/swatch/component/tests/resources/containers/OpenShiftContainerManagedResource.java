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
package com.redhat.swatch.component.tests.resources.containers;

import static com.redhat.swatch.component.tests.utils.StringUtils.EMPTY;

import com.redhat.swatch.component.tests.api.clients.OpenshiftClient;
import com.redhat.swatch.component.tests.configuration.openshift.OpenShiftServiceConfiguration;
import com.redhat.swatch.component.tests.configuration.openshift.OpenShiftServiceConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import com.redhat.swatch.component.tests.logging.LoggingHandler;
import com.redhat.swatch.component.tests.logging.OpenShiftLoggingHandler;
import io.fabric8.kubernetes.api.model.Pod;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class OpenShiftContainerManagedResource extends ManagedResource {

  private static final String POD_LABEL = "pod";

  private final String serviceName;
  private final Map<Integer, Integer> portsMapping;
  private OpenshiftClient client;
  private LoggingHandler loggingHandler;
  private boolean running;

  // Cache for discovered pod labels to avoid repeated API calls
  private Map<String, String> discoveredPodLabels;

  // Cache for discovered service name to avoid repeated API calls
  private String discoveredServiceName;

  public OpenShiftContainerManagedResource(String serviceName, Map<Integer, Integer> portsMapping) {
    this.serviceName = serviceName;
    this.portsMapping = portsMapping;
  }

  @Override
  public String getDisplayName() {
    return serviceName;
  }

  @Override
  public void start() {
    if (running) {
      return;
    }

    client = context.get(OpenShiftExtensionBootstrap.CLIENT);
    validateService();
    loggingHandler = new OpenShiftLoggingHandler(podLabels(), containerName(), context);
    loggingHandler.startWatching();
    running = true;
  }

  @Override
  public void stop() {
    if (loggingHandler != null) {
      loggingHandler.stopWatching();
    }

    running = false;
  }

  @Override
  public String getHost() {
    return "localhost";
  }

  @Override
  public int getMappedPort(int port) {
    return client.port(
        getActualServiceName(), portsMapping.getOrDefault(port, port), context.getOwner(), podLabels());
  }

  @Override
  public boolean isRunning() {
    return loggingHandler != null && loggingHandler.logsContains(getExpectedLog());
  }

  @Override
  public List<String> logs() {
    return loggingHandler.logs();
  }

  @Override
  protected LoggingHandler getLoggingHandler() {
    return loggingHandler;
  }

  @Override
  protected void init(ServiceContext context) {
    super.init(context);
    context.loadCustomConfiguration(
        OpenShiftServiceConfiguration.class, new OpenShiftServiceConfigurationBuilder());
  }

  protected Map<String, String> podLabels() {
    // Return cached labels if available
    if (discoveredPodLabels != null) {
      return discoveredPodLabels;
    }

    // Try to discover labels from running pods
    Optional<Map<String, String>> discovered = discoverPodLabels();
    if (discovered.isPresent()) {
      discoveredPodLabels = discovered.get();
      return discoveredPodLabels;
    }

    // Fallback to default labels
    return getDefaultPodLabels();
  }

  /**
   * Gets the default pod labels when discovery fails.
   * Subclasses can override this to provide service-specific defaults.
   *
   * @return default pod labels
   */
  protected Map<String, String> getDefaultPodLabels() {
    return Map.of(POD_LABEL, serviceName);
  }

  protected String containerName() {
    return serviceName;
  }

  protected String getExpectedLog() {
    return EMPTY;
  }

  /**
   * Gets the actual service name by trying to discover it from the environment.
   *
   * <p>This method first tries to discover the real service name by examining
   * running pods and their associated services. If discovery fails, it falls
   * back to the original service name.
   *
   * @return the discovered or fallback service name
   */
  protected String getActualServiceName() {
    // Return cached service name if available
    if (discoveredServiceName != null) {
      return discoveredServiceName;
    }

    // Try to discover the actual service name
    Optional<String> discovered = discoverServiceName();
    if (discovered.isPresent()) {
      discoveredServiceName = discovered.get();
      return discoveredServiceName;
    }

    // Fallback to original service name
    return serviceName;
  }

  /**
   * Gets the current namespace from the OpenShift client context.
   *
   * @return the current namespace, or empty if unavailable
   */
  protected Optional<String> getCurrentNamespace() {
    if (context == null) {
      return Optional.empty();
    }

    OpenshiftClient client = context.get(OpenShiftExtensionBootstrap.CLIENT);
    if (client == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(client.namespace());
  }

  /**
   * Discovers pod labels by finding running pods that contain our target container.
   *
   * <p>This method searches for pods in the current namespace that contain a container
   * with the name returned by {@link #containerName()}. It then extracts the labels
   * from the first running pod found.
   *
   * @return discovered pod labels, or empty if no suitable pod is found
   */
  private Optional<Map<String, String>> discoverPodLabels() {
    if (client == null) {
      return Optional.empty();
    }

    try {
      String targetContainerName = containerName();

      // Get all pods in the namespace and find ones with our container
      List<Pod> allPods = client.underlyingClient().pods().list().getItems();

      Optional<Pod> matchingPod = allPods.stream()
          .filter(this::isPodRunning)
          .filter(pod -> podContainsContainer(pod, targetContainerName))
          .findFirst();

      if (matchingPod.isPresent()) {
        Map<String, String> labels = matchingPod.get().getMetadata().getLabels();
        if (labels != null && !labels.isEmpty()) {
          return Optional.of(Map.copyOf(labels));
        }
      }
    } catch (Exception e) {
      // Log warning but don't fail - we'll fall back to defaults
      // Note: Consider adding proper logging here
    }

    return Optional.empty();
  }

  /**
   * Checks if a pod is in running state.
   *
   * @param pod the pod to check
   * @return true if the pod is running
   */
  private boolean isPodRunning(Pod pod) {
    return pod.getStatus() != null && "Running".equals(pod.getStatus().getPhase());
  }

  /**
   * Checks if a pod contains a container with the specified name.
   *
   * @param pod the pod to check
   * @param containerName the container name to look for
   * @return true if the pod contains the container
   */
  private boolean podContainsContainer(Pod pod, String containerName) {
    return pod.getSpec() != null
        && pod.getSpec().getContainers() != null
        && pod.getSpec().getContainers().stream()
            .anyMatch(container -> containerName.equals(container.getName()));
  }

  /**
   * Discovers the actual service name by examining running services in the namespace.
   *
   * <p>This method looks for services that might be related to our target container.
   * It's particularly useful in ephemeral environments where service names might
   * follow patterns like "env-{namespace}-featureflags".
   *
   * @return discovered service name, or empty if not found
   */
  private Optional<String> discoverServiceName() {
    if (client == null) {
      return Optional.empty();
    }

    try {
      // Get all services in the namespace
      var services = client.underlyingClient().services().list().getItems();

      // First, try to find a service that exactly matches our expected name
      boolean exactMatch = services.stream()
          .anyMatch(service -> serviceName.equals(service.getMetadata().getName()));

      if (exactMatch) {
        return Optional.of(serviceName);
      }

      // If no exact match, try to find services that might be related
      // This is particularly useful for ephemeral environments
      Optional<String> relatedService = services.stream()
          .map(service -> service.getMetadata().getName())
          .filter(this::isServiceNameRelated)
          .findFirst();

      return relatedService;
    } catch (Exception e) {
      // Fall back to original service name if discovery fails
    }

    return Optional.empty();
  }

  /**
   * Checks if a service name appears to be related to our target service.
   * Subclasses can override this to provide service-specific matching logic.
   *
   * @param serviceName the service name to check
   * @return true if the service name appears related
   */
  protected boolean isServiceNameRelated(String serviceName) {
    // Default implementation: check if it contains our original service name
    return serviceName.contains(this.serviceName);
  }

  private void validateService() {
    // check whether the service does exist
    String actualServiceName = getActualServiceName();
    client.checkServiceExists(actualServiceName);
    // check whether pods do exist
    client.checkPodsExists(actualServiceName, podLabels(), containerName());
  }
}
