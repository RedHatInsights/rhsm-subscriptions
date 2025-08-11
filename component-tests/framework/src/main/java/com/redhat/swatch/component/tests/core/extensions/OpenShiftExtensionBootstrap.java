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
package com.redhat.swatch.component.tests.core.extensions;

import com.redhat.swatch.component.tests.api.RunOnOpenShift;
import com.redhat.swatch.component.tests.api.clients.OpenshiftClient;
import com.redhat.swatch.component.tests.api.extensions.ExtensionBootstrap;
import com.redhat.swatch.component.tests.configuration.ComponentTestConfiguration;
import com.redhat.swatch.component.tests.configuration.openshift.OpenShiftConfiguration;
import com.redhat.swatch.component.tests.configuration.openshift.OpenShiftConfigurationBuilder;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.core.DependencyContext;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.FileUtils;
import com.redhat.swatch.component.tests.utils.InjectUtils;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

public class OpenShiftExtensionBootstrap implements ExtensionBootstrap {
  public static final String CLIENT = "oc-client";
  public static final String TARGET_OPENSHIFT = "openshift";

  private OpenshiftClient client;

  @Override
  public boolean appliesFor(ComponentTestContext context) {
    return isEnabled(context);
  }

  @Override
  public void beforeAll(ComponentTestContext context) {
    OpenShiftConfiguration configuration =
        context.loadCustomConfiguration(TARGET_OPENSHIFT, new OpenShiftConfigurationBuilder());

    client = new OpenshiftClient();
    client.initializeClientUsingNamespace(new DefaultKubernetesClient().getNamespace());

    if (configuration.getAdditionalResources() != null) {
      for (String additionalResource : configuration.getAdditionalResources()) {
        client.apply(Path.of(additionalResource));
      }
    }
  }

  @Override
  public void afterAll(ComponentTestContext context) {
    OpenShiftConfiguration configuration = context.getConfigurationAs(OpenShiftConfiguration.class);
    client.deleteResourcesInComponentTestContext(context.getId());
    if (configuration.getAdditionalResources() != null) {
      for (String additionalResource : configuration.getAdditionalResources()) {
        client.delete(Path.of(additionalResource));
      }
    }
  }

  @Override
  public void updateServiceContext(ServiceContext context) {
    context.put(CLIENT, client);
  }

  @Override
  public List<Class<?>> supportedParameters() {
    return Arrays.asList(
        OpenshiftClient.class, OpenShiftClient.class, Deployment.class, Service.class, Route.class);
  }

  @Override
  public Optional<Object> getParameter(DependencyContext dependency) {
    if (dependency.getType() == OpenshiftClient.class) {
      return Optional.of(client);
    } else if (dependency.getType() == OpenShiftClient.class) {
      return Optional.of(client.underlyingClient());
    } else {
      // named parameters
      String named = InjectUtils.getNamedValueFromDependencyContext(dependency);
      if (named == null) {
        throw new RuntimeException(
            "To inject OpenShift resources, need to provide the name using @Named. Problematic field: "
                + dependency.getName());
      }

      if (dependency.getType() == Deployment.class) {
        return Optional.of(client.underlyingClient().apps().deployments().withName(named).get());
      } else if (dependency.getType() == Service.class) {
        return Optional.of(client.underlyingClient().services().withName(named).get());
      } else if (dependency.getType() == Route.class) {
        return Optional.of(client.underlyingClient().routes().withName(named).get());
      }
    }

    return Optional.empty();
  }

  @Override
  public void onError(ComponentTestContext context, Throwable throwable) {
    if (context.getConfigurationAs(OpenShiftConfiguration.class).isPrintInfoOnError()) {
      Log.error(
          "Test "
              + context.getRunningTestClassAndMethodName()
              + " failed. Printing diagnosis information from Openshift... ");
      Log.error("Test " + throwable + ": " + Arrays.toString(throwable.getStackTrace()));

      FileUtils.createDirectoryIfDoesNotExist(logsTestFolder(context));
      printEvents(context);
      printPodLogs(context);
    }
  }

  private void printEvents(ComponentTestContext context) {
    String events = client.getEvents();
    FileUtils.copyContentTo(events, logsTestFolder(context).resolve("events" + Log.LOG_SUFFIX));
    Log.error(events);
  }

  private void printPodLogs(ComponentTestContext context) {
    Map<String, String> logs = client.logs();
    for (Entry<String, String> podLog : logs.entrySet()) {
      FileUtils.copyContentTo(
          podLog.getValue(), logsTestFolder(context).resolve(podLog.getKey() + Log.LOG_SUFFIX));
      Log.error("Pod[%s]: '%s'", podLog.getKey(), podLog.getValue());
    }
  }

  private Path logsTestFolder(ComponentTestContext context) {
    return context.getLogFolder().resolve(context.getRunningTestClassName());
  }

  public static boolean isEnabled(ComponentTestContext context) {
    return context.isAnnotationPresent(RunOnOpenShift.class)
        || TARGET_OPENSHIFT.equals(
            context.getConfigurationAs(ComponentTestConfiguration.class).getTarget());
  }
}
