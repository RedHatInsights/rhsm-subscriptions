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

import static com.redhat.swatch.component.tests.utils.DateUtils.assertDatesAreEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
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

    // Then: Response contains system default threshold and no last_modified
    assertNotNull(response, "Response should not be null");
    assertNotNull(response.getCustomThreshold(), "Custom threshold should not be null");
    assertEquals(
        DEFAULT_THRESHOLD,
        response.getCustomThreshold(),
        "Should return system default threshold when org has no custom preferences");
    assertNull(
        response.getLastModified(), "last_modified should be absent when using default threshold");
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
    assertNotNull(postResponse.getLastModified(), "POST response should contain last_modified");

    // Verify persistence by retrieving
    OrgPreferencesResponse getResponse = whenGetOrgPreferences();
    assertEquals(
        customThreshold, getResponse.getCustomThreshold(), "GET should return persisted value");
    assertDatesAreEqual(postResponse.getLastModified(), getResponse.getLastModified());
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
    assertNotNull(response.getLastModified(), "Should return last_modified for configured org");
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
    assertNotNull(response.getLastModified(), "Should return last_modified after update");
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
    assertNotNull(response.getLastModified(), "POST response should contain last_modified");
  }

  @TestPlanName("org-preferences-TC006")
  @Test
  void shouldAcceptMaximumBoundaryValue() {
    // Given: Organization configures threshold at maximum boundary

    // When: Setting threshold to maximum boundary (100)
    OrgPreferencesResponse response = whenUpdateOrgPreferences(100);

    // Then: Request is accepted and returns boundary value
    assertEquals(100, response.getCustomThreshold(), "Should accept threshold value 100");
    assertNotNull(response.getLastModified(), "POST response should contain last_modified");
  }

  @TestPlanName("org-preferences-TC007")
  @Test
  void shouldAllowOrgAdminToUpdatePreferences() {
    // Given: An org admin user identity with a custom threshold
    Integer customThreshold = 42;
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(customThreshold);

    // When: Org admin updates the threshold
    OrgPreferencesResponse body =
        service.updateOrgPreferencesAsUserExpectSuccess(orgId, true, request);

    // Then: Request succeeds
    assertEquals(
        customThreshold, body.getCustomThreshold(), "Response should contain updated threshold");
  }

  @TestPlanName("org-preferences-TC008")
  @Test
  void shouldRejectNonAdminUserFromUpdatingPreferences() {
    // Given: A non-admin user identity
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(42);

    // When: Non-admin user attempts to update the threshold
    Response response = service.updateOrgPreferencesAsUser(orgId, false, request);

    // Then: Request is rejected with 403 Forbidden
    assertEquals(
        HttpStatus.SC_FORBIDDEN,
        response.statusCode(),
        "Non-admin user should be rejected with 403");
  }

  @TestPlanName("org-preferences-TC009")
  @Test
  void shouldNotAffectAnotherOrgPreferencesWhenAdminUsesOwnIdentity() {
    // Given: Org B has a custom threshold configured
    String orgBId = RandomUtils.generateRandom();
    Integer orgBThreshold = 30;
    OrgPreferencesRequest orgBRequest = new OrgPreferencesRequest();
    orgBRequest.setCustomThreshold(orgBThreshold);
    service.updateOrgPreferencesAsUserExpectSuccess(orgBId, true, orgBRequest);

    // When: Admin from org A (different org) updates their own threshold
    Integer orgAThreshold = 55;
    OrgPreferencesRequest orgARequest = new OrgPreferencesRequest();
    orgARequest.setCustomThreshold(orgAThreshold);
    service.updateOrgPreferencesAsUserExpectSuccess(orgId, true, orgARequest);

    // Then: Org B's threshold is unchanged; org A's threshold is updated
    OrgPreferencesResponse orgBPreferences = service.getOrgPreferencesExpectSuccess(orgBId);
    assertEquals(
        orgBThreshold, orgBPreferences.getCustomThreshold(), "Org B threshold should be unchanged");

    OrgPreferencesResponse orgAPreferences = service.getOrgPreferencesExpectSuccess(orgId);
    assertEquals(
        orgAThreshold, orgAPreferences.getCustomThreshold(), "Org A threshold should be updated");
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
