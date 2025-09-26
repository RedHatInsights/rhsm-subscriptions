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
package com.redhat.swatch.component.tests.core;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.api.configuration.ServiceListener;
import com.redhat.swatch.component.tests.configuration.ServiceConfiguration;
import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.FileUtils;
import com.redhat.swatch.component.tests.utils.PropertiesUtils;
import com.redhat.swatch.component.tests.utils.ServiceLoaderUtils;
import com.redhat.swatch.component.tests.utils.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class BaseService<T extends Service> implements Service {
  private final List<ServiceListener> listeners = ServiceLoaderUtils.load(ServiceListener.class);

  private final Map<String, String> properties = new HashMap<>();
  private final List<Runnable> futureProperties = new LinkedList<>();

  private ManagedResource managedResource;
  private String serviceName;
  private ServiceContext context;

  @Override
  public String getContextId() {
    return context.getComponentTestContext().getId();
  }

  @Override
  public String getName() {
    return serviceName;
  }

  @Override
  public String getDisplayName() {
    return managedResource.getDisplayName();
  }

  /**
   * The runtime configuration property to be used if the built artifact is configured to be run.
   */
  public T withProperties(String... propertiesFiles) {
    properties.clear();
    Stream.of(propertiesFiles).map(PropertiesUtils::toMap).forEach(properties::putAll);
    return (T) this;
  }

  /**
   * The runtime configuration property to be used if the built artifact is configured to be run.
   */
  @Override
  public T withProperty(String key, String value) {
    this.properties.put(key, value);
    return (T) this;
  }

  /**
   * The runtime configuration property to be used if the built artifact is configured to be run.
   */
  public T withProperty(String key, Supplier<String> value) {
    futureProperties.add(() -> properties.put(key, value.get()));
    return (T) this;
  }

  @Override
  public boolean isRunning() {
    if (managedResource == null) {
      return false;
    }

    return managedResource.isRunning();
  }

  @Override
  public String getHost() {
    return managedResource.getHost();
  }

  @Override
  public int getMappedPort(int port) {
    return managedResource.getMappedPort(port);
  }

  @Override
  public ServiceConfiguration getConfiguration() {
    return context.getConfiguration();
  }

  @Override
  public Map<String, String> getProperties() {
    return Collections.unmodifiableMap(properties);
  }

  @Override
  public List<String> getLogs() {
    return new ArrayList<>(managedResource.logs());
  }

  @Override
  public String getProperty(String property, String defaultValue) {
    String value = getProperties().get(property);
    if (StringUtils.isNotEmpty(value)) {
      return value;
    }

    String computedValue = managedResource.getProperty(property);
    if (StringUtils.isNotEmpty(computedValue)) {
      return computedValue;
    }

    return defaultValue;
  }

  /**
   * Start the managed resource. If the managed resource is running, it does nothing.
   *
   * @throws RuntimeException when application errors at startup.
   */
  @Override
  public void start() {
    if (isRunning()) {
      return;
    }

    Log.trace(this, "Starting service (%s)", getDisplayName());
    doStart();
    Log.info(this, "Service started (%s)", getDisplayName());
  }

  /** Stop the application. */
  @Override
  public void stop() {
    if (!isRunning()) {
      return;
    }

    Log.debug(this, "Stopping service (%s)", getDisplayName());
    listeners.forEach(ext -> ext.onServiceStopped(context));
    managedResource.stop();

    Log.info(this, "Service stopped (%s)", getDisplayName());
  }

  /** Let JUnit close remaining resources. */
  @Override
  public void close() {
    if (!context.getComponentTestContext().isDebug()) {
      if (isRunning()) {
        stop();
      }

      if (!context.getComponentTestContext().isFailed()) {
        try {
          FileUtils.deletePath(getServiceFolder());
        } catch (Exception ex) {
          Log.warn(this, "Could not delete service folder. Caused by " + ex.getMessage());
        }
      }
    }
  }

  @Override
  public ServiceContext register(String serviceName, ComponentTestContext context) {
    this.serviceName = serviceName;
    this.context = new ServiceContext(this, context);
    context.getTestStore().put(serviceName, this);
    return this.context;
  }

  @Override
  public void init(ManagedResource managedResource) {
    this.managedResource = managedResource;
    FileUtils.recreateDirectory(context.getServiceFolder());
    this.managedResource.init(context);
    this.managedResource.validate();
  }

  public Path getServiceFolder() {
    return context.getServiceFolder();
  }

  @Override
  public void onTestStarted() {
    this.managedResource.getLoggingHandler().onTestStarted();
  }

  private void doStart() {
    try {
      managedResource.start();
      managedResource.waitUntilResourceIsStarted();
      listeners.forEach(ext -> ext.onServiceStarted(context));
    } catch (Exception ex) {
      listeners.forEach(ext -> ext.onServiceError(context, ex));
      throw ex;
    }
  }
}
