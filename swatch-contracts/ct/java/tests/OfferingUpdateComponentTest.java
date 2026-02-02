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

import static domain.Offering.METERED_NO;
import static domain.Offering.PRODUCT_ID_OPENSHIFT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.Contract;
import com.redhat.swatch.contract.test.model.OfferingProductTags;
import domain.BillingProvider;
import domain.Offering;
import domain.Product;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class OfferingUpdateComponentTest extends BaseContractComponentTest {

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @TestPlanName("offering-update-TC001")
  @Test
  void shouldProcessProductUpdateEvent() {
    // Given: An existing RHACM offering with a contract
    String sku = RandomUtils.generateRandom();
    Offering rhacmOffering = Offering.buildRhacmOffering(sku);
    var contract =
        domain.Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    givenContractIsCreated(contract);

    // When: A UMB product update event is sent changing offering from RHACM to OpenShift
    Offering openshiftOffering =
        rhacmOffering.toBuilder()
            .description("Updated to OpenShift Container Platform offering")
            .level1("OpenShift")
            .level2("OCP - OpenShift Container Platform")
            .metered(METERED_NO)
            .engProducts(List.of(PRODUCT_ID_OPENSHIFT)) // OpenShift product ID
            .build();
    wiremock.forProductAPI().stubOfferingData(openshiftOffering);
    artemis.forOfferings().send(openshiftOffering);

    // Then: API returns HTTP 200 response with updated product tag reflecting the change
    AwaitilityUtils.untilAsserted(
        () -> {
          OfferingProductTags updatedTags = whenGetSkuProductTags(openshiftOffering);
          thenProductTagsShouldContain(
              updatedTags,
              Product.OPENSHIFT.getName(),
              "Product tag should be 'OpenShift Container Platform' after update. Actual was: "
                  + updatedTags.getData());
        });

    // And: The contract still exists and was not lost during the update
    List<Contract> contractsAfterUpdate = service.getContractsByOrgId(orgId);
    assertEquals(1, contractsAfterUpdate.size());
    assertEquals(sku, contractsAfterUpdate.get(0).getSku());
  }

  @TestPlanName("offering-update-TC002")
  @Test
  void shouldHandleMalformedEvent() {
    // Given: a valid existing offering
    Offering validOffering = Offering.buildRosaOffering(RandomUtils.generateRandom());
    givenOfferingExists(validOffering);

    // When: send one malformed UMB message
    artemis.forOfferings().sendMalformed("{\"invalid\": \"json\" missing bracket");

    // Wait for error logs to appear
    AwaitilityUtils.untilAsserted(
        () -> service.logs().assertContains("Unable to read UMB product message for JSON"));

    // Then: System remains operational
    assertTrue(
        service.isRunning(),
        "System should remain operational after malformed events via Health Check API");

    // And: Valid offerings remain unaffected by malformed UMB events
    OfferingProductTags validTagsAfter = whenGetSkuProductTags(validOffering);
    assertNotNull(validTagsAfter, "Valid offering should still be accessible");
  }

  private void givenOfferingExists(Offering offering) {
    wiremock.forProductAPI().stubOfferingData(offering);
    Response syncResponse = service.syncOffering(offering.getSku());
    assertEquals(HttpStatus.SC_OK, syncResponse.statusCode(), "Offering sync should succeed");
  }

  private OfferingProductTags whenGetSkuProductTags(Offering offering) {
    return service
        .getSkuProductTags(offering.getSku())
        .then()
        .statusCode(HttpStatus.SC_OK)
        .extract()
        .as(OfferingProductTags.class);
  }

  private void thenProductTagsShouldContain(
      OfferingProductTags productTags, String expectedTag, String message) {
    assertNotNull(productTags, "Product tags should not be null");
    assertNotNull(productTags.getData(), "Product tags data should not be null");
    assertTrue(
        productTags.getData().stream().anyMatch(expectedTag::equals),
        message + " (expected: " + expectedTag + ")");
  }
}
