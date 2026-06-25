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
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Component tests for ROSA contract-adjustment remittance (SWATCH-4615).
 *
 * <p>Contract coverage is stubbed via {@link api.ContractsWiremockService}; usage arrives on Kafka
 * {@code TALLY}; assertions use {@link api.BillableUsageSwatchService#getRemittances}. Expected
 * values come from {@link api.BillableUsageRemittanceExpectations}.
 */
public class ContractAdjustmentComponentTest extends BaseBillableUsageComponentTest {

  private static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();
  private static final String CORES_AWS_DIMENSION = ROSA.getMetric(CORES).getAwsDimension();
  private static final String INSTANCE_HOURS_AWS_DIMENSION =
      ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
  private static final double CORES_BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  // Instance-hours has no billingFactor in rosa.yaml; production default is 1.0
  private static final double INSTANCE_HOURS_BILLING_FACTOR = 1.0;

  /**
   * Removing a contract mid-month must not change remittance already recorded; usage billed after
   * the contract is restored must apply the adjustment formula.
   */
  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC001")
  public void testVerifyContractAdjustmentForRemoveContract() {
    String billingAccountId = RandomUtils.generateRandom();
    double contractMetricValue = 6.0;
    double applicableMetricUsage = 100.0;
    double totalBillingUsage = applicableMetricUsage * 2;
    Map<MetricId, Double> remittedByMetric = new HashMap<>();

    // Phase 1: contract (coverage 6), first tally increment 100 → initial remittance per metric
    givenRosaContractWithEqualMetricCoverage(contractMetricValue);
    whenUsageIsTallied(billingAccountId, applicableMetricUsage, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedInitialRemittance(
              applicableMetricUsage, metric.billingFactor(), contractMetricValue);
      remittedByMetric.put(
          metric.metricId(), thenAccountRemittanceEquals(metric, billingAccountId, expected));
    }

    // Phase 2: contract removed, re-tally with zero increment → remittance unchanged
    givenContractIsRemoved();
    whenUsageIsTallied(billingAccountId, 0.0, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      thenAccountRemittanceEquals(
          metric, billingAccountId, remittedByMetric.get(metric.metricId()));
    }

    // Phase 3: contract restored, second increment 100 (month total 200) → adjusted remittance
    givenRosaContractWithEqualMetricCoverage(contractMetricValue);
    whenUsageIsTallied(billingAccountId, applicableMetricUsage, totalBillingUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedRemittanceAfterUsageIncrease(
              totalBillingUsage,
              remittedByMetric.get(metric.metricId()),
              metric.billingFactor(),
              contractMetricValue);
      thenAccountRemittanceEquals(metric, billingAccountId, expected);
    }
  }

  /**
   * Adding a second contract mid-month keeps remittance at the first contract's value until usage
   * exceeds combined coverage (contracts 10 + 100; usage 100 → 200 → 501).
   */
  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC002")
  public void testVerifyContractAdjustmentWhenAddMoreContractInMidMonth() {
    String billingAccountId = RandomUtils.generateRandom();
    double initialContractMetricValue = 10.0;
    double addedContractMetricValue = 100.0;
    double totalContractMetricValue = initialContractMetricValue + addedContractMetricValue;
    double applicableMetricUsage = 100.0;
    Map<MetricId, Double> remittedByMetric = new HashMap<>();

    // Phase 1: first contract (coverage 10), tally increment 100
    givenRosaContractWithEqualMetricCoverage(initialContractMetricValue);
    whenUsageIsTallied(billingAccountId, applicableMetricUsage, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedInitialRemittance(
              applicableMetricUsage, metric.billingFactor(), initialContractMetricValue);
      remittedByMetric.put(
          metric.metricId(), thenAccountRemittanceEquals(metric, billingAccountId, expected));
    }

    // Phase 2: second contract added, re-tally only — remittance still reflects first contract
    givenMultipleRosaContractsWithEqualMetricCoverage(
        initialContractMetricValue, addedContractMetricValue);
    whenUsageIsTallied(billingAccountId, 0.0, applicableMetricUsage);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      thenAccountRemittanceEquals(
          metric, billingAccountId, remittedByMetric.get(metric.metricId()));
    }

    // Phase 3: second usage increment (month total 200) — still first-contract remittance
    double totalAfterSecondEvent = applicableMetricUsage * 2;
    whenUsageIsTallied(billingAccountId, applicableMetricUsage, totalAfterSecondEvent);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      thenAccountRemittanceEquals(
          metric, billingAccountId, remittedByMetric.get(metric.metricId()));
    }

    // Phase 4: large third increment (month total 501) — remittance uses combined coverage
    double totalAfterThirdEvent = totalAfterSecondEvent + 301.0;
    whenUsageIsTallied(billingAccountId, 301.0, totalAfterThirdEvent);

    for (RosaMetricScenario metric : rosaMetricScenarios()) {
      double expected =
          expectedRemittanceAfterUsageIncrease(
              totalAfterThirdEvent,
              remittedByMetric.get(metric.metricId()),
              metric.billingFactor(),
              totalContractMetricValue);
      thenAccountRemittanceEquals(metric, billingAccountId, expected);
    }
  }

  /** ROSA AWS metrics exercised in each test (Cores and Instance-hours). */
  private record RosaMetricScenario(MetricId metricId, String awsDimension, double billingFactor) {}

  private List<RosaMetricScenario> rosaMetricScenarios() {
    return List.of(
        new RosaMetricScenario(CORES, CORES_AWS_DIMENSION, CORES_BILLING_FACTOR),
        new RosaMetricScenario(
            INSTANCE_HOURS, INSTANCE_HOURS_AWS_DIMENSION, INSTANCE_HOURS_BILLING_FACTOR));
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
    contractsWiremock.setupMultipleMultiMetricContracts(
        orgId,
        ROSA.getName(),
        List.of(
            equalCoverageForAllRosaMetrics(firstContractBillableUnits),
            equalCoverageForAllRosaMetrics(secondContractBillableUnits)));
  }

  /** Stub contracts API so no contract exists for the org/product. */
  private void givenContractIsRemoved() {
    contractsWiremock.setupContractNotFound(orgId, ROSA.getName());
  }

  /**
   * Publish a TALLY summary per ROSA metric. {@code value} is the increment; {@code currentTotal}
   * is the month-to-date cumulative usage.
   */
  private void whenUsageIsTallied(String billingAccountId, double value, double currentTotal) {
    for (RosaMetricScenario metric : rosaMetricScenarios()) {
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
  }

  /** Assert monthly remittance via API; billing provider must be {@code aws} (tally API value). */
  private double thenAccountRemittanceEquals(
      RosaMetricScenario metric, String billingAccountId, double expectedRemittedValue) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<MonthlyRemittance> remittances =
                  service.getRemittances(
                      ROSA.getName(),
                      orgId,
                      metric.metricId().toString(),
                      BillingProvider.AWS.toTallyApiModel().value(),
                      billingAccountId);
              assertEquals(1, remittances.size(), "Expected one monthly remittance row per metric");
              assertEquals(
                  expectedRemittedValue,
                  remittances.get(0).getRemittedValue(),
                  0.001,
                  "Account remittance mismatch for metric " + metric.metricId());
            });
    return expectedRemittedValue;
  }
}
