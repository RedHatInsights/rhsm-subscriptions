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
package org.candlepin.subscriptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.function.Consumer;
import org.candlepin.subscriptions.security.InsightsUserPrincipal;
import org.candlepin.subscriptions.security.InsightsUserPrincipal.Internal;
import org.candlepin.subscriptions.security.RhAssociatePrincipal;
import org.candlepin.subscriptions.security.RhAssociatePrincipal.SamlAssertions;
import org.candlepin.subscriptions.security.RhIdentity;
import org.springframework.http.HttpHeaders;

public class IntegrationTestUtils {

  static final RhIdentity ADMIN_IDENTITY = new RhIdentity();
  static final RhIdentity DEFAULT_IDENTITY = new RhIdentity();
  static final ObjectMapper mapper = new ObjectMapper();
  static final Encoder b64 = Base64.getEncoder();

  static {
    InsightsUserPrincipal user = new InsightsUserPrincipal();
    user.setAccountNumber("account123");
    Internal internal = new Internal();
    internal.setOrgId("org123");
    user.setInternal(internal);

    DEFAULT_IDENTITY.setIdentity(user);

    RhAssociatePrincipal associate = new RhAssociatePrincipal();
    SamlAssertions samlAssertions = new SamlAssertions();
    samlAssertions.setEmail("example@redhat.com");
    associate.setSamlAssertions(samlAssertions);
    ADMIN_IDENTITY.setIdentity(associate);
  }

  static Consumer<HttpHeaders> auth(RhIdentity identity) {
    return headers -> {
      if (identity != null) {
        headers.add("x-rh-identity", encodeIdentity(identity));
      }
    };
  }

  static String encodeIdentity(RhIdentity identity) {
    try {
      return b64.encodeToString(mapper.writeValueAsBytes(identity));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
