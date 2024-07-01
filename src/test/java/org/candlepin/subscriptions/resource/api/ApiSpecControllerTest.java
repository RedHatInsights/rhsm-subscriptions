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
package org.candlepin.subscriptions.resource.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
class ApiSpecControllerTest {
  @Autowired ApiSpecController controller;

  @Test
  void testOpenApiJson() {
    /* Tests that we receive a successful non-empty response */
    String json = controller.getOpenApiV1Json();
    assertNotEquals(0, json.length());
  }

  @Test
  void testOpenApiYaml() {
    /* Tests that we receive a successful non-empty response */
    String yaml = controller.getOpenApiV1Yaml();
    assertNotEquals(0, yaml.length());
  }

  @Test
  void testInternalOpenApiJson() {
    /* Tests that we receive a successful non-empty response */
    String json = controller.getInternalSubSyncApiJson();
    assertNotEquals(0, json.length());

    json = controller.getInternalTallyApiJson();
    assertNotEquals(0, json.length());
  }

  @Test
  void testInternalOpenApiYaml() {
    /* Tests that we receive a successful non-empty response */
    String yaml = controller.getInternalSubSyncApiYaml();
    assertNotEquals(0, yaml.length());

    yaml = controller.getInternalTallyApiYaml();
    assertNotEquals(0, yaml.length());
  }
}
