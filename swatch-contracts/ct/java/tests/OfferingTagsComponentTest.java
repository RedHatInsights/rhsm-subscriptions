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

import static com.redhat.swatch.component.tests.utils.RandomUtils.generateRandom;
import static domain.Offering.buildOpenShiftOffering;
import static domain.Offering.buildRhelOffering;
import static domain.Offering.buildRosaOffering;
import static domain.Product.OPENSHIFT;
import static domain.Product.RHEL;
import static domain.Product.ROSA;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.contract.test.model.OfferingProductTags;
import domain.Offering;
import domain.Product;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class OfferingTagsComponentTest extends BaseContractComponentTest {

  @TestPlanName("offering-tags-TC001")
  @Test
  void shouldRetrieveProductTagsForSynchronizedOffering() {
    // Given: A synchronized ROSA offering
    Offering rosaOffering = givenRosaOfferingExists();

    // When: Retrieving product tags for the synchronized SKU
    OfferingProductTags productTags = whenGetSkuProductTags(rosaOffering);

    // Then: Product tags are returned with correct mapping based on level_1 and level_2
    assertNotNull(productTags, "Product tags should not be null");
    assertNotNull(productTags.getData(), "Product tags data should not be null");
    assertFalse(productTags.getData().isEmpty(), "Product tags should contain at least one tag");

    // Verify the expected product tag for ROSA
    assertThat(
        "Should contain the rosa product tag",
        productTags.getData(),
        containsInAnyOrder(ROSA.getName()));

    // Verify subsequent API calls return consistent tag data
    OfferingProductTags secondCallTags = whenGetSkuProductTags(rosaOffering);
    assertEquals(
        productTags.getData(),
        secondCallTags.getData(),
        "Subsequent API calls should return consistent tag data");
  }

  @TestPlanName("offering-tags-TC002")
  @Test
  void shouldVerifyProductTagMappingForDifferentProductTypes() {
    // Given: Multiple offerings with different level_1/level_2 combinations
    var rosaSku = givenRosaOfferingExists();
    var rhelSku = givenRhelOfferingExists();
    var openshiftSku = givenOpenShiftOfferingExists();

    // When: Retrieving product tags for each product type
    OfferingProductTags rosaTags = whenGetSkuProductTags(rosaSku);
    OfferingProductTags rhelTags = whenGetSkuProductTags(rhelSku);
    OfferingProductTags openshiftTags = whenGetSkuProductTags(openshiftSku);

    // Then: Each product returns appropriate product tags
    thenProductTagsShouldMatch(rosaTags, ROSA);
    thenProductTagsShouldMatch(rhelTags, RHEL);
    thenProductTagsShouldMatch(openshiftTags, OPENSHIFT);

    // Verify different level combinations produce distinct product tags
    thenProductTagsShouldBeDifferent(rosaTags, rhelTags, ROSA, RHEL);
    thenProductTagsShouldBeDifferent(rosaTags, openshiftTags, ROSA, OPENSHIFT);
    thenProductTagsShouldBeDifferent(rhelTags, openshiftTags, RHEL, OPENSHIFT);
  }

  @TestPlanName("offering-tags-TC003")
  @Test
  void shouldHandleProductTagRetrievalForNonExistentOffering() {
    // Given: A SKU that does not exist in the system
    String nonExistentSku = "NONEXISTENT_SKU";

    // When: Retrieving product tags for the non-existent SKU
    Response response = service.getSkuProductTags(nonExistentSku);

    // Then: It returns not found
    assertEquals(
        HttpStatus.SC_NOT_FOUND,
        response.statusCode(),
        "Response should return 404 Not Found for non-existent SKU");
  }

  private Offering givenRosaOfferingExists() {
    return givenOfferingExists(buildRosaOffering(generateRandom()));
  }

  private Offering givenRhelOfferingExists() {
    return givenOfferingExists(buildRhelOffering(generateRandom(), 4.0, 2.0));
  }

  private Offering givenOpenShiftOfferingExists() {
    return givenOfferingExists(buildOpenShiftOffering(generateRandom(), 16.0, 4.0));
  }

  private Offering givenOfferingExists(Offering offering) {
    wiremock.forProductAPI().stubOfferingData(offering);
    Response syncResponse = service.syncOffering(offering.getSku());
    assertEquals(
        HttpStatus.SC_OK,
        syncResponse.statusCode(),
        "Offering sync should succeed for SKU " + offering.getSku());
    return offering;
  }

  private OfferingProductTags whenGetSkuProductTags(Offering offering) {
    return service
        .getSkuProductTags(offering.getSku())
        .then()
        .statusCode(SC_OK)
        .extract()
        .as(OfferingProductTags.class);
  }

  private void thenProductTagsShouldMatch(OfferingProductTags tags, Product productName) {
    assertNotNull(tags, productName + " tags should not be null");
    assertNotNull(tags.getData(), productName + " tags data should not be null");
    assertThat(
        productName + " should have correct product tag",
        tags.getData(),
        containsInAnyOrder(productName.getName()));
  }

  private void thenProductTagsShouldBeDifferent(
      OfferingProductTags tags1, OfferingProductTags tags2, Product name1, Product name2) {
    assertNotEquals(
        tags1.getData(),
        tags2.getData(),
        name1 + " and " + name2 + " should have different product tags");
  }
}
