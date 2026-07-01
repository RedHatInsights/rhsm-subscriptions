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

import com.redhat.swatch.component.tests.api.HbiDatabase;
import com.redhat.swatch.component.tests.api.Service;
import com.redhat.swatch.component.tests.api.extensions.AnnotationBinding;
import com.redhat.swatch.component.tests.core.ComponentTestContext;
import com.redhat.swatch.component.tests.core.ManagedResource;
import com.redhat.swatch.component.tests.core.extensions.OpenShiftExtensionBootstrap;
import java.lang.annotation.Annotation;

/**
 * Annotation binding for {@link HbiDatabase}.
 *
 * <p>Configures HBI database connection for local (docker-compose) or OpenShift environments.
 */
public class HbiDatabaseAnnotationBinding implements AnnotationBinding {

  private static final String DEFAULT_DB_HOST = "localhost";
  private static final String DEFAULT_DB_PORT = "5432";
  private static final String DEFAULT_DB_DATABASE = "insights";
  private static final String DEFAULT_DB_USERNAME = "insights";
  private static final String DEFAULT_DB_PASSWORD = "insights";

  @Override
  public boolean isFor(Annotation... annotations) {
    return findAnnotation(annotations, HbiDatabase.class).isPresent();
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
            // Match container with underscore or dash, with optional suffix (e.g., -1)
            // Matches: rhsm-subscriptions_db, rhsm-subscriptions-db, rhsm-subscriptions-db-1, etc.
            .containerNameRegex("rhsm-subscriptions[_-]db") // Same container as Swatch DB locally
            .envVarPrefix("INVENTORY_DB") // Environment variable prefix for HBI database
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
            .secretName("swatch-tally-ct-hbi-db") // Secret created by ClowdApp
            .serviceName("swatch-tally-ct-hbi-db") // Service name in OpenShift
            .podLabel("swatch-tally-ct-hbi") // Pod selector label
            .portSecretKey("db.port")
            .databaseSecretKey("db.name")
            .usernameSecretKey("db.user")
            .passwordSecretKey("db.password")
            .sslModeSecretKey("db.sslmode")
            .build();

    return new OpenShiftDatabaseManagedResource(osConfig);
  }
}
