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

import static domain.AwsLicenseArns.awsLicenseArn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ContractsFromSubscriptionsComponentTest extends BaseContractComponentTest {

  private static final double CORES_CAPACITY = 20.0;
  private static final double SOCKETS_CAPACITY = 2.0;

  @AfterEach
  void resetUseSubscriptionsFlag() {
    unleash.disableUseSubscriptions();
  }

  @TestPlanName("contracts-from-subscriptions-TC001")
  @Test
  void shouldReturnEquivalentContractFromBothDataSources() {
    Contract contract = givenExistingRosaContractWithLicense();

    var fromContractsTable = service.getContractsByOrgId(orgId);
    whenUseSubscriptionsIsEnabled();
    var fromSubscriptionsTable = service.getContractsByOrgId(orgId);

    assertEquals(1, fromContractsTable.size());
    assertEquals(1, fromSubscriptionsTable.size());
    // uuid is the only field that cannot be served from the subscriptions tables, so the
    // contracts-table response is the one checked against the base-class expectations
    verifyCommonContractFields(contract, fromContractsTable.get(0));
    thenContractsAreEquivalent(fromContractsTable.get(0), fromSubscriptionsTable.get(0));
    verifyAwsBillingProviderId(contract, fromSubscriptionsTable.get(0));
    verifyMetric(
        fromSubscriptionsTable.get(0), contract.getProduct().getMetric(CORES), CORES_CAPACITY);
  }

  @TestPlanName("contracts-from-subscriptions-TC002")
  @Test
  void shouldReturnNullUuidWhenServedFromSubscriptions() {
    givenExistingRosaContractWithLicense();

    assertNotNull(service.getContractsByOrgId(orgId).get(0).getUuid());

    whenUseSubscriptionsIsEnabled();

    assertNull(
        service.getContractsByOrgId(orgId).get(0).getUuid(),
        "Subscriptions have no contract UUID to expose");
  }

  @TestPlanName("contracts-from-subscriptions-TC003")
  @Test
  void shouldReturnSubscriptionWithoutContractOnlyWhenFlagEnabled() {
    Contract subscriptionOnly =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, CORES_CAPACITY));
    givenSubscriptionIsCreated(subscriptionOnly);

    assertTrue(
        service.getContractsByOrgId(orgId).isEmpty(),
        "The contracts tables hold no record for a subscription created on its own");

    whenUseSubscriptionsIsEnabled();

    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(1, contracts.size());
    assertEquals(
        subscriptionOnly.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());
  }

  @TestPlanName("contracts-from-subscriptions-TC004")
  @Test
  void shouldApplyEveryFilterWhenServedFromSubscriptions() {
    Contract contract = givenExistingRosaContractWithLicense();
    Contract other =
        Contract.buildRosaContract(orgId, BillingProvider.AZURE, Map.of(CORES, CORES_CAPACITY));
    givenContractIsCreated(other);
    whenUseSubscriptionsIsEnabled();

    assertEquals(1, service.getContracts(contract).size());
    assertEquals(2, service.getContractsByOrgIdAndProductTag(orgId, Product.ROSA.getName()).size());
    assertEquals(
        1, service.getContractsByOrgIdAndBillingProvider(orgId, BillingProvider.AWS).size());
    assertEquals(
        1,
        service
            .getContractsByOrgIdAndBillingAccountId(orgId, contract.getBillingAccountId())
            .size());
    assertEquals(
        1,
        service.getContractsByOrgIdAndVendorProductCode(orgId, contract.getProductCode()).size());
    assertEquals(2, service.getContractsByOrgIdAndTimestamp(orgId, OffsetDateTime.now()).size());
  }

  @TestPlanName("contracts-from-subscriptions-TC005")
  @Test
  void shouldFilterOutSubscriptionsOutsideTheTimestamp() {
    givenExistingRosaContractWithLicense();
    whenUseSubscriptionsIsEnabled();

    assertTrue(
        service
            .getContractsByOrgIdAndTimestamp(orgId, OffsetDateTime.now().plusDays(10))
            .isEmpty());
  }

  @TestPlanName("contracts-from-subscriptions-TC006")
  @Test
  void shouldReturnEmptyWhenOrgHasNoSubscriptions() {
    whenUseSubscriptionsIsEnabled();

    assertTrue(service.getContractsByOrgId(orgId).isEmpty());
  }

  @TestPlanName("contracts-from-subscriptions-TC007")
  @Test
  void shouldExcludeMarketplaceSubscriptionForNonContractEnabledProduct() {
    Contract rosaContract = givenExistingRosaContractWithLicense();
    givenMarketplaceRhelSubscriptionIsCreated();

    whenUseSubscriptionsIsEnabled();

    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(
        1,
        contracts.size(),
        "A marketplace subscription for a product that is not contract-enabled is not a contract");
    assertEquals(rosaContract.getSubscriptionNumber(), contracts.get(0).getSubscriptionNumber());
  }

  private Contract givenExistingRosaContractWithLicense() {
    String subscriptionNumber = RandomUtils.generateRandom();
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, CORES_CAPACITY))
            .toBuilder()
            .subscriptionNumber(subscriptionNumber)
            .licenseId(awsLicenseArn(subscriptionNumber))
            .build();
    givenContractIsCreated(contract);
    return contract;
  }

  private Contract givenMarketplaceRhelSubscriptionIsCreated() {
    Contract marketplaceRhel =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(SOCKETS, SOCKETS_CAPACITY))
            .toBuilder()
            .product(Product.RHEL)
            .offering(
                Offering.buildRhelOffering(RandomUtils.generateRandom(), null, SOCKETS_CAPACITY))
            .build();
    givenSubscriptionIsCreated(marketplaceRhel);
    return marketplaceRhel;
  }

  private void whenUseSubscriptionsIsEnabled() {
    unleash.enableUseSubscriptions();
  }

  private void thenContractsAreEquivalent(
      com.redhat.swatch.contract.test.model.Contract fromContracts,
      com.redhat.swatch.contract.test.model.Contract fromSubscriptions) {
    assertEquals(fromContracts.getSubscriptionNumber(), fromSubscriptions.getSubscriptionNumber());
    assertEquals(fromContracts.getSku(), fromSubscriptions.getSku());
    assertEquals(fromContracts.getOrgId(), fromSubscriptions.getOrgId());
    assertEquals(fromContracts.getStartDate(), fromSubscriptions.getStartDate());
    assertEquals(fromContracts.getEndDate(), fromSubscriptions.getEndDate());
    assertEquals(fromContracts.getBillingProvider(), fromSubscriptions.getBillingProvider());
    assertEquals(fromContracts.getBillingProviderId(), fromSubscriptions.getBillingProviderId());
    assertEquals(fromContracts.getBillingAccountId(), fromSubscriptions.getBillingAccountId());
    assertEquals(fromContracts.getProductTags(), fromSubscriptions.getProductTags());
    assertEquals(fromContracts.getVendorProductCode(), fromSubscriptions.getVendorProductCode());
    assertEquals(fromContracts.getLicenseId(), fromSubscriptions.getLicenseId());
    assertEquals(sortedMetrics(fromContracts), sortedMetrics(fromSubscriptions));
  }

  private List<String> sortedMetrics(com.redhat.swatch.contract.test.model.Contract contract) {
    return contract.getMetrics().stream()
        .map(metric -> metric.getMetricId() + "=" + metric.getValue())
        .sorted()
        .toList();
  }
}
