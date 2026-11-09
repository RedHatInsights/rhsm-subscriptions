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
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static domain.BillingProvider.AWS;
import static domain.Product.RHEL_PAYG_ADDON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallySummaryBuilder.buildTallySummaryWithDefaults;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import utils.TallySummaryBuilder;

public class BillingCalculationComponentTest extends BaseBillableUsageComponentTest {

  @AfterEach
  void flushAggregates() {
    service.flushBillableUsageAggregationTopic();
  }

  @TestPlanName("billable-usage-billing-calculation-TC001")
  @Test
  void shouldNotCreateNewRemittanceWhenCurrentTotalDecreases() {
    // Given: First remittance (value 10) has SUCCEEDED
    double firstValue = 10.0;
    double decreasedTotal = 6.0;
    String billingAccountId = RandomUtils.generateRandom();
    BillableUsage firstBillableUsage =
        givenFirstRemittanceWithStatus(
            RHEL_PAYG_ADDON,
            RHEL_PAYG_ADDON.getMetric(VCPUS),
            AWS,
            firstValue,
            billingAccountId,
            BillableUsage.Status.SUCCEEDED,
            null,
            RemittanceStatus.SUCCEEDED);

    // When: Second tally summary with current_total = 6 (below the remitted 10)
    TallySummary secondTally =
        TallySummaryBuilder.buildTallySummaryWithCurrentTotal(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            MetricIdUtils.getVCpus().getValue(),
            decreasedTotal,
            decreasedTotal,
            AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, secondTally);

    // Then: Billable usage is emitted with value 0 (nothing billable when total decreased)
    List<BillableUsage> secondUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, RHEL_PAYG_ADDON, 0.0),
            1);
    assertEquals(
        1, secondUsages.size(), "Expected exactly one billable usage message emitted with value 0");

    // Then: Monthly total unchanged — no new remittance row for the second tally
    List<MonthlyRemittance> monthlyRemittances =
        service.getRemittances(
            orgId, RHEL_PAYG_ADDON, VCPUS, AWS.toTallyApiModel().value(), billingAccountId);
    assertEquals(1, monthlyRemittances.size(), "Only one monthly accumulation period expected");
    assertNotNull(monthlyRemittances.getFirst().getRemittedValue());
    assertEquals(
        firstValue,
        monthlyRemittances.getFirst().getRemittedValue(),
        0.001,
        "Monthly total should equal the first remittance only — no second remittance created");

    // Then: Original remittance unchanged
    thenRemittanceValueIs(firstBillableUsage, firstValue);
  }

  @TestPlanName("billable-usage-billing-calculation-TC002")
  @Test
  void shouldExcludeFailedRemittancesFromTotalRemitted() {
    // Given: First remittance (value 10) has FAILED
    double firstValue = 10.0;
    double secondTotal = 6.0;
    String billingAccountId = RandomUtils.generateRandom();
    BillableUsage firstBillableUsage =
        givenFirstRemittanceWithStatus(
            RHEL_PAYG_ADDON,
            RHEL_PAYG_ADDON.getMetric(VCPUS),
            AWS,
            firstValue,
            billingAccountId,
            BillableUsage.Status.FAILED,
            BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND,
            RemittanceStatus.FAILED);

    // When: Second tally summary with current_total = 6 (failed remittance is excluded)
    TallySummary secondTally =
        TallySummaryBuilder.buildTallySummaryWithCurrentTotal(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            secondTotal,
            secondTotal,
            AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, secondTally);

    // Then: Billable usage is emitted with full current_total (failed not counted as remitted)
    List<BillableUsage> secondUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, RHEL_PAYG_ADDON, secondTotal),
            1);
    assertEquals(
        1, secondUsages.size(), "Expected exactly one billable usage message with value 6");

    // Then: Two remittance rows — first FAILED, second PENDING with value 6
    thenRemittanceStatusIs(firstBillableUsage, RemittanceStatus.FAILED);

    var secondUsage = secondUsages.getFirst();
    thenRemittanceStatusIs(secondUsage, RemittanceStatus.PENDING);
    thenRemittanceValueIs(secondUsage, secondTotal);
  }

  @TestPlanName("billable-usage-billing-calculation-TC003")
  @Test
  void shouldUpdateSnapshotDateToRemittanceDateOnOutput() {
    // Given: Tally with snapshot date one hour in the past
    givenNoContractCoverageForProduct(RHEL_PAYG_ADDON);
    OffsetDateTime beforeRemittance = OffsetDateTime.now(ZoneOffset.UTC);
    TallySummary tallySummary =
        buildTallySummaryWithDefaults(orgId, RHEL_PAYG_ADDON.getName(), VCPUS.toString(), 8.0);
    OffsetDateTime originalSnapshotDate =
        tallySummary.getTallySnapshots().getFirst().getSnapshotDate();

    // When: Tally is published
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Billable usage is emitted on Kafka
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, RHEL_PAYG_ADDON), 1);
    assertEquals(1, billableUsages.size(), "Expected exactly one billable usage message");
    OffsetDateTime emittedSnapshotDate = billableUsages.getFirst().getSnapshotDate();

    // Then: emitted snapshot_date is not the original tally date (which was ~1 hour ago)
    assertTrue(
        emittedSnapshotDate.isAfter(originalSnapshotDate.plusMinutes(59)),
        String.format(
            "Emitted snapshot_date %s should be updated from the original tally date %s to the remittance creation time",
            emittedSnapshotDate, originalSnapshotDate));

    // Then: emitted snapshot_date is close to the remittance creation time
    assertTrue(
        Duration.between(beforeRemittance, emittedSnapshotDate).abs().toSeconds() <= 10,
        String.format(
            "Emitted snapshot_date %s should be within 10 seconds of remittance creation time %s",
            emittedSnapshotDate, beforeRemittance));
  }
}
