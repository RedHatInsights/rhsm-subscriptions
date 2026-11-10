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

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static domain.BillingProvider.AWS;
import static domain.Product.ROSA;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.BillableUsageAggregateHelper.buildBillableUsageAggregate;
import static utils.TallySummaryBuilder.buildTallySummaryWithCurrentTotal;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.ContractStub;
import domain.Product;
import domain.RemittanceStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.Test;

public class ContractAdjustmentComponentTest extends BaseBillableUsageComponentTest {

  private static final DateTimeFormatter MONTH_ID = DateTimeFormatter.ofPattern("uuuu-MM");

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC001")
  void shouldAdjustRemittanceWhenContractIsRemoved() {
    double contractMetricValue = 6.0;
    double applicableMetricUsage = 100.0;
    double totalBillingUsage = applicableMetricUsage * 2;
    Map<MetricId, Double> remittedByMetric = new HashMap<>();

    // Phase 1: contract (coverage 6), first tally increment 100 → initial remittance per metric
    givenRosaContractWithEqualMetricCoverage(contractMetricValue);
    whenUsageIsTallied(applicableMetricUsage, applicableMetricUsage);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      double expected =
          expectedInitialRemittance(
              applicableMetricUsage, ROSA.getBillingFactor(metricId), contractMetricValue);
      remittedByMetric.put(metricId, thenAccountRemittedUsageEquals(ROSA, metricId, expected));
    }

