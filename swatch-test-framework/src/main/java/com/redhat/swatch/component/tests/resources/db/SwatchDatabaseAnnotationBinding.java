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
package com.redhat.swatch.component.tests.resources.db;

import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.api.ServiceLifecycle;
import com.redhat.swatch.component.tests.api.SwatchDatabase;
import com.redhat.swatch.component.tests.api.extensions.AnnotationBinding;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import java.lang.annotation.Annotation;

public class SwatchDatabaseAnnotationBinding implements AnnotationBinding {

  private static final String DEFAULT_DB_HOST = "localhost";
  private static final String DEFAULT_DB_PORT = "5432";
  private static final String DEFAULT_DB_DATABASE = "rhsm-subscriptions";
  private static final String DEFAULT_DB_USERNAME = "rhsm-subscriptions";
  private static final String DEFAULT_DB_PASSWORD = "rhsm-subscriptions";

  @Override
  public boolean isFor(Annotation... annotations) {
    return findAnnotation(annotations, SwatchDatabase.class).isPresent();
  }

  @Override
  public ServiceLifecycle getLifecycle(Annotation annotation) {
    return ((SwatchDatabase) annotation).lifecycle();
  }

  @Override
  public ManagedResource getManagedResource(
      ComponentTestContext context, Service service, Annotation... annotations) {
    if (OpenShiftExtensionBootstrap.isEnabled(context)) {
      return createOpenShiftResource();
    }

    return createLocalResource();
  }

  private LocalDatabaseManagedResource createLocalResource() {
    var localConfig =
        LocalDatabaseResourceConfig.builder()
            .containerNameRegex("rhsm-subscriptions_db")
            .envVarPrefix("SWATCH_DB")
            .defaultHost(DEFAULT_DB_HOST)
            .defaultPort(DEFAULT_DB_PORT)
            .defaultDatabaseName(DEFAULT_DB_DATABASE)
            .defaultUsername(DEFAULT_DB_USERNAME)
            .defaultPassword(DEFAULT_DB_PASSWORD)
            .build();

    return new LocalDatabaseManagedResource(localConfig);
  }

  private OpenShiftDatabaseManagedResource createOpenShiftResource() {
    var osConfig =
        OpenShiftDatabaseResourceConfig.builder()
            .secretName("swatch-database-db")
            .serviceName("swatch-database-db")
            .podLabel("swatch-database")
            .portSecretKey("db.port")
            .databaseSecretKey("db.name")
            .usernameSecretKey("db.user")
            .passwordSecretKey("db.password")
            .sslModeSecretKey("db.sslmode")
            .build();

    return new OpenShiftDatabaseManagedResource(osConfig);
  }
}
