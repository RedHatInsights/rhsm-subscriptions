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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OfferingProductTags;
import domain.Offering;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class OfferingSyncComponentTest extends BaseContractComponentTest {

  @TestPlanName("offering-sync-TC001")
  @Test
  void shouldSynchronizeOfferingFromExternalProductData() {
    // Given: An offering with level_1 and level_2 attributes
    Offering offering = Offering.buildRhacmOffering(RandomUtils.generateRandom());
    givenOfferingDataIsStubbed(offering);

    // When: Offering is synchronized
    Response syncResponse = whenOfferingIsSynced(offering.getSku());

    // Then: Sync should succeed with HTTP 200
    assertThat("Offering sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));

    // And: Product tag is correctly mapped based on level_1/level_2 attributes
    OfferingProductTags productTags =
        service
            .getSkuProductTags(offering.getSku())
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(OfferingProductTags.class);
    thenProductTagsShouldBePresent(productTags);
    thenProductTagsShouldContain(
        productTags,
        "rhacm",
        "Product tag should be derived from level_1='OpenShift' and level_2='ACM - Advanced Cluster Management'");
  }

  @TestPlanName("offering-sync-TC002")
  @Test
  void shouldHandleSynchronizationOfNonExistentOffering() {
    // Given: A non-existent SKU that is not stubbed in wiremock
    String invalidSku = "INVALID_SKU_" + RandomUtils.generateRandom();

    // When: Attempting to synchronize a non-existent offering
    Response syncResponse = whenOfferingIsSynced(invalidSku);

    // Then: System should handle non-existent SKU gracefully with HTTP 404
    assertThat(
        "System should handle non-existent SKU gracefully",
        syncResponse.statusCode(),
        is(HttpStatus.SC_NOT_FOUND));

    // And: Verify no offering record was created in the database
    Response tagsResponse = service.getSkuProductTags(invalidSku);
    assertThat(
        "Product tags should not exist for non-existent SKU",
        tagsResponse.statusCode(),
        is(HttpStatus.SC_NOT_FOUND));
  }

  @TestPlanName("offering-sync-TC003")
  @Test
  void shouldSynchronizeMeteredOffering() {
    // Given: A metered offering (metered='Y') with level_1/level_2 attributes
    String meteredSku = RandomUtils.generateRandom();
    Offering meteredOffering = Offering.buildRosaOffering(meteredSku);
    givenOfferingDataIsStubbed(meteredOffering);

    // When: Metered offering is synchronized
    Response syncResponse = whenOfferingIsSynced(meteredSku);

    // Then: Sync should succeed with HTTP 200
    assertThat(
        "Metered offering sync should succeed", syncResponse.statusCode(), is(HttpStatus.SC_OK));

    // And: Product tags should be successfully synchronized with expected value
    OfferingProductTags productTags =
        service
            .getSkuProductTags(meteredSku)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(OfferingProductTags.class);
    thenProductTagsShouldBePresent(productTags);
    thenProductTagsShouldContain(
        productTags, "rosa", "Product tag returned matches expected value for metered offering");
  }

  @TestPlanName("offering-sync-TC004")
  @Test
  void shouldSynchronizeUnlimitedCapacityOffering() {
    // Given: An unlimited capacity offering (has_unlimited_usage=True)
    String sku = RandomUtils.generateRandom();
    Offering unlimitedOffering = Offering.buildRhelUnlimitedOffering(sku);
    givenOfferingDataIsStubbed(unlimitedOffering);

    // When: Unlimited capacity offering is synchronized
    Response syncResponse = whenOfferingIsSynced(unlimitedOffering.getSku());

    // Then: Sync should succeed with HTTP 200
    assertThat(
        "Sync unlimited capacity offering should succeed",
        syncResponse.statusCode(),
        is(HttpStatus.SC_OK));

    // And: Product tags should be successfully synchronized with expected value
    OfferingProductTags productTags =
        service
            .getSkuProductTags(sku)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(OfferingProductTags.class);
    thenProductTagsShouldBePresent(productTags);
    thenProductTagsShouldContain(
        productTags,
        "RHEL for x86",
        "Product tag should match expected value for unlimited RHEL offering");
  }

  /**
   * Helper method to stub offering data in the external product API.
   *
   * @param offering the offering to stub
   */
  private void givenOfferingDataIsStubbed(Offering offering) {
    wiremock.forProductAPI().stubOfferingData(offering);
  }

  /**
   * Helper method to trigger offering synchronization.
   *
   * @param sku the SKU to synchronize
   * @return the sync response
   */
  private Response whenOfferingIsSynced(String sku) {
    return service.syncOffering(sku);
  }

  /**
   * Helper method to verify that product tags are present and valid.
   *
   * <p>This method performs standard validation that product tags exist and contain at least one
   * entry. Use {@link #thenProductTagsShouldContain} to verify specific tag values.
   *
   * @param productTags the product tags response to verify
   */
  private void thenProductTagsShouldBePresent(OfferingProductTags productTags) {
    assertThat("Product tags should not be null", productTags, is(notNullValue()));
    assertNotNull(productTags.getData(), "Product tags data should not be null");
    assertThat(
        "Product tags should contain at least one tag",
        productTags.getData().size(),
        is(greaterThan(0)));
  }

  /**
   * Helper method to verify that product tags contain a specific expected tag value.
   *
   * <p>This method searches the product tags data for an exact match of the expected tag. The
   * search is case-sensitive.
   *
   * @param productTags the product tags response to verify
   * @param expectedTag the expected product tag to find (case-sensitive)
   * @param message the assertion message to display if the tag is not found
   */
  private void thenProductTagsShouldContain(
      OfferingProductTags productTags, String expectedTag, String message) {
    assertNotNull(productTags.getData(), "Product tags data should not be null");
    boolean containsExpectedTag = productTags.getData().stream().anyMatch(expectedTag::equals);
    assertTrue(containsExpectedTag, message + " (expected: " + expectedTag + ")");
  }
}
