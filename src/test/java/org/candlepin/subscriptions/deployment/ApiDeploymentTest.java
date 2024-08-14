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
package org.candlepin.subscriptions.deployment;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.restassured.RestAssured;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.resource.ApiConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"api", "test"})
class ApiDeploymentTest {
  @LocalServerPort private int port;

  @BeforeEach
  public void init() {
    RestAssured.port = this.port;
  }

  @Autowired ApiConfiguration configuration;

  @Test
  void testDeployment() {
    assertNotNull(configuration);
  }

  @Test
  void testApiSupportsLargeRequests() {
    given()
        .header("X-Correlation-ID", StringUtils.repeat("Z", 20000))
        .when()
        .get("/does-not-exist")
        .then()
        .statusCode(401);
  }
}
