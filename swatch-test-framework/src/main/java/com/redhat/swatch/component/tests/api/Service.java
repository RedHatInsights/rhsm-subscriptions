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
package com.redhat.swatch.component.tests.api;

import com.redhat.swatch.component.tests.api.extensions.AnnotationBinding;
import com.redhat.swatch.component.tests.configuration.ServiceConfiguration;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.ServiceContext;
import com.redhat.swatch.component.tests.utils.LogsVerifier;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Service extends AutoCloseable {

  String getContextId();

  String getName();

  String getDisplayName();

  ServiceConfiguration getConfiguration();

  Map<String, String> getProperties();

  String getProperty(String property, String defaultValue);

  default Optional<String> getProperty(String property) {
    return Optional.ofNullable(getProperty(property, null));
  }

  List<String> getLogs();

  ServiceContext register(String serviceName, ComponentTestContext context);

  void init(ManagedResource resource);

  void start();

  void stop();

  @Override
  void close();

  String getHost();

  int getMappedPort(int port);

  boolean isRunning();

  Service withProperty(String key, String value);

  default void onTestStarted() {}

  default LogsVerifier logs() {
    return new LogsVerifier(this);
  }

  default void validate(AnnotationBinding binding, Annotation[] annotations) {}
}
