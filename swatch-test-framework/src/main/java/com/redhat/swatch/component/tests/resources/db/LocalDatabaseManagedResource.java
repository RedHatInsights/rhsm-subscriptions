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

import com.redhat.swatch.component.tests.resources.containers.LocalContainerManagedResource;

/**
 * Generic local database managed resource that works for any database service.
 *
 * <p>Uses configuration-driven approach to eliminate per-database boilerplate.
 */
public class LocalDatabaseManagedResource extends LocalContainerManagedResource {

  private static final int POSTGRESQL_PORT = 5432;

  private final LocalDatabaseResourceConfig localConfig;

  public LocalDatabaseManagedResource(LocalDatabaseResourceConfig localConfig) {
    super(localConfig.getContainerNameRegex());
    this.localConfig = localConfig;
  }

  @Override
  public void start() {
    String host;
    String port;

    String envHostKey = localConfig.getEnvVarPrefix() + "_DB_HOST";
    if (System.getenv(envHostKey) != null) {
      host = env(envHostKey, localConfig.getDefaultHost());
      port = env(localConfig.getEnvVarPrefix() + "_DB_PORT", localConfig.getDefaultPort());
    } else {
      super.start();
      host = getHost();
      port = String.valueOf(getMappedPort(POSTGRESQL_PORT));
    }

    setProperty("host", host);
    setProperty("port", port);
    setProperty(
        "database",
        env(localConfig.getEnvVarPrefix() + "_DB_NAME", localConfig.getDefaultDatabaseName()));
    setProperty(
        "username",
        env(localConfig.getEnvVarPrefix() + "_DB_USER", localConfig.getDefaultUsername()));
    setProperty(
        "password",
        env(localConfig.getEnvVarPrefix() + "_DB_PASSWORD", localConfig.getDefaultPassword()));
    setProperty(
        "sslmode",
        env(localConfig.getEnvVarPrefix() + "_DB_SSLMODE", localConfig.getDefaultSslMode()));
  }

  private void setProperty(String name, String value) {
    context.getOwner().withProperty(name, value);
  }

  private String env(String name, String defaultValue) {
    String v = System.getenv(name);
    return v == null || v.isBlank() ? defaultValue : v;
  }
}
