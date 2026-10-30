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

import static api.BillableUsageRemittanceExpectations.expectedInitialRemittance;
import static api.BillableUsageRemittanceExpectations.expectedRemittanceAfterUsageIncrease;
import static api.BillableUsageTestHelper.createTallySummaryWithCurrentTotal;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.ContractStub;
import domain.RemittanceStatus;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.Test;

/**
 * Component tests for ROSA contract-adjustment remittance.
 *
 * <p>Contract coverage is stubbed via {@link api.ContractsWiremockService}; usage arrives on Kafka
 * {@code TALLY}; assertions use {@link api.BillableUsageSwatchService#getRemittances}. Expected
 * values for TC001–TC002 come from {@link api.BillableUsageRemittanceExpectations}.
 *
 * <p>Coverage is the <strong>sum</strong> of metrics on all valid contracts; the newest contract
 * {@code licenseId} is only used to attribute overage remittance.
 */
public class ContractAdjustmentComponentTest extends BaseBillableUsageComponentTest {

  private static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();
  private static final String CORES_AWS_DIMENSION = ROSA.getMetric(CORES).getAwsDimension();
  private static final String INSTANCE_HOURS_AWS_DIMENSION =
      ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
  private static final double CORES_BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  // Instance-hours has no billingFactor in rosa.yaml; production default is 1.0
  private static final double INSTANCE_HOURS_BILLING_FACTOR = 1.0;

  private static final String PREPAID_LICENSE = "license-prepaid";
  private static final String NEW_LICENSE = "license-new";
  private static final String RESTORED_LICENSE = "license-restored";
  private static final RosaMetricScenario INSTANCE_HOURS_METRIC =
      new RosaMetricScenario(
          INSTANCE_HOURS, INSTANCE_HOURS_AWS_DIMENSION, INSTANCE_HOURS_BILLING_FACTOR);

  /**
   * Removing a contract mid-month must not change remittance already recorded; usage billed after
   * the contract is restored must apply the adjustment formula.
   */
  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC001")
  public void testVerifyContractAdjustmentForRemoveContract() {
    double contractMetricValue = 6.0;
    double applicableMetricUsage = 100.0;
    double totalBillingUsage = applicableMetricUsage * 2;
    Map<MetricId, Double> remittedByMetric = new HashMap<>();

    // Phase 1: contract (coverage 6), first tally increment 100 → initial remittance per metric
    givenRosaContractWithEqualMetricCoverage(contractMetricValue);
    whenUsageIsTallied(applicableMetricUsage, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedInitialRemittance(
              applicableMetricUsage, metric.billingFactor(), contractMetricValue);
      remittedByMetric.put(metric.metricId(), thenAccountRemittanceEquals(metric, expected));
    }

    // Phase 2: contract removed, re-tally with zero increment → remittance unchanged
    givenContractIsRemoved();
    whenUsageIsTallied(0.0, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      thenAccountRemittanceEquals(metric, remittedByMetric.get(metric.metricId()));
    }

    // Phase 3: contract restored, second increment 100 (month total 200) → adjusted remittance
    givenRosaContractWithEqualMetricCoverage(contractMetricValue);
    whenUsageIsTallied(applicableMetricUsage, totalBillingUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedRemittanceAfterUsageIncrease(
              totalBillingUsage,
              remittedByMetric.get(metric.metricId()),
              metric.billingFactor(),
              contractMetricValue);
      thenAccountRemittanceEquals(metric, expected);
    }
  }

