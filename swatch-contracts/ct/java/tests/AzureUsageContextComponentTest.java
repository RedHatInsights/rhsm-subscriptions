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

import static domain.ErrorCodes.SUBSCRIPTION_RECENTLY_TERMINATED_CODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.AzureUsageContext;
import com.redhat.swatch.contract.test.model.ServiceLevelType;
import com.redhat.swatch.contract.test.model.UsageType;
import domain.BillingProvider;
import domain.Contract;
import domain.Product;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class AzureUsageContextComponentTest extends BaseContractComponentTest {

  @TestPlanName("azure-usage-context-TC001")
  @Test
  void shouldReturnAzureUsageContextForExistingContract() {
    Contract contract = givenExistingRosaContract();

    var usageContext =
        whenGetAzureMarketplaceContext(contract.getBillingAccountId())
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(AzureUsageContext.class);

    assertEquals(
        contract.getResourceId(),
        usageContext.getAzureResourceId(),
        "azureResourceId should match billing_provider_id");
    assertEquals(
        contract.getPlanId(), usageContext.getPlanId(), "planId should match billing_provider_id");
    assertEquals(
        contract.getProductCode(),
        usageContext.getOfferId(),
        "offerId should match billing_provider_id");
  }

  @TestPlanName("azure-usage-context-TC002")
  @Test
  void shouldReturn404WhenAzureUsageContextNotFound() {
    Response response = whenGetAzureMarketplaceContext("nonexistent-billing-account");

    assertEquals(
        HttpStatus.SC_NOT_FOUND,
        response.statusCode(),
        "Azure usage context lookup should return 404 when no subscription matches");
    assertTrue(
        response.body().asString().isBlank(),
        "Missing subscription should return 404 without an error body");
  }

  @TestPlanName("azure-usage-context-TC003")
  @Test
  void shouldReturn404WhenAzureUsageContextHasInactiveSubs() {
    Contract contract = givenExistingRosaContractTerminated();

    Response response = whenGetAzureMarketplaceContext(contract.getBillingAccountId());

    assertEquals(
        HttpStatus.SC_NOT_FOUND,
        response.statusCode(),
        "Azure usage context lookup should return 404 when no active subscription matches");
    assertEquals(
        SUBSCRIPTION_RECENTLY_TERMINATED_CODE,
        response.jsonPath().getString("code"),
        "Inactive subscription should return " + SUBSCRIPTION_RECENTLY_TERMINATED_CODE);
  }

  private Contract givenExistingRosaContract() {
    String sku = RandomUtils.generateRandom();
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AZURE, Map.of(CORES, 10.0), sku);
    givenContractIsCreated(contract);
    return contract;
  }

  private Contract givenExistingRosaContractTerminated() {
    Contract contract = givenExistingRosaContract();
    assertEquals(
        HttpStatus.SC_OK,
        service.terminateSubscription(contract, OffsetDateTime.now().minusMinutes(30)).statusCode(),
        "Contract termination should succeed");
    return contract;
  }

  private Response whenGetAzureMarketplaceContext(String azureAccountId) {
    return service.getAzureMarketplaceContext(
        orgId,
        Product.ROSA,
        OffsetDateTime.now(),
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        azureAccountId);
  }
}
