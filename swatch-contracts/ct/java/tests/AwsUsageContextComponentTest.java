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
import com.redhat.swatch.contract.test.model.AwsUsageContext;
import com.redhat.swatch.contract.test.model.ServiceLevelType;
import com.redhat.swatch.contract.test.model.UsageType;
import domain.Contract;
import domain.Product;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class AwsUsageContextComponentTest extends BaseContractComponentTest {

  @TestPlanName("aws-usage-context-TC001")
  @Test
  void shouldReturnAwsUsageContextForExistingContract() {
    Contract contract = givenRosaContractIsCreated(10.0);

    AwsUsageContext usageContext =
        whenGetAwsMarketplaceContext(contract.getBillingAccountId())
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .as(AwsUsageContext.class);

    assertEquals(
        contract.getProductCode(),
        usageContext.getProductCode(),
        "productCode should match billing_provider_id");
    assertEquals(
        contract.getCustomerId(),
        usageContext.getCustomerId(),
        "customerId should match billing_provider_id");
    assertEquals(
        contract.getSellerAccountId(),
        usageContext.getAwsSellerAccountId(),
        "awsSellerAccountId should match billing_provider_id");
  }

  @TestPlanName("aws-usage-context-TC002")
  @Test
  void shouldReturn404WhenAwsUsageContextNotFound() {
    Response response = whenGetAwsMarketplaceContext("nonexistent-billing-account");

    assertEquals(
        HttpStatus.SC_NOT_FOUND,
        response.statusCode(),
        "AWS usage context lookup should return 404 when no subscription matches");
    assertTrue(
        response.body().asString().isBlank(),
        "Missing subscription should return 404 without an error body");
  }

  @TestPlanName("aws-usage-context-TC003")
  @Test
  void shouldReturn404WhenAwsUsageContextHasInactiveSubs() {
    Contract contract = givenRosaContractIsTerminated();

    Response response = whenGetAwsMarketplaceContext(contract.getBillingAccountId());

    assertEquals(
        HttpStatus.SC_NOT_FOUND,
        response.statusCode(),
        "AWS usage context lookup should return 404 when no active subscription matches");
    assertEquals(
        SUBSCRIPTION_RECENTLY_TERMINATED_CODE,
        response.jsonPath().getString("code"),
        "Inactive subscription should return " + SUBSCRIPTION_RECENTLY_TERMINATED_CODE);
  }

  private Contract givenRosaContractIsTerminated() {
    Contract contract = givenRosaContractIsCreated(10.0);
    OffsetDateTime terminationDate = OffsetDateTime.now().minusMinutes(30);
    assertEquals(
        HttpStatus.SC_OK,
        service.terminateSubscription(contract, terminationDate).statusCode(),
        "Contract termination should succeed");
    return contract;
  }

  private Response whenGetAwsMarketplaceContext(String billingAccountId) {
    return service.getAwsUsageContext(
        orgId,
        Product.ROSA,
        OffsetDateTime.now(),
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        billingAccountId);
  }
}