  /**
   * Adding a second contract mid-month keeps remittance at the first contract's value until usage
   * exceeds combined coverage (contracts 10 + 100; usage 100 → 200 → 501).
   */
  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC002")
  public void testVerifyContractAdjustmentWhenAddMoreContractInMidMonth() {
    double initialContractMetricValue = 10.0;
    double addedContractMetricValue = 100.0;
    double totalContractMetricValue = initialContractMetricValue + addedContractMetricValue;
    double applicableMetricUsage = 100.0;
    Map<MetricId, Double> remittedByMetric = new HashMap<>();

    // Phase 1: first contract (coverage 10), tally increment 100
    givenRosaContractWithEqualMetricCoverage(initialContractMetricValue);
    whenUsageIsTallied(applicableMetricUsage, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedInitialRemittance(
              applicableMetricUsage, metric.billingFactor(), initialContractMetricValue);
      remittedByMetric.put(metric.metricId(), thenAccountRemittanceEquals(metric, expected));
    }

    // Phase 2: second contract added, re-tally only — remittance still reflects first contract
    givenMultipleRosaContractsWithEqualMetricCoverage(
        initialContractMetricValue, addedContractMetricValue);
    whenUsageIsTallied(0.0, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      thenAccountRemittanceEquals(metric, remittedByMetric.get(metric.metricId()));
    }

    // Phase 3: second usage increment (month total 200) — still first-contract remittance
    double totalAfterSecondEvent = applicableMetricUsage * 2;
    whenUsageIsTallied(applicableMetricUsage, totalAfterSecondEvent);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      thenAccountRemittanceEquals(metric, remittedByMetric.get(metric.metricId()));
    }

