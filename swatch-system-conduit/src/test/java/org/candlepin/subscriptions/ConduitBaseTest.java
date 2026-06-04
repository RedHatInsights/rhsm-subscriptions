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

import static org.apache.commons.codec.binary.Base64.encodeBase64;
import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    useMainMethod = SpringBootTest.UseMainMethod.ALWAYS,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "DEV_MODE=true",
      // enable grabbing metrics in tests
      "management.prometheus.metrics.export.enabled=true",
      // use a random port in management server
      "management.server.port=0",
    })
@ActiveProfiles({"rhsm-conduit", "test"})
public abstract class ConduitBaseTest {

  private static final String IDENTITY_TEMPLATE =
      "{\"identity\":{\"type\":\"User\",\"internal\":{\"org_id\":\"%s\"}}}";
  private static final String ORG_ID = "org123";
  private static final String LOCALHOST = "http://localhost:";

  @LocalServerPort private int port;

  @LocalManagementPort private int mgt;

  protected HttpEntity<Void> request() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(RH_IDENTITY_HEADER, user());

    return new HttpEntity<>(headers);
  }

  protected String user() {
    String identity = String.format(IDENTITY_TEMPLATE, ORG_ID);
    return new String(encodeBase64(identity.getBytes(StandardCharsets.UTF_8)));
  }

  protected String basePath() {
    return LOCALHOST + port;
  }

  protected String managementBasePath() {
    return LOCALHOST + mgt;
  }

  protected String apiBasePath() {
    return basePath() + "/api/rhsm-subscriptions/v1";
  }
}
