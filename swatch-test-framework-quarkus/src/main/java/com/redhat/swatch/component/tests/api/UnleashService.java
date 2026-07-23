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
package com.redhat.swatch.component.tests.api;

import static com.redhat.swatch.component.tests.utils.StringUtils.EMPTY;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;

public class UnleashService extends RestService {

  public static final String ADMIN_TOKEN = "admin.token";

  private static final String PROJECT_ID = "default";
  private static final String ENVIRONMENT = "development";
  private static final String UNLEASH_BASE_PATH = "/api/admin";
  private static final String UNLEASH_FEATURES_PATH =
      UNLEASH_BASE_PATH + "/projects/" + PROJECT_ID + "/features";
  private static final String UNLEASH_ENVIRONMENT_PATH =
      UNLEASH_FEATURES_PATH + "/%s/environments/" + ENVIRONMENT;

  public void createFlag(String flag) {
    String payload =
        String.format(
            """
        {
          "name": "%s",
          "type": "operational"
        }
        """,
            flag);

    given().body(payload).when().post(UNLEASH_FEATURES_PATH).then().log().ifError();
  }

  public void enableFlag(String flag) {
    var response = given().post(UNLEASH_ENVIRONMENT_PATH.formatted(flag) + "/on");

    if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
      createFlag(flag);
      enableFlag(flag);
    }
  }

  public void disableFlag(String flag) {
    var response = given().post(UNLEASH_ENVIRONMENT_PATH.formatted(flag) + "/off");

    if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
      createFlag(flag);
      disableFlag(flag);
    }
  }

  public void setVariant(String flag, String variantName, String payloadValue) {
    String payload =
        String.format(
            """
        [{
          "name": "%s",
          "weight": 1000,
          "weightType": "variable",
          "payload": {
            "type": "string",
            "value": "%s"
          }
        }]
        """,
            variantName, payloadValue);

    var response =
        given().body(payload).when().put(UNLEASH_FEATURES_PATH + "/" + flag + "/variants");
    response.then().log().ifError();
    if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
      createFlag(flag);
      enableFlag(flag);
      setVariant(flag, variantName, payloadValue);
    }
  }

  public void clearVariants(String flag) {
    given().body("[]").when().put(UNLEASH_FEATURES_PATH + "/" + flag + "/variants");
  }

  public void listFlags() {
    given().get(UNLEASH_FEATURES_PATH).then().log().all();
  }

  public boolean isFlagEnabled(String flag) {
    return given()
        .get(UNLEASH_FEATURES_PATH + "/" + flag)
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .path("environments.find { it.name == 'development' }.enabled");
  }

  @Override
  public RequestSpecification given() {
    return super.given().header("Authorization", adminToken()).contentType(ContentType.JSON);
  }

  private String adminToken() {
    return this.getProperty(ADMIN_TOKEN, EMPTY);
  }
}
