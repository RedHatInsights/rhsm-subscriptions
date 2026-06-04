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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesResponse;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class OrgPreferencesComponentTest extends BaseUtilizationComponentTest {

  /** Matches ORG_PREFERENCE_DEFAULT_THRESHOLD in swatch-utilization application.properties */
  private static final Integer DEFAULT_THRESHOLD = 80;

  @TestPlanName("org-preferences-TC001")
  @Test
  void shouldRetrieveDefaultThresholdForOrganizationWithoutCustomPreferences() {
    // Given: Organization has not configured any custom preferences

    // When: Retrieving org preferences
    OrgPreferencesResponse response = whenGetOrgPreferences();

    // Then: Response contains system default threshold
    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getCustomThreshold(), "Custom threshold should not be null");
    assertEquals(
        DEFAULT_THRESHOLD,
        response.getCustomThreshold(),
        "Should return system default threshold when org has no custom preferences");
  }

  @TestPlanName("org-preferences-TC002")
  @Test
  void shouldUpdateOrganizationThresholdPreferences() {
    // Given: Organization has not configured any custom preferences
    Integer customThreshold = 15;

    // When: Updating org preferences with custom threshold
    OrgPreferencesResponse postResponse = whenUpdateOrgPreferences(customThreshold);

    // Then: Response contains the persisted custom threshold value
    assertNotNull(postResponse, "POST response should not be null");
    assertEquals(
        customThreshold,
        postResponse.getCustomThreshold(),
        "POST response should contain custom threshold");

    // Verify persistence by retrieving
    OrgPreferencesResponse getResponse = whenGetOrgPreferences();
    assertEquals(
        customThreshold, getResponse.getCustomThreshold(), "GET should return persisted value");
  }

  @TestPlanName("org-preferences-TC003")
  @Test
  void shouldRetrieveCustomThresholdAfterUpdate() {
    // Given: Organization has configured custom threshold
    Integer customThreshold = 20;
    whenUpdateOrgPreferences(customThreshold);

    // When: Retrieving org preferences
    OrgPreferencesResponse response = whenGetOrgPreferences();

    // Then: Response contains the previously configured custom threshold (not system default)
    assertNotNull(response, "Response should not be null");
    assertEquals(
        customThreshold,
        response.getCustomThreshold(),
        "Should return custom threshold, not system default");
  }

  @TestPlanName("org-preferences-TC004")
  @Test
  void shouldUpdateExistingCustomThreshold() {
    // Given: Organization has already configured custom threshold
    Integer initialThreshold = 10;
    Integer updatedThreshold = 25;
    whenUpdateOrgPreferences(initialThreshold);

    // When: Updating threshold with different value
    whenUpdateOrgPreferences(updatedThreshold);

    // Then: Response contains updated threshold (not initial value)
    OrgPreferencesResponse response = whenGetOrgPreferences();
    assertEquals(
        updatedThreshold,
        response.getCustomThreshold(),
        "Should return updated threshold, not initial threshold");
  }

  @TestPlanName("org-preferences-TC005")
  @Test
  void shouldRejectNegativeThresholdValues() {
    // Given: Organization attempts to configure negative threshold

    // When: Attempting to set threshold below minimum (< 0)
    Response response = attemptUpdateOrgPreferences(-5);

    // Then: Request is rejected with 400 Bad Request
    assertEquals(
        HttpStatus.SC_BAD_REQUEST,
        response.statusCode(),
        "Should reject negative threshold values with 400");
  }

  @TestPlanName("org-preferences-TC005")
  @Test
  void shouldRejectThresholdValuesAboveMaximum() {
    // Given: Organization attempts to configure threshold above maximum

    // When: Attempting to set threshold above 100
    Response response = attemptUpdateOrgPreferences(150);

    // Then: Request is rejected with 400 Bad Request
    assertEquals(
        HttpStatus.SC_BAD_REQUEST,
        response.statusCode(),
        "Should reject threshold values > 100 with 400");
  }

  @TestPlanName("org-preferences-TC006")
  @Test
  void shouldAcceptMinimumBoundaryValue() {
    // Given: Organization configures threshold at minimum boundary

    // When: Setting threshold to minimum boundary (0)
    OrgPreferencesResponse response = whenUpdateOrgPreferences(0);

    // Then: Request is accepted and returns boundary value
    assertEquals(0, response.getCustomThreshold(), "Should accept threshold value 0");
  }

  @TestPlanName("org-preferences-TC006")
  @Test
  void shouldAcceptMaximumBoundaryValue() {
    // Given: Organization configures threshold at maximum boundary

    // When: Setting threshold to maximum boundary (100)
    OrgPreferencesResponse response = whenUpdateOrgPreferences(100);

    // Then: Request is accepted and returns boundary value
    assertEquals(100, response.getCustomThreshold(), "Should accept threshold value 100");
  }

  private OrgPreferencesResponse whenGetOrgPreferences() {
    return service.getOrgPreferencesExpectSuccess(orgId);
  }

  private OrgPreferencesResponse whenUpdateOrgPreferences(Integer customThreshold) {
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(customThreshold);
    return service.updateOrgPreferencesExpectSuccess(orgId, request);
  }

  private Response attemptUpdateOrgPreferences(Integer customThreshold) {
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(customThreshold);
    return service.updateOrgPreferences(orgId, request);
  }
}
