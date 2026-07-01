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

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Configuration for local database managed resources.
 *
 * <p>Captures configuration for local database resources:
 *
 * <ul>
 *   <li>Container name pattern for container discovery
 *   <li>Environment variable prefix for overrides
 *   <li>Default connection values used when env vars aren't set
 * </ul>
 */
@Builder
@Getter
public class LocalDatabaseResourceConfig {
  @NonNull private final String containerNameRegex;
  @NonNull private final String envVarPrefix;

  @Builder.Default private final String defaultHost = "localhost";
  @Builder.Default private final String defaultPort = "5432";
  @Builder.Default private final String defaultDatabaseName = "postgres";
  @Builder.Default private final String defaultUsername = "postgres";
  @Builder.Default private final String defaultPassword = "postgres";
  private final String defaultSslMode;
}
