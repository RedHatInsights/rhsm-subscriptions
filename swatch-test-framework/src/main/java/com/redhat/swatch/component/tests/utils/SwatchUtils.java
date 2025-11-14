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
package com.redhat.swatch.component.tests.utils;

import java.util.Map;

public final class SwatchUtils {
  public static final String SERVER_PORT_PROPERTY = "SERVER_PORT";
  public static final int SERVER_PORT = 8000;
  public static final int MANAGEMENT_PORT = 9000;
  public static final Map<String, String> SECURITY_HEADERS =
      Map.of(
          "x-rh-swatch-psk", "placeholder",
          "Origin", "console.redhat.com");

  private SwatchUtils() {}

  public static String securityHeadersWithServiceRole(String orgId) {
    // Use X509 type authentication like IQE does, which grants "service" role automatically
    // which also avoids RBAC validation issues in test environments
    // For X509 auth, the system uses subject_dn as the principal name (which becomes the orgId)
    // So we put the orgId in the subject_dn field
    String json =
        "{\"identity\":{\"type\":\"X509\",\"auth_type\":\"X509\","
            + "\"x509\":{\"subject_dn\":\""
            + orgId
            + "\",\"issuer_dn\":\"CN=test-issuer\"}},"
            + "\"entitlements\":{\"rhel\":{\"is_entitled\":true}}}";
    String rhId =
        java.util.Base64.getEncoder()
            .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    return rhId;
  }
}
