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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.SkuCapacityReportV2;
import com.redhat.swatch.contract.test.model.SkuCapacityV2;
import domain.BillingProvider;
import domain.Contract;
import domain.Offering;
import domain.Product;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  @TestPlanName("offering-contract-TC003")
  @Test
  void shouldAggregateCapacityAcrossActiveContracts() {
    // Given: Two active ROSA contracts with the same SKU and billing account
    String sku = "rosa-" + generateRandom();
    String billingAccountId = "billing-" + generateRandom();
    Contract firstContract =
        givenActiveRosaContract(sku, billingAccountId, Map.of(CORES, 8.0, INSTANCE_HOURS, 4.0));
    Contract secondContract =
        givenActiveRosaContract(sku, billingAccountId, Map.of(CORES, 12.0, INSTANCE_HOURS, 6.0));
    givenContractIsCreated(firstContract);
    givenContractIsCreated(secondContract);

    // When: Querying the contract and capacity reports for the organization
    AwaitilityUtils.untilAsserted(
        () -> thenCapacityReportsMatchContractMetrics(sku, firstContract, secondContract));
  }

  private Contract givenRosaContractWithCapacity() {
    Map<MetricId, Double> capacity = Map.of(CORES, 16.0, INSTANCE_HOURS, 100.0);
    return Contract.buildRosaContract(orgId, BillingProvider.AWS, capacity);
  }

  private Contract givenActiveRosaContract(
      String sku, String billingAccountId, Map<MetricId, Double> capacity) {
    OffsetDateTime now = clock.now();
    return Contract.buildRosaContract(orgId, BillingProvider.AWS, capacity, sku).toBuilder()
        .billingAccountId(billingAccountId)
        .startDate(now.minusDays(1))
        .endDate(now.plusDays(1))
        .build();
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

  private void thenCapacityReportsMatchContractMetrics(String sku, Contract... expectedContracts) {
    List<com.redhat.swatch.contract.test.model.Contract> contracts =
        service.getContractsByOrgIdAndTimestamp(orgId, clock.now());
    assertEquals(
        expectedContracts.length,
        contracts.size(),
        "All expected active contracts should be returned");
    thenActiveContractsMatch(contracts, expectedContracts);

    SkuCapacityReportV2 skuReport = service.getSkuCapacityByProductIdForOrg(Product.ROSA, orgId);
    SkuCapacityV2 skuCapacity = thenSkuCapacityContainsContracts(skuReport, sku, contracts);

    for (MetricId metricId : List.of(CORES, INSTANCE_HOURS)) {
      thenMetricMatchesCapacityReports(
          metricId, contracts, expectedContracts, skuReport, skuCapacity);
    }
  }

  private void thenActiveContractsMatch(
      List<com.redhat.swatch.contract.test.model.Contract> contracts,
      Contract... expectedContracts) {
    for (Contract expectedContract : expectedContracts) {
      thenActiveContractMatches(contracts, expectedContract);
    }
  }

  private void thenActiveContractMatches(
      List<com.redhat.swatch.contract.test.model.Contract> contracts, Contract expectedContract) {
    var actualContract =
        contracts.stream()
            .filter(
                contract ->
                    expectedContract
                        .getSubscriptionNumber()
                        .equals(contract.getSubscriptionNumber()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "Active contract not found: " + expectedContract.getSubscriptionNumber()));

    assertEquals(orgId, actualContract.getOrgId(), "Contract organization should match");
    assertEquals(
        expectedContract.getBillingAccountId(),
        actualContract.getBillingAccountId(),
        "Contract billing account should match");
    assertEquals("aws", actualContract.getBillingProvider(), "Contract should be AWS");
    thenContractCapacityShouldMatchOffering(actualContract, expectedContract);
  }

  private void thenMetricMatchesCapacityReports(
      MetricId metricId,
      List<com.redhat.swatch.contract.test.model.Contract> contracts,
      Contract[] expectedContracts,
      SkuCapacityReportV2 skuReport,
      SkuCapacityV2 skuCapacity) {
    double expectedCapacity = getExpectedCapacity(contracts, metricId);
    assertEquals(
        getExpectedRawCapacity(metricId, expectedContracts),
        expectedCapacity,
        0.01,
        "Contract metric total should be adjusted by the billing factor for " + metricId);
    assertEquals(
        expectedCapacity,
        getSkuCapacity(skuReport, skuCapacity, metricId),
        0.01,
        "SKU capacity should equal the summed active contract capacity for " + metricId);
    thenDailyCapacityMatches(metricId, expectedCapacity);
  }

  private SkuCapacityV2 thenSkuCapacityContainsContracts(
      SkuCapacityReportV2 report,
      String sku,
      List<com.redhat.swatch.contract.test.model.Contract> contracts) {
    assertNotNull(report, "SKU capacity report should not be null");
    assertNotNull(report.getMeta(), "SKU capacity report meta should not be null");
    assertEquals(
        Product.ROSA.getName(),
        report.getMeta().getProduct(),
        "SKU capacity report product should be ROSA");
    assertNotNull(report.getMeta().getMeasurements(), "SKU report measurements should not be null");
    assertNotNull(report.getData(), "SKU capacity report data should not be null");

    List<SkuCapacityV2> matchingSkuRows =
        report.getData().stream().filter(capacity -> sku.equals(capacity.getSku())).toList();
    assertEquals(1, matchingSkuRows.size(), "SKU capacity report should contain one matching SKU");

    SkuCapacityV2 skuCapacity = matchingSkuRows.get(0);
    thenSkuCapacityContainsSubscriptions(skuCapacity, contracts);
    return skuCapacity;
  }

  private void thenSkuCapacityContainsSubscriptions(
      SkuCapacityV2 skuCapacity, List<com.redhat.swatch.contract.test.model.Contract> contracts) {
    assertNotNull(skuCapacity.getMeasurements(), "SKU measurements should not be null");
    assertNotNull(skuCapacity.getSubscriptions(), "SKU subscriptions should not be null");
    assertEquals(
        contracts.size(),
        skuCapacity.getSubscriptions().size(),
        "SKU capacity should include every active contract");
    for (var contract : contracts) {
      assertTrue(
          skuCapacity.getSubscriptions().stream()
              .anyMatch(
                  subscription ->
                      contract.getSubscriptionNumber().equals(subscription.getNumber())),
          "SKU capacity should include contract " + contract.getSubscriptionNumber());
    }
  }

  private void assertContractMetrics(
      Contract expectedContract, com.redhat.swatch.contract.test.model.Contract actualContract) {
    Map<String, Double> expectedMetrics = expectedContract.getContractMetrics();
    assertEquals(
        expectedMetrics.size(),
        actualContract.getMetrics().size(),
        "Contract should contain exactly the expected metrics");

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

  private double getExpectedCapacity(
      List<com.redhat.swatch.contract.test.model.Contract> contracts, MetricId metricId) {
    Metric metricDefinition = Product.ROSA.getMetric(metricId);
    String dimension = metricDefinition.getAwsDimension();
    double contractMetricTotal =
        contracts.stream()
            .mapToDouble(contract -> getContractMetricValue(contract, dimension))
            .sum();
    double billingFactor = Optional.ofNullable(metricDefinition.getBillingFactor()).orElse(1.0);
    return contractMetricTotal / billingFactor;
  }

  private double getContractMetricValue(
      com.redhat.swatch.contract.test.model.Contract contract, String dimension) {
    var matchingMetrics =
        contract.getMetrics().stream()
            .filter(metric -> dimension.equals(metric.getMetricId()))
            .toList();
    assertEquals(
        1, matchingMetrics.size(), "Contract should contain exactly one metric for " + dimension);
    assertNotNull(matchingMetrics.get(0).getValue(), "Contract metric value should not be null");
    return matchingMetrics.get(0).getValue().doubleValue();
  }

  private double getExpectedRawCapacity(MetricId metricId, Contract... contracts) {
    double capacity = 0.0;
    for (Contract contract : contracts) {
      Double contractCapacity = contract.getSubscriptionMeasurements().get(metricId);
      assertNotNull(contractCapacity, "Expected contract capacity should not be null");
      capacity += contractCapacity;
    }
    return capacity;
  }

  private double getSkuCapacity(
      SkuCapacityReportV2 report, SkuCapacityV2 skuCapacity, MetricId metricId) {
    int metricIndex = report.getMeta().getMeasurements().indexOf(metricId.toString());
    assertTrue(metricIndex >= 0, "Metric not found in SKU capacity report: " + metricId);
    assertTrue(
        metricIndex < skuCapacity.getMeasurements().size(),
        "SKU measurement index is outside the returned measurements: " + metricId);
    Double measurement = skuCapacity.getMeasurements().get(metricIndex);
    assertNotNull(measurement, "SKU capacity measurement should not be null for " + metricId);
    return measurement;
  }

  private void thenDailyCapacityMatches(MetricId metricId, double expectedCapacity) {
    OffsetDateTime ending = clock.now();
    CapacityReportByMetricId report =
        service.getCapacityReportByMetricId(
            Product.ROSA,
            orgId,
            metricId.toString(),
            ending.minusDays(1),
            ending.plusDays(1),
            GranularityType.DAILY,
            null);
    thenDailyCapacityReportIsValid(report, metricId);

    List<CapacitySnapshotByMetricId> populatedSnapshots =
        report.getData().stream()
            .filter(snapshot -> Boolean.TRUE.equals(snapshot.getHasData()))
            .toList();
    assertFalse(
        populatedSnapshots.isEmpty(), "Daily capacity report should contain populated snapshots");
    for (CapacitySnapshotByMetricId snapshot : populatedSnapshots) {
      assertNotNull(snapshot.getDate(), "Daily capacity snapshot date should not be null");
      assertNotNull(snapshot.getValue(), "Daily capacity snapshot value should not be null");
      assertEquals(
          expectedCapacity,
          snapshot.getValue().doubleValue(),
          0.01,
          "Daily capacity should match for " + metricId + " on " + snapshot.getDate());
    }
  }

  private void thenDailyCapacityReportIsValid(CapacityReportByMetricId report, MetricId metricId) {
    assertNotNull(report, "Daily capacity report should not be null");
    assertNotNull(report.getMeta(), "Daily capacity report meta should not be null");
    assertEquals(
        Product.ROSA.getName(),
        report.getMeta().getProduct(),
        "Daily capacity report product should be ROSA");
    assertEquals(
        metricId.toString(),
        report.getMeta().getMetricId(),
        "Daily capacity report metric should match the requested metric");
    assertEquals(
        GranularityType.DAILY,
        report.getMeta().getGranularity(),
        "Daily capacity report granularity should be daily");
    assertNotNull(report.getData(), "Daily capacity report data should not be null");
    assertFalse(report.getData().isEmpty(), "Daily capacity report data should not be empty");
    assertTrue(
        report.getData().stream().anyMatch(snapshot -> Boolean.TRUE.equals(snapshot.getHasData())),
        "Daily capacity report should contain a data snapshot");
  }
}
