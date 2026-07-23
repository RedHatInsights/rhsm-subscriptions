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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.model.InfoFeatureFlag;
import com.redhat.swatch.component.tests.model.InfoFeatureFlags;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
  private static final String DISABLED_VARIANT = "disabled";

  private SwatchService swatchService;

  /**
   * Sets the Swatch service whose management {@code /info} endpoint is used to wait until feature
   * flag changes are reflected.
   */
  @SuppressWarnings("unchecked")
  public <T extends UnleashService> T withSwatchService(SwatchService swatchService) {
    this.swatchService = swatchService;
    return (T) this;
  }

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
      return;
    }
    waitForFlag(flag, true, null, null);
  }

  public void disableFlag(String flag) {
    var response = given().post(UNLEASH_ENVIRONMENT_PATH.formatted(flag) + "/off");

    if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
      createFlag(flag);
      disableFlag(flag);
      return;
    }
    waitForFlag(flag, false, null, null);
  }

  public void setVariant(String flag, String variantName, String payloadValue) {
    String value = payloadValue == null ? "" : payloadValue;
    String payload =
        JsonUtils.toJson(
            List.of(
                Map.of(
                    "name",
                    variantName,
                    "weight",
                    1000,
                    "weightType",
                    "variable",
                    "payload",
                    Map.of("type", "string", "value", value))));

    var response =
        given().body(payload).when().put(UNLEASH_FEATURES_PATH + "/" + flag + "/variants");
    response.then().log().ifError();
    if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
      createFlag(flag);
      enableFlag(flag);
      setVariant(flag, variantName, payloadValue);
      return;
    }
    waitForFlag(flag, isFlagEnabled(flag), variantName, payloadValue);
  }

  public void clearVariants(String flag) {
    given()
        .body("[]")
        .when()
        .put(UNLEASH_FEATURES_PATH + "/" + flag + "/variants")
        .then()
        .log()
        .ifError();
    waitForFlag(flag, isFlagEnabled(flag), DISABLED_VARIANT, null);
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

  private void waitForFlag(
      String flag, boolean expectedEnabled, String expectedVariantName, String expectedPayload) {
    if (swatchService == null) {
      throw new IllegalStateException(
          "UnleashService.withSwatchService(...) is required so feature-flag waits can use management /info");
    }

    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofMillis(100), Duration.ofSeconds(20))
            .withService(swatchService)
            .timeoutMessage(
                "Feature flag '%s' did not reach expected state enabled=%s variant=%s payload=%s",
                flag, expectedEnabled, expectedVariantName, expectedPayload);

    AwaitilityUtils.untilAsserted(
        () -> assertFlagMatches(flag, expectedEnabled, expectedVariantName, expectedPayload),
        settings);
  }

  private void assertFlagMatches(
      String flag, boolean expectedEnabled, String expectedVariantName, String expectedPayload) {
    InfoFeatureFlags featureFlags =
        swatchService
            .getFeatureFlags()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Management /info must expose a feature-flags section for Unleash waits"));
    assertNotNull(featureFlags.getFlags(), "feature-flags list should be present in /info");

    InfoFeatureFlag actual =
        featureFlags.getFlags().stream()
            .filter(entry -> flag.equals(entry.getName()))
            .findFirst()
            .orElse(null);
    assertNotNull(actual, "Feature flag '" + flag + "' should be present");
    assertEquals(expectedEnabled, actual.getEnabled(), "Feature flag enabled state mismatch");

    if (expectedVariantName != null) {
      assertNotNull(actual.getVariant(), "Feature flag variant should be present");
      assertEquals(
          expectedVariantName, actual.getVariant().getName(), "Feature flag variant name mismatch");
      if (expectedPayload != null) {
        assertNotNull(actual.getVariant().getPayload(), "Variant payload should be present");
        assertEquals(
            expectedPayload,
            actual.getVariant().getPayload().getValue(),
            "Variant payload value mismatch");
      }
    }
  }

  private String adminToken() {
    return this.getProperty(ADMIN_TOKEN, EMPTY);
  }
}