    // Phase 4: large third increment (month total 501) — remittance uses combined coverage
    double totalAfterThirdEvent = totalAfterSecondEvent + 301.0;
    whenUsageIsTallied(301.0, totalAfterThirdEvent);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedRemittanceAfterUsageIncrease(
              totalAfterThirdEvent,
              remittedByMetric.get(metric.metricId()),
              metric.billingFactor(),
              totalContractMetricValue);
      thenAccountRemittanceEquals(metric, expected);
    }
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC003")
  void shouldRemitOnlyPostSwitchOverageWhenCoverageBecomesZero() {
    OffsetDateTime now = OffsetDateTime.now();
    double prepaidCoverage = 1000.0;
    double monthToDateWhileCovered = 100.0;
    double postSwitchIncrement = 5.0;
    double monthToDateAfterSwitch = monthToDateWhileCovered + postSwitchIncrement;
    double expectedOverage =
        expectedInitialRemittance(postSwitchIncrement, INSTANCE_HOURS_BILLING_FACTOR, 0.0);

    // Phase 1: prepaid covers usage → nothing remitted.
    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, prepaidCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, monthToDateWhileCovered, monthToDateWhileCovered);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateWhileCovered);

    // Phase 2: only newer empty-metric contract (capacity 0).
    givenRosaEmptyMetricContractWithLicense(NEW_LICENSE, now.minusHours(1), now.plusDays(10));

    // Phase 3: small new usage; remittance is only the post-switch overage.
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, postSwitchIncrement, monthToDateAfterSwitch);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, expectedOverage, NEW_LICENSE);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateWhileCovered);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC004")
  void shouldCoverAgainAfterOverageWhenContractRestored() {
    OffsetDateTime now = OffsetDateTime.now();
    double prepaidCoverage = 1000.0;
    double currentTotal = 100.0;

    // Phase 1: prepaid covers usage → COVERED only.
    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, prepaidCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, currentTotal, currentTotal);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, currentTotal);

    // Phase 2: only newer empty-metric contract (capacity 0).
    givenRosaEmptyMetricContractWithLicense(NEW_LICENSE, now.minusHours(1), now.plusDays(10));

    // Phase 3: post-switch overage under the empty-metric license.
    double firstDeltaCurrentTotal = 5.0;
    whenUsageIsTallied(
        INSTANCE_HOURS_METRIC, firstDeltaCurrentTotal, currentTotal + firstDeltaCurrentTotal);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, firstDeltaCurrentTotal, NEW_LICENSE);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, currentTotal);

    // Phase 4: restored coverage; further growth is COVERED, overage unchanged.
    // Tally snapshots use now-1h; contract start must be at or before that snapshot.
    double secondDeltaCurrentTotal = 15.0;
    givenRosaInstanceHoursContractWithLicense(
        RESTORED_LICENSE, prepaidCoverage, now.minusHours(2), now.plusDays(10));
    whenUsageIsTallied(
        INSTANCE_HOURS_METRIC,
        secondDeltaCurrentTotal,
        currentTotal + firstDeltaCurrentTotal + secondDeltaCurrentTotal);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, firstDeltaCurrentTotal, NEW_LICENSE);
    thenAccountCoveredRemittanceEquals(
        INSTANCE_HOURS_METRIC, currentTotal + secondDeltaCurrentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC005")
  void shouldRemitOnlyDeltaAboveCoveredAfterOverage() {
    OffsetDateTime now = OffsetDateTime.now();
    double prepaidCoverage = 50.0;
    double currentTotal = 80.0;
    double expectedFirstOverage = currentTotal - prepaidCoverage;

    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, prepaidCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, currentTotal, currentTotal);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, prepaidCoverage);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, expectedFirstOverage);

    double deltaCurrentTotal = 15.0;
    givenRosaEmptyMetricContractWithLicense(NEW_LICENSE, now.minusHours(1), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, deltaCurrentTotal, currentTotal + deltaCurrentTotal);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, prepaidCoverage);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, expectedFirstOverage + deltaCurrentTotal);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, deltaCurrentTotal, NEW_LICENSE);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC006")
  void shouldHoldCoveredWhenReplacementCoverageSmaller() {
    OffsetDateTime now = OffsetDateTime.now();
    double monthToDateUnderLargeContract = 80.0;

    // large contract
    double largeCoverage = 100.0;
    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, largeCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(
        INSTANCE_HOURS_METRIC, monthToDateUnderLargeContract, monthToDateUnderLargeContract);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateUnderLargeContract);

    // small contract - no increment
    double smallCoverage = 40.0;
    givenRosaInstanceHoursContractWithLicense(
        NEW_LICENSE, smallCoverage, now.minusHours(2), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, 0.0, monthToDateUnderLargeContract);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateUnderLargeContract);

    // growth fits under the new live coverage → COVERED, not marketplace remittance
    double deltaCurrentTotal = 10.0;
    double monthToDateAfterGrowth = monthToDateUnderLargeContract + deltaCurrentTotal;
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, deltaCurrentTotal, monthToDateAfterGrowth);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateAfterGrowth);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC007")
  void shouldRemitOnlyNewOverageAfterEmptyMetricCatchUp() {
    OffsetDateTime now = OffsetDateTime.now();
    double prepaidCoverage = 1000.0;
    double monthToDateWhileCovered = 80.0;
    double monthToDateAfterSwitch = 85.0;
    double monthToDateNextDay = 100.0;
    double expectedFirstOverage = 5.0;
    double expectedSecondOverage = 15.0;

    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, prepaidCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, monthToDateWhileCovered, monthToDateWhileCovered);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateWhileCovered);

    givenRosaEmptyMetricContractWithLicense(NEW_LICENSE, now.minusHours(1), now.plusDays(10));
    whenUsageIsTallied(
        INSTANCE_HOURS_METRIC,
        monthToDateAfterSwitch - monthToDateWhileCovered,
        monthToDateAfterSwitch);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, expectedFirstOverage, NEW_LICENSE);

    whenUsageIsTallied(
        INSTANCE_HOURS_METRIC, monthToDateNextDay - monthToDateAfterSwitch, monthToDateNextDay);
    thenAccountRemittanceEquals(
        INSTANCE_HOURS_METRIC, expectedFirstOverage + expectedSecondOverage);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDateWhileCovered);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC008")
  void shouldNotRemitWhenEmptyMetricsAndUsageUnchanged() {
    OffsetDateTime now = OffsetDateTime.now();
    double prepaidCoverage = 100.0;
    double monthToDate = 80.0;

    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, prepaidCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, monthToDate, monthToDate);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDate);

    givenRosaEmptyMetricContractWithLicense(NEW_LICENSE, now.minusHours(1), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, 0.0, monthToDate);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDate);

    givenRosaInstanceHoursContractWithLicense(
        RESTORED_LICENSE, prepaidCoverage, now.minusHours(2), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, 0.0, monthToDate);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, monthToDate);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC009")
  void shouldRemitFullMtdWhenPriorTallySkippedByApiError() {
    OffsetDateTime now = OffsetDateTime.now();
    double firstTotal = 40.0;
    double secondTotal = 80.0;

    givenContractsApiReturnsServerError();
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, firstTotal, firstTotal);
    thenContractsApiWasCalled();
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);

    givenRosaEmptyMetricContractWithLicense(NEW_LICENSE, now.minusHours(1), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, secondTotal - firstTotal, secondTotal);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, secondTotal, NEW_LICENSE);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC010")
  void shouldRetryFailedOverageWithoutRemittingCovered() {
    OffsetDateTime now = OffsetDateTime.now();
    double prepaidCoverage = 50.0;
    double currentTotal = 80.0;
    double expectedRemitted = currentTotal - prepaidCoverage;

    givenRosaInstanceHoursContractWithLicense(
        PREPAID_LICENSE, prepaidCoverage, now.minusDays(10), now.plusDays(10));
    whenUsageIsTallied(INSTANCE_HOURS_METRIC, currentTotal, currentTotal);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, prepaidCoverage);
    thenAccountRemittanceEquals(INSTANCE_HOURS_METRIC, expectedRemitted);

    BillableUsage overage =
        kafkaBridge
            .waitForKafkaMessage(
                BILLABLE_USAGE,
                MessageValidators.billableUsageMatchesWithValue(
                    orgId, ROSA.getName(), expectedRemitted),
                1)
            .getFirst();
    whenRemittedRemittanceMarkedFailed(overage);
    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, prepaidCoverage);

    whenUsageIsTallied(INSTANCE_HOURS_METRIC, 0.0, currentTotal);
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        new DefaultMessageValidator<>(
            usage ->
                Objects.equals(orgId, usage.getOrgId())
                    && Objects.equals(ROSA.getName(), usage.getProductId())
                    && Math.abs(usage.getValue() - expectedRemitted) < 0.001
                    && !Objects.equals(overage.getUuid(), usage.getUuid()),
            BillableUsage.class),
        1);

    thenAccountCoveredRemittanceEquals(INSTANCE_HOURS_METRIC, prepaidCoverage);
  }

  private record RosaMetricScenario(MetricId metricId, String awsDimension, double billingFactor) {}

  private List<RosaMetricScenario> rosaMetricScenarios() {
    return List.of(
        new RosaMetricScenario(CORES, CORES_AWS_DIMENSION, CORES_BILLING_FACTOR),
        INSTANCE_HOURS_METRIC);
  }

  private Map<String, Double> equalCoverageForAllRosaMetrics(double contractBillableUnits) {
    return Map.of(
        CORES_AWS_DIMENSION, contractBillableUnits,
        INSTANCE_HOURS_AWS_DIMENSION, contractBillableUnits);
  }

  /** Stub a single contract with equal billable units on every ROSA AWS dimension. */
  private void givenRosaContractWithEqualMetricCoverage(double contractBillableUnits) {
    contractsWiremock.setupMultiMetricContractCoverage(
        orgId, ROSA.getName(), equalCoverageForAllRosaMetrics(contractBillableUnits));
  }

  /** Stub two active contracts; billable-usage aggregates coverage across both. */
  private void givenMultipleRosaContractsWithEqualMetricCoverage(
      double firstContractBillableUnits, double secondContractBillableUnits) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    contractsWiremock.setupContracts(
        orgId,
        ROSA.getName(),
        List.of(
            new ContractStub(
                startDate,
                endDate,
                equalCoverageForAllRosaMetrics(firstContractBillableUnits),
                null),
            new ContractStub(
                startDate,
                endDate,
                equalCoverageForAllRosaMetrics(secondContractBillableUnits),
                null)));
  }

  /** Stub contracts API so no contract exists for the org/product. */
  private void givenContractIsRemoved() {
    contractsWiremock.setupContractNotFound(orgId, ROSA.getName());
  }

  private void givenRosaInstanceHoursContractWithLicense(
      String licenseId, double billableUnits, OffsetDateTime start, OffsetDateTime end) {
    contractsWiremock.setupContracts(
        orgId,
        ROSA.getName(),
        List.of(
            new ContractStub(
                start, end, Map.of(INSTANCE_HOURS_AWS_DIMENSION, billableUnits), licenseId)));
  }

  private void givenRosaEmptyMetricContractWithLicense(
      String licenseId, OffsetDateTime start, OffsetDateTime end) {
    contractsWiremock.setupContracts(
        orgId, ROSA.getName(), List.of(new ContractStub(start, end, Map.of(), licenseId)));
  }

  private void givenContractsApiReturnsServerError() {
    contractsWiremock.setupContractServiceError(orgId);
  }

  /**
   * Publish a TALLY summary per ROSA metric. {@code value} is the increment; {@code currentTotal}
   * is the month-to-date cumulative usage.
   */
  private void whenUsageIsTallied(double value, double currentTotal) {
    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      whenUsageIsTallied(metric, value, currentTotal);
    }
  }

  private void whenUsageIsTallied(RosaMetricScenario metric, double value, double currentTotal) {
    var tallySummary =
        createTallySummaryWithCurrentTotal(
            orgId,
            ROSA.getName(),
            metric.metricId().toString(),
            value,
            currentTotal,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
  }

  private void whenRemittedRemittanceMarkedFailed(BillableUsage overage) {
    kafkaBridge.produceKafkaMessage(
        BILLABLE_USAGE_STATUS,
        buildBillableUsageAggregate(
            orgId,
            ROSA.getName(),
            INSTANCE_HOURS.toString(),
            billingAccountId,
            BillableUsage.Status.FAILED,
            BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND,
            null,
            List.of(overage.getUuid().toString()),
            overage.getLicenseId()));
    // Same tally also has COVERED; wait until the overage remittance is FAILED.
    AwaitilityUtils.untilAsserted(
        () -> {
          List<TallyRemittance> remittances =
              service.getRemittancesByTally(overage.getTallyId().toString());
          assertTrue(
              remittances.stream()
                  .anyMatch(
                      remittance ->
                          RemittanceStatus.FAILED.name().equalsIgnoreCase(remittance.getStatus())
                              && Math.abs(remittance.getRemittedPendingValue() - overage.getValue())
                                  < 0.001),
              "Overage remittance should be FAILED before retry");
        });
  }

  private double thenAccountRemittanceEquals(
      RosaMetricScenario metric, double expectedRemittedValue) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                expectedRemittedValue,
                overageRemittances(metric).stream()
                    .mapToDouble(MonthlyRemittance::getRemittedValue)
                    .sum(),
                0.001,
                "Account remittance mismatch for metric " + metric.metricId()));
    return expectedRemittedValue;
  }

  private void thenAccountRemittanceEquals(
      RosaMetricScenario metric, double expectedRemittedValue, String expectedLicenseId) {
    AwaitilityUtils.untilAsserted(
        () -> {
          MonthlyRemittance remittance =
              overageRemittances(metric).stream()
                  .filter(row -> expectedLicenseId.equals(row.getLicenseId()))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new AssertionError(
                              "No overage remittance for license "
                                  + expectedLicenseId
                                  + " metric "
                                  + metric.metricId()));
          assertEquals(
              expectedRemittedValue,
              remittance.getRemittedValue(),
              0.001,
              "Remittance mismatch for " + metric.metricId());
          assertEquals(
              expectedLicenseId, remittance.getLicenseId(), "Remittance licenseId mismatch");
        });
  }

  private void thenAccountCoveredRemittanceEquals(
      RosaMetricScenario metric, double expectedCoveredValue) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                expectedCoveredValue,
                coveredRemittances(metric).stream()
                    .mapToDouble(MonthlyRemittance::getRemittedValue)
                    .sum(),
                0.001,
                "COVERED remittance mismatch for metric " + metric.metricId()));
  }

  private void thenContractsApiWasCalled() {
    AwaitilityUtils.untilAsserted(
        () ->
            assertTrue(
                contractsWiremock.countRequests(
                        "/mock/contractApi/api/swatch-contracts/internal/contracts")
                    >= 1,
                "Expected contracts API call for the skipped tally"));
  }

  private List<MonthlyRemittance> overageRemittances(RosaMetricScenario metric) {
    return accountRemittances(metric).stream()
        .filter(row -> !"covered".equalsIgnoreCase(row.getRemittanceStatus()))
        .toList();
  }

  private List<MonthlyRemittance> coveredRemittances(RosaMetricScenario metric) {
    return accountRemittances(metric).stream()
        .filter(row -> "covered".equalsIgnoreCase(row.getRemittanceStatus()))
        .toList();
  }

  private List<MonthlyRemittance> accountRemittances(RosaMetricScenario metric) {
    return service.getRemittances(
        ROSA.getName(),
        orgId,
        metric.metricId().toString(),
        BillingProvider.AWS.toTallyApiModel().value(),
        billingAccountId);
  }
}
