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
package com.redhat.swatch.contract.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class RhIdentityUtils {
  public static final String ASSOCIATE_IDENTITY_JSON =
      """
            {
              "identity": {
                "type": "Associate",
                "associate" : {
                  "email": "test@example.com"
                }
              }
            }
            """;

  public static final String ASSOCIATE_IDENTITY_HEADER = generateHeader(ASSOCIATE_IDENTITY_JSON);
  public static final String X509_IDENTITY_JSON =
      """
            {
              "identity": {
                "type": "X509",
                "x509" : {
                  "subject_dn": "CN=test.example.com"
                }
              }
            }
            """;

  public static final String X509_IDENTITY_HEADER = generateHeader(X509_IDENTITY_JSON);

  public static final String CUSTOMER_IDENTITY_JSON =
      """
            {
              "identity": {
                "type": "User",
                "org_id": "org123"
              }
            }
            """;

  public static final String CUSTOMER_IDENTITY_HEADER = generateHeader(CUSTOMER_IDENTITY_JSON);

  static String generateHeader(String json) {
    return new String(
        Base64.getEncoder().encode(json.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
  }
}
