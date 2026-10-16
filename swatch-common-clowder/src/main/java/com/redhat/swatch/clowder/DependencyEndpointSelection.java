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
 * Chooses a Clowder dependency endpoint from V1/V2 metadata and an explicit mode.
 *
 * <ul>
 *   <li>{@code auto}: prefer usable V2, otherwise V1
 *   <li>{@code v1}: force V1 even when V2 metadata exists
 *   <li>{@code v2}: require usable V2 (fail closed when missing)
 * </ul>
 *
 * <p>V2 is usable when URI is non-blank and {@code authenticated} is present (true or false).
 */
public record DependencyEndpointSelection(
    String mode, String version, DependencyEndpoint endpoint) {

  public static final String MODE_AUTO = "auto";
  public static final String MODE_V1 = "v1";
  public static final String MODE_V2 = "v2";
  public static final String VERSION_V1 = "v1";
  public static final String VERSION_V2 = "v2";

  public String uri() {
    return endpoint == null ? null : endpoint.uri();
  }

  public String trustStorePath() {
    return endpoint == null ? "" : endpoint.trustStorePath();
  }

  public String trustStorePassword() {
    return endpoint == null ? "" : endpoint.trustStorePassword();
  }

  public String trustStoreType() {
    return endpoint == null ? "" : endpoint.trustStoreType();
  }

  public static DependencyEndpointSelection select(
      String mode, DependencyEndpoint v1, DependencyEndpoint v2) {
    String normalizedMode = normalizeMode(mode);
    if (MODE_V1.equals(normalizedMode)) {
      return v1(normalizedMode, v1);
    }
    if (MODE_V2.equals(normalizedMode)) {
      if (!isUsableV2(v2)) {
        throw new IllegalStateException(
            "endpoint mode=v2 but Clowder V2 URI or authenticated metadata is missing");
      }
      return v2(normalizedMode, v2);
    }
    if (isUsableV2(v2)) {
      return v2(normalizedMode, v2);
    }
    return v1(normalizedMode, v1);
  }

  public static DependencyEndpointSelection v1Endpoint(DependencyEndpoint v1) {
    return v1(MODE_AUTO, v1);
  }

  private static DependencyEndpointSelection v1(String mode, DependencyEndpoint endpoint) {
    DependencyEndpoint resolved =
        endpoint == null ? DependencyEndpoint.v1(null, null, null, null) : endpoint;
    return new DependencyEndpointSelection(mode, VERSION_V1, resolved);
  }

  private static DependencyEndpointSelection v2(String mode, DependencyEndpoint endpoint) {
    return new DependencyEndpointSelection(mode, VERSION_V2, endpoint);
  }

  private static boolean isUsableV2(DependencyEndpoint v2) {
    return v2 != null && hasText(v2.uri()) && v2.authenticated() != null;
  }

  private static String normalizeMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return MODE_AUTO;
    }
    String normalized = mode.trim().toLowerCase();
    if (MODE_V1.equals(normalized) || MODE_V2.equals(normalized)) {
      return normalized;
    }
    return MODE_AUTO;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