    // Phase 2: contract removed, re-tally with zero increment → remittance unchanged
    givenContractIsRemoved();
    whenUsageIsTallied(0.0, applicableMetricUsage);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      thenAccountRemittedUsageEquals(ROSA, metricId, remittedByMetric.get(metricId));
    }

    // Phase 3: contract restored, second increment 100 (month total 200) → adjusted remittance
    givenRosaContractWithEqualMetricCoverage(contractMetricValue);
    whenUsageIsTallied(applicableMetricUsage, totalBillingUsage);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      double expected =
          expectedRemittanceAfterUsageIncrease(
              totalBillingUsage,
              remittedByMetric.get(metricId),
              ROSA.getBillingFactor(metricId),
              contractMetricValue);
      thenAccountRemittedUsageEquals(ROSA, metricId, expected);
    }
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC002")
  void shouldAdjustRemittanceWhenContractIsAddedMidMonth() {
    double initialContractMetricValue = 10.0;
    double addedContractMetricValue = 100.0;
    double totalContractMetricValue = initialContractMetricValue + addedContractMetricValue;
    double applicableMetricUsage = 100.0;
    Map<MetricId, Double> remittedByMetric = new HashMap<>();

    // Phase 1: first contract (coverage 10), tally increment 100
    givenRosaContractWithEqualMetricCoverage(initialContractMetricValue);
    whenUsageIsTallied(applicableMetricUsage, applicableMetricUsage);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      double expected =
          expectedInitialRemittance(
              applicableMetricUsage, ROSA.getBillingFactor(metricId), initialContractMetricValue);
      remittedByMetric.put(metricId, thenAccountRemittedUsageEquals(ROSA, metricId, expected));
    }

    // Phase 2: second contract added, re-tally only — remittance still reflects first contract
    givenMultipleRosaContractsWithEqualMetricCoverage(
        initialContractMetricValue, addedContractMetricValue);
    whenUsageIsTallied(0.0, applicableMetricUsage);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      thenAccountRemittedUsageEquals(ROSA, metricId, remittedByMetric.get(metricId));
    }

    // Phase 3: second usage increment (month total 200) — still first-contract remittance
    double totalAfterSecondEvent = applicableMetricUsage * 2;
    whenUsageIsTallied(applicableMetricUsage, totalAfterSecondEvent);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      thenAccountRemittedUsageEquals(ROSA, metricId, remittedByMetric.get(metricId));
    }

    // Phase 4: large third increment (month total 501) — remittance uses combined coverage
    double totalAfterThirdEvent = totalAfterSecondEvent + 301.0;
    whenUsageIsTallied(301.0, totalAfterThirdEvent);

    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      double expected =
          expectedRemittanceAfterUsageIncrease(
              totalAfterThirdEvent,
              remittedByMetric.get(metricId),
              ROSA.getBillingFactor(metricId),
              totalContractMetricValue);
      thenAccountRemittedUsageEquals(ROSA, metricId, expected);
    }
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC003")
  void shouldRemitOnlyPostSwitchOverageWhenCoverageBecomesZero() {
    OffsetDateTime now = OffsetDateTime.now();

    // Phase 1: prepaid covers usage → nothing remitted.
    double prepaidCoverage = 1000.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", prepaidCoverage, now.minusDays(10), now.plusDays(10));

    double currentTotal = 100.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, currentTotal, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    // Phase 2: only newer empty-metric contract (capacity 0).
    givenRosaEmptyMetricContractWithLicense("license-new", now.minusHours(1), now.plusDays(10));

    // Phase 3: small new usage; remittance is only the post-switch overage.
    double deltaCurrentTotal = 5.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, deltaCurrentTotal, currentTotal + deltaCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, deltaCurrentTotal, "license-new");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC004")
  void shouldCoverAgainAfterOverageWhenContractRestored() {
    OffsetDateTime now = OffsetDateTime.now();

    // Phase 1: prepaid covers usage → COVERED only.
    double prepaidCoverage = 1000.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", prepaidCoverage, now.minusDays(10), now.plusDays(10));

    double currentTotal = 100.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, currentTotal, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    // Phase 2: only newer empty-metric contract (capacity 0).
    givenRosaEmptyMetricContractWithLicense("license-new", now.minusHours(1), now.plusDays(10));

    // Phase 3: post-switch overage under the empty-metric license.
    double firstDeltaCurrentTotal = 5.0;
    whenUsageIsTallied(
        ROSA, INSTANCE_HOURS, firstDeltaCurrentTotal, currentTotal + firstDeltaCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, firstDeltaCurrentTotal, "license-new");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    // Phase 4: restored coverage; further growth is COVERED, overage unchanged.
    // Tally snapshots use now-1h; contract start must be at or before that snapshot.
    double secondDeltaCurrentTotal = 15.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-restored", prepaidCoverage, now.minusHours(2), now.plusDays(10));
    whenUsageIsTallied(
        ROSA,
        INSTANCE_HOURS,
        secondDeltaCurrentTotal,
        currentTotal + firstDeltaCurrentTotal + secondDeltaCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, firstDeltaCurrentTotal, "license-new");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal + secondDeltaCurrentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC005")
  void shouldRemitOnlyDeltaAboveCoveredAfterOverage() {
    OffsetDateTime now = OffsetDateTime.now();

    double prepaidCoverage = 50.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", prepaidCoverage, now.minusDays(10), now.plusDays(10));

    double currentTotal = 80.0;
    double expectedFirstOverage = currentTotal - prepaidCoverage;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, currentTotal, currentTotal);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, prepaidCoverage);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, expectedFirstOverage);

    double deltaCurrentTotal = 15.0;
    givenRosaEmptyMetricContractWithLicense("license-new", now.minusHours(1), now.plusDays(10));
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, deltaCurrentTotal, currentTotal + deltaCurrentTotal);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, prepaidCoverage);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, expectedFirstOverage + deltaCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, deltaCurrentTotal, "license-new");
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC006")
  void shouldHoldCoveredWhenReplacementCoverageSmaller() {
    OffsetDateTime now = OffsetDateTime.now();

    // large contract
    double largeCoverage = 100.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", largeCoverage, now.minusDays(10), now.plusDays(10));

    double currentTotal = 80.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, currentTotal, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    // small contract - no increment
    double smallCoverage = 40.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-new", smallCoverage, now.minusHours(2), now.plusDays(10));
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 0.0, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    // growth fits under the new live coverage → COVERED, not marketplace remittance
    double deltaCurrentTotal = 10.0;
    double monthToDateAfterGrowth = currentTotal + deltaCurrentTotal;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, deltaCurrentTotal, monthToDateAfterGrowth);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, monthToDateAfterGrowth);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC007")
  void shouldRemitOnlyNewOverageAfterEmptyMetricCatchUp() {
    OffsetDateTime now = OffsetDateTime.now();

    double prepaidCoverage = 1000.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", prepaidCoverage, now.minusDays(10), now.plusDays(10));

    double firstCurrentTotal = 80.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, firstCurrentTotal, firstCurrentTotal);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstCurrentTotal);

    givenRosaEmptyMetricContractWithLicense("license-new", now.minusHours(1), now.plusDays(10));

    double secondCurrentTotal = 85.0;
    double expectedFirstOverage = secondCurrentTotal - firstCurrentTotal;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, expectedFirstOverage, secondCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, expectedFirstOverage, "license-new");

    double thirdCurrentTotal = 100.0;
    double expectedSecondOverage = thirdCurrentTotal - secondCurrentTotal;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, expectedSecondOverage, thirdCurrentTotal);
    thenAccountRemittedUsageEquals(
        ROSA, INSTANCE_HOURS, expectedSecondOverage + expectedFirstOverage);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstCurrentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC008")
  void shouldNotRemitWhenEmptyMetricsAndUsageUnchanged() {
    OffsetDateTime now = OffsetDateTime.now();

    double prepaidCoverage = 100.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", prepaidCoverage, now.minusDays(10), now.plusDays(10));

    double currentTotal = 80.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, currentTotal, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    givenRosaEmptyMetricContractWithLicense("license-new", now.minusHours(1), now.plusDays(10));
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 0.0, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);

    givenRosaInstanceHoursContractWithLicense(
        "license-restored", prepaidCoverage, now.minusHours(2), now.plusDays(10));
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 0.0, currentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC009")
  void shouldRemitFullMtdWhenPriorTallySkippedByApiError() {
    OffsetDateTime now = OffsetDateTime.now();

    givenContractsApiReturnsServerError();

    double firstCurrentTotal = 40.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, firstCurrentTotal, firstCurrentTotal);
    thenContractsApiWasCalled();
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, 0.0);

    givenRosaEmptyMetricContractWithLicense("license-new", now.minusHours(1), now.plusDays(10));

    double secondCurrentTotal = 80.0;
    whenUsageIsTallied(
        ROSA, INSTANCE_HOURS, secondCurrentTotal - firstCurrentTotal, secondCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, secondCurrentTotal, "license-new");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC010")
  void shouldRetryFailedOverageWithoutRemittingCovered() {
    OffsetDateTime now = OffsetDateTime.now();

    double prepaidCoverage = 50.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-prepaid", prepaidCoverage, now.minusDays(10), now.plusDays(10));

    double currentTotal = 80.0;
    double expectedRemitted = currentTotal - prepaidCoverage;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, currentTotal, currentTotal);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, prepaidCoverage);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, expectedRemitted);

    BillableUsage overage =
        kafkaBridge
            .waitForKafkaMessage(
                BILLABLE_USAGE,
                MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedRemitted),
                1)
            .getFirst();
    whenRemittedUsageMarkedFailed(overage);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, prepaidCoverage);

    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 0.0, currentTotal);
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesValueExcludingUuid(
            orgId, ROSA, expectedRemitted, overage.getUuid()),
        1);

    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, prepaidCoverage);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC011")
  void shouldCoverThenRemitOverageWithTwoActiveContracts() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime end = now.plusDays(30);

    double firstContractCovered = 40.0;
    double secondContractCovered = 30.0;
    givenRosaInstanceHoursContracts(
        List.of(
            new ContractStub(
                now.minusMonths(2),
                end,
                Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), firstContractCovered),
                "license-a"),
            new ContractStub(
                now.minusMonths(1),
                end,
                Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), secondContractCovered),
                "license-z")));

    double firstCurrentTotal = 55.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, firstCurrentTotal, firstCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    // ALLOCATION_ORDER: same endDate → newer start (Z) first → Z:30 A:25
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstCurrentTotal);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, secondContractCovered, "license-z");
    thenAccountCoveredUsageEquals(
        ROSA, INSTANCE_HOURS, firstCurrentTotal - secondContractCovered, "license-a");

    double combinedCoverage = secondContractCovered + firstContractCovered;
    double secondCurrentTotal = 90.0;
    whenUsageIsTallied(
        ROSA, INSTANCE_HOURS, secondCurrentTotal - firstCurrentTotal, secondCurrentTotal);
    thenAccountRemittedUsageEquals(
        ROSA, INSTANCE_HOURS, secondCurrentTotal - combinedCoverage, "license-z");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, combinedCoverage);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, secondContractCovered, "license-z");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstContractCovered, "license-a");
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC012")
  void shouldSkipGapThenCoverUnderReplacementLicense() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    double firstContractCovered = 100.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-a", firstContractCovered, now.minusDays(10), now.plusDays(10));

    double firstCurrentTotal = 80.0;
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, firstCurrentTotal, firstCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstCurrentTotal);

    givenContractIsRemoved();
    contractsWiremock.clearAllRequests();

    double secondCurrentTotal = 90.0;
    whenUsageIsTallied(
        ROSA, INSTANCE_HOURS, secondCurrentTotal - firstCurrentTotal, secondCurrentTotal);
    thenContractsApiWasCalled();
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstCurrentTotal);

    double secondContractCovered = 40.0;
    givenRosaInstanceHoursContractWithLicense(
        "license-y", secondContractCovered, now.minusHours(2), now.plusDays(10));

    double thirdCurrentTotal = 100.0;
    whenUsageIsTallied(
        ROSA, INSTANCE_HOURS, thirdCurrentTotal - secondCurrentTotal, thirdCurrentTotal);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, thirdCurrentTotal);

    double stackedFloor = firstCurrentTotal + secondContractCovered;
    double forthCurrentTotal = 150.0;
    whenUsageIsTallied(
        ROSA, INSTANCE_HOURS, forthCurrentTotal - thirdCurrentTotal, forthCurrentTotal);
    thenAccountRemittedUsageEquals(
        ROSA, INSTANCE_HOURS, forthCurrentTotal - stackedFloor, "license-y");
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, stackedFloor);
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC013")
  void shouldNotRemitGapUsageInNextMonth() {
    OffsetDateTime oct9 = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime oct12 = OffsetDateTime.of(2026, 10, 12, 12, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime nov1 = OffsetDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    String october = oct9.format(MONTH_ID);
    String november = nov1.format(MONTH_ID);

    givenRosaInstanceHoursContractWithLicense(
        "license-a", 100.0, oct9.minusDays(8), oct9.plusDays(1));
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 80.0, 80.0, oct9);
    thenAccountCoveredUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 80.0, october);
    thenAccountRemittedUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 0.0, october);

    givenContractIsRemoved();
    contractsWiremock.clearAllRequests();
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 10.0, 90.0, oct12);
    thenContractsApiWasCalled();
    thenAccountCoveredUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 80.0, october);
    thenAccountRemittedUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 0.0, october);

    givenRosaInstanceHoursContractWithLicense(
        "license-y", 40.0, nov1.minusHours(1), nov1.plusDays(29));
    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 50.0, 50.0, nov1);
    thenAccountCoveredUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 80.0, october);
    thenAccountRemittedUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 0.0, october);
    thenAccountCoveredUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 40.0, november);
    thenAccountRemittedUsageForPeriodEquals(ROSA, INSTANCE_HOURS, 10.0, november, "license-y");
  }

  @Test
  @TestPlanName("billable-usage-contract-adjustment-TC014")
  void shouldCoverUsageByContractActiveOnSnapshotDate() {
    OffsetDateTime oct9 = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime oct1 = OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime oct10 = OffsetDateTime.of(2026, 10, 10, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime oct13 = OffsetDateTime.of(2026, 10, 13, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime oct29 = OffsetDateTime.of(2026, 10, 29, 0, 0, 0, 0, ZoneOffset.UTC);

    givenRosaInstanceHoursContracts(
        List.of(
            new ContractStub(
                oct1,
                oct10,
                Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), 100.0),
                "license-a"),
            new ContractStub(
                oct13,
                oct29,
                Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), 40.0),
                "license-y")));

    whenUsageIsTallied(ROSA, INSTANCE_HOURS, 80.0, 80.0, oct9);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, 80.0, "license-a");
  }

  private Map<String, Double> equalCoverageForAllRosaMetrics(double contractBillableUnits) {
    return Map.of(
        ROSA.getMetric(CORES).getAwsDimension(), contractBillableUnits,
        ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), contractBillableUnits);
  }

  private void givenRosaContractWithEqualMetricCoverage(double contractBillableUnits) {
    contractsWiremock
        .forContractsAPI()
        .stubMultiMetricContractCoverage(
            orgId, ROSA, equalCoverageForAllRosaMetrics(contractBillableUnits));
  }

  private void givenMultipleRosaContractsWithEqualMetricCoverage(
      double firstContractBillableUnits, double secondContractBillableUnits) {
    OffsetDateTime startDate = OffsetDateTime.now().minusMonths(1);
    OffsetDateTime endDate = OffsetDateTime.now().plusYears(1);
    contractsWiremock
        .forContractsAPI()
        .stubContracts(
            orgId,
            ROSA,
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

  private void givenContractIsRemoved() {
    contractsWiremock.forContractsAPI().stubContractNotFound(orgId, ROSA);
  }

  private void givenRosaInstanceHoursContractWithLicense(
      String licenseId, double billableUnits, OffsetDateTime start, OffsetDateTime end) {
    contractsWiremock
        .forContractsAPI()
        .stubContracts(
            orgId,
            ROSA,
            List.of(
                new ContractStub(
                    start,
                    end,
                    Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), billableUnits),
                    licenseId)));
  }

  private void givenRosaInstanceHoursContracts(List<ContractStub> contracts) {
    contractsWiremock.forContractsAPI().stubContracts(orgId, ROSA, contracts);
  }

  private void givenRosaEmptyMetricContractWithLicense(
      String licenseId, OffsetDateTime start, OffsetDateTime end) {
    contractsWiremock
        .forContractsAPI()
        .stubContracts(orgId, ROSA, List.of(new ContractStub(start, end, Map.of(), licenseId)));
  }

  private void givenContractsApiReturnsServerError() {
    contractsWiremock.forContractsAPI().stubContractServiceError(orgId);
  }

  private void whenUsageIsTallied(double value, double currentTotal) {
    for (MetricId metricId : ROSA.getMetrics().keySet()) {
      whenUsageIsTallied(ROSA, metricId, value, currentTotal);
    }
  }

  private void whenUsageIsTallied(
      Product product, MetricId metricId, double value, double currentTotal) {
    whenUsageIsTallied(
        product,
        metricId,
        value,
        currentTotal,
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC));
  }

  private void whenUsageIsTallied(
      Product product,
      MetricId metricId,
      double value,
      double currentTotal,
      OffsetDateTime snapshotDate) {
    var tallySummary =
        buildTallySummaryWithCurrentTotal(
            orgId,
            product.getName(),
            metricId.getValue(),
            value,
            currentTotal,
            AWS,
            billingAccountId,
            snapshotDate);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
  }

  private void whenRemittedUsageMarkedFailed(BillableUsage overage) {
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
    AwaitilityUtils.untilAsserted(() -> {
      List<TallyRemittance> remittances = service.getRemittancesByTally(overage.getTallyId());
      assertTrue(
          remittances.stream()
              .filter(r -> r.getRemittedPendingValue() != null)
              .anyMatch(remittance ->
                  RemittanceStatus.FAILED.name().equalsIgnoreCase(remittance.getStatus())
                      && Math.abs(remittance.getRemittedPendingValue() - overage.getValue())
                          < 0.001),
          "Overage remittance should be FAILED before retry");
    });
  }

  private void thenContractsApiWasCalled() {
    AwaitilityUtils.untilAsserted(() ->
        assertTrue(
            contractsWiremock.countRequests(
                    "/mock/contractApi/api/swatch-contracts/internal/contracts")
                >= 1,
            "Expected contracts API call for the skipped tally"));
  }

  private static double expectedInitialRemittance(
      double metricUsage, double billingFactor, double contractBillableUnits) {
    double contractValue = contractBillableUnits / billingFactor;
    return Math.ceil(metricUsage * billingFactor) / billingFactor - contractValue;
  }

  /**
   * Remittance after new usage when contracts may have changed mid-month.
   *
   * <p>{@code alreadyRemitted + ceil((totalUsage - alreadyRemitted) × billingFactor) /
   * billingFactor − contractBillableUnits / billingFactor}
   */
  private static double expectedRemittanceAfterUsageIncrease(
      double totalUsage,
      double alreadyRemitted,
      double billingFactor,
      double contractBillableUnits) {
    double contractValue = contractBillableUnits / billingFactor;
    double applicableUsage =
        Math.ceil((totalUsage - alreadyRemitted) * billingFactor) / billingFactor;
    return alreadyRemitted + applicableUsage - contractValue;
  }
}
