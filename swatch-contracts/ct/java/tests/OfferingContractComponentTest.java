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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OfferingContractComponentTest extends BaseContractComponentTest {

  @TestPlanName("offering-contract-TC001")
  @Test
  void shouldCreateContractWithOfferingCapacity() {
    // Given: A ROSA offering and contract with capacity defined
    Contract contract = givenRosaContractWithCapacity();

    // When: Creating the contract via public API
    givenContractIsCreated(contract);

    // Then: Contract is created successfully with capacity reflecting offering definitions
    var contracts = service.getContractsByOrgId(orgId);

    thenContractShouldBeCreatedSuccessfully(contracts);
    thenContractCapacityShouldMatchOffering(contracts.get(0), contract);
  }

  @TestPlanName("offering-contract-TC002")
  @Test
  void shouldVerifyContractCapacityWithDifferentOfferingTypes() {
    // Given: Two offerings - one metered (ROSA) and one unlimited (RHEL unlimited)
    Contract meteredContract = givenRosaContractWithCapacity();
    Contract unlimitedContract = givenRhelUnlimitedContract();

    // When: Creating contracts for each offering type
    givenContractIsCreated(meteredContract);
    givenContractIsCreated(unlimitedContract);

    // Then: Metered contract has quantified capacity
    var meteredContracts =
        service.getContractsByOrgIdAndBillingProvider(orgId, BillingProvider.AWS);
    thenMeteredContractShouldHaveQuantifiedCapacity(meteredContracts, meteredContract);

    // Then: Unlimited contract shows appropriate unlimited capacity flags
    var unlimitedOrgId = unlimitedContract.getOrgId();
    var unlimitedContracts =
        service.getContractsByOrgIdAndBillingProvider(unlimitedOrgId, BillingProvider.AWS);
    thenUnlimitedContractShouldBeHandledCorrectly(unlimitedContracts, unlimitedContract);
  }

  private Contract givenRosaContractWithCapacity() {
    Map<MetricId, Double> capacity = Map.of(CORES, 16.0, INSTANCE_HOURS, 100.0);
    return Contract.buildRosaContract(orgId, BillingProvider.AWS, capacity);
  }

  private Contract givenRhelUnlimitedContract() {
    String unlimitedOrgId = givenOrgIdWithSuffix("_unlimited");
    Offering unlimitedOffering = Offering.buildRhelUnlimitedOffering(generateRandom());

    return Contract.builder()
        .customerId("customer" + generateRandom())
        .sellerAccountId("seller" + generateRandom())
        .productCode("product" + generateRandom())
        .subscriptionMeasurements(Map.of()) // Unlimited offerings have no measurements
        .billingProvider(BillingProvider.AWS)
        .billingAccountId("billing" + generateRandom())
        .orgId(unlimitedOrgId)
        .product(domain.Product.RHEL)
        .offering(unlimitedOffering)
        .subscriptionId(generateRandom())
        .subscriptionNumber(generateRandom())
        .startDate(java.time.OffsetDateTime.now().minusDays(1))
        .endDate(java.time.OffsetDateTime.now().plusDays(1))
        .build();
  }

  private void thenContractShouldBeCreatedSuccessfully(
      List<com.redhat.swatch.contract.test.model.Contract> contracts) {
    assertFalse(contracts.isEmpty(), "Should have at least one contract");
    var createdContract = contracts.get(0);
    assertNotNull(createdContract.getUuid(), "Contract UUID should not be null");
    assertNotNull(createdContract.getSku(), "Contract SKU should not be null");
  }

  private void thenContractCapacityShouldMatchOffering(
      com.redhat.swatch.contract.test.model.Contract createdContract, Contract expectedContract) {
    assertNotNull(createdContract.getSku(), "Contract should reference the offering SKU");
    assertEquals(
        expectedContract.getOffering().getSku(),
        createdContract.getSku(),
        "Contract SKU should match offering SKU");

    assertNotNull(
        createdContract.getMetrics(),
        "Contract dimensions should reference offering capacity metrics");
    assertContractMetrics(expectedContract, createdContract);
  }

  private void thenMeteredContractShouldHaveQuantifiedCapacity(
      List<com.redhat.swatch.contract.test.model.Contract> contracts, Contract expectedContract) {
    assertEquals(1, contracts.size(), "Should have exactly one metered contract");

    var meteredContract = contracts.get(0);
    assertNotNull(meteredContract.getMetrics(), "Metered contract should have metrics");
    assertFalse(
        meteredContract.getMetrics().isEmpty(), "Metered contract should have quantified capacity");
    assertContractMetrics(expectedContract, meteredContract);
  }

  private void thenUnlimitedContractShouldBeHandledCorrectly(
      List<com.redhat.swatch.contract.test.model.Contract> contracts, Contract expectedContract) {
    assertEquals(1, contracts.size(), "Should have exactly one unlimited contract");

    var unlimitedContract = contracts.get(0);
    assertNotNull(unlimitedContract.getUuid(), "Unlimited contract should be created successfully");
    assertEquals(
        expectedContract.getOffering().getSku(),
        unlimitedContract.getSku(),
        "Unlimited contract SKU should match offering SKU");

    // Verify unlimited offerings show appropriate unlimited capacity flags
    // Unlimited contracts should have empty metrics since there are no numeric capacity limits
    assertNotNull(unlimitedContract.getMetrics(), "Unlimited contract should have metrics field");
    assertEquals(
        0,
        unlimitedContract.getMetrics().size(),
        "Unlimited contract should have empty metrics (no numeric capacity limits)");
  }

  private void assertContractMetrics(
      Contract expectedContract, com.redhat.swatch.contract.test.model.Contract actualContract) {
    Map<String, Double> expectedMetrics = expectedContract.getContractMetrics();

    for (Map.Entry<String, Double> expectedEntry : expectedMetrics.entrySet()) {
      String metricId = expectedEntry.getKey();
      Double expectedValue = expectedEntry.getValue();

      var metric =
          actualContract.getMetrics().stream()
              .filter(m -> m.getMetricId().equals(metricId))
              .findFirst()
              .orElseThrow(
                  () ->
                      new AssertionError(
                          "Metric '" + metricId + "' not found in contract metrics"));

      assertEquals(
          expectedValue,
          metric.getValue().doubleValue(),
          0.01,
          "Metric '" + metricId + "' should have value " + expectedValue);
    }
  }
}
