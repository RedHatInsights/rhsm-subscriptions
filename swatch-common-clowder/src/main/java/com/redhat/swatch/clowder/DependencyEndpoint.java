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
package com.redhat.swatch.clowder;

/**
 * Resolved Clowder endpoint metadata for one dependency version (V1 or V2).
 *
 * <p>{@code authenticated} is {@code null} when the Clowder field is absent. Explicit {@code true}
 * or {@code false} both count as present.
 */
public record DependencyEndpoint(
    String uri,
    String trustStorePath,
    String trustStorePassword,
    String trustStoreType,
    Boolean authenticated) {

  public static DependencyEndpoint of(
      String uri,
      String trustStorePath,
      String trustStorePassword,
      String trustStoreType,
      Boolean authenticated) {
    return new DependencyEndpoint(
        uri,
        nullToEmpty(trustStorePath),
        nullToEmpty(trustStorePassword),
        nullToEmpty(trustStoreType),
        authenticated);
  }

  public static DependencyEndpoint v1(
      String uri, String trustStorePath, String trustStorePassword, String trustStoreType) {
    return of(uri, trustStorePath, trustStorePassword, trustStoreType, null);
  }

  /** Parses config string values. Blank means authenticated was not set in Clowder. */
  public static Boolean parseAuthenticated(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Boolean.parseBoolean(value.trim());
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
