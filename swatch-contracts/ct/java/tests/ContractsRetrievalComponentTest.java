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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ContractsRetrievalComponentTest extends BaseContractComponentTest {

  @TestPlanName("contracts-retrieval-TC001")
  @Test
  void shouldGetContractByOrgId() {
    final String firstOrgId = orgId + "a";
    final String secondOrgId = orgId + "b";
    // Create multiple contracts for the first organization
    givenExistingContractForOrgId(firstOrgId);
    givenExistingContractForOrgId(firstOrgId);
    // Create multiple contracts for the second organization
    givenExistingContractForOrgId(secondOrgId);
    givenExistingContractForOrgId(secondOrgId);

    // When get the contracts for the first organization
    var contracts = service.getContractsByOrgId(firstOrgId);

    // We only get the contracts for this organization, not the second organization
    assertEquals(2, contracts.size());
    contracts.forEach(c -> assertEquals(firstOrgId, c.getOrgId()));
  }

  @TestPlanName("contracts-retrieval-TC002")
  @Test
  void shouldGetContractsOnlyActiveAtASpecificTime() {
    OffsetDateTime now = OffsetDateTime.now();
    // given inactive contract
    givenExistingContractAtTimestamp(now.minusDays(3), now.minusDays(2));
    // given active contract
    var activeContract = givenExistingContractAtTimestamp(now.minusDays(1), now.plusDays(2));
    // given active contract at a future date
    var futureContract = givenExistingContractAtTimestamp(now.plusDays(5), now.plusDays(10));

    // Verify that only the active contract is returned
    var contracts = service.getContractsByOrgIdAndTimestamp(orgId, now);
    assertEquals(1, contracts.size());
    assertEquals(activeContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());

    // Verify that only the future contract is returned
    contracts = service.getContractsByOrgIdAndTimestamp(orgId, now.plusDays(8));
    assertEquals(1, contracts.size());
    assertEquals(futureContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());
  }

  @TestPlanName("contracts-retrieval-TC003")
  @Test
  void shouldGetContractsByBillingProvider() {
    var awsContract = givenExistingContractForBillingProvider(BillingProvider.AWS);
    var azureContract = givenExistingContractForBillingProvider(BillingProvider.AZURE);

    // Verify that we only return the aws contract
    var contracts = service.getContractsByOrgIdAndBillingProvider(orgId, BillingProvider.AWS);
    assertEquals(1, contracts.size());
    assertEquals(awsContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());

    // Verify that we only return the azure contract
    contracts = service.getContractsByOrgIdAndBillingProvider(orgId, BillingProvider.AZURE);
    assertEquals(1, contracts.size());
    assertEquals(azureContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());
  }

  @TestPlanName("contracts-retrieval-TC004")
  @Test
  void shouldGetContractsByTimestampAndOrgIdAndBillingProvider() {
    OffsetDateTime now = OffsetDateTime.now();
    var awsExpired = givenExistingContractAtTimestamp(now.minusDays(10), now.minusDays(2));
    var awsContract = givenExistingContractForBillingProvider(BillingProvider.AWS);
    var azureContract = givenExistingContractForBillingProvider(BillingProvider.AZURE);

    // Verify that we only return the aws contract
    var contracts =
        service.getContractsByOrgIdAndBillingProviderAndTimestamp(orgId, BillingProvider.AWS, now);
    assertEquals(1, contracts.size());
    assertEquals(awsContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());

    // Verify that we only return the azure contract
    contracts =
        service.getContractsByOrgIdAndBillingProviderAndTimestamp(
            orgId, BillingProvider.AZURE, now);
    assertEquals(1, contracts.size());
    assertEquals(azureContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());

    // Verify that we only return the aws expired contract
    contracts =
        service.getContractsByOrgIdAndBillingProviderAndTimestamp(
            orgId, BillingProvider.AWS, now.minusDays(5));
    assertEquals(1, contracts.size());
    assertEquals(awsExpired.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());
  }

  @TestPlanName("contracts-retrieval-TC005")
  @Test
  void shouldGetEmptyContractsWhenFilteringOutByTimestamp() {
    OffsetDateTime now = OffsetDateTime.now();
    // given active contract
    givenExistingContractAtTimestamp(now.minusDays(1), now.plusDays(2));

    // Verify that we return empty contracts using a different timestamp
    var contracts = service.getContractsByOrgIdAndTimestamp(orgId, now.plusDays(10));
    assertTrue(contracts.isEmpty());
  }

  @TestPlanName("contracts-retrieval-TC006")
  @Test
  void shouldGetEmptyContractsWhenThereAreNoContractsForOrgId() {
    var contracts = service.getContractsByOrgId(orgId);
    assertTrue(contracts.isEmpty());
  }

  private Contract givenExistingContractForBillingProvider(BillingProvider billingProvider) {
    Contract contract = (Contract) contractBuilder().billingProvider(billingProvider).build();
    givenContractIsCreated(contract);
    return contract;
  }

  private Contract givenExistingContractAtTimestamp(
      OffsetDateTime startDate, OffsetDateTime endDate) {
    Contract contract = (Contract) contractBuilder().startDate(startDate).endDate(endDate).build();
    givenContractIsCreated(contract);
    return contract;
  }

  private void givenExistingContractForOrgId(String orgId) {
    givenContractIsCreated((Contract) contractBuilder().orgId(orgId).build());
  }

  private Contract.ContractBuilder contractBuilder() {
    Product product = Product.ROSA;
    String seed = RandomUtils.generateRandom();
    return Contract.builder()
        .customerId("customer" + seed)
        .sellerAccountId("seller" + seed)
        .productCode("product" + seed)
        .planId("plan" + seed)
        .clientId("clientId" + seed)
        .resourceId("resourceId" + seed)
        .subscriptionMeasurements(Map.of(CORES, 10.0))
        .billingProvider(BillingProvider.AWS)
        .billingAccountId("billing" + seed)
        .orgId(orgId)
        .product(product)
        .offering(Offering.buildRosaOffering(seed))
        .subscriptionId(seed)
        .subscriptionNumber(seed)
        .startDate(OffsetDateTime.now().minusDays(1))
        .endDate(OffsetDateTime.now().plusDays(1));
  }
}
