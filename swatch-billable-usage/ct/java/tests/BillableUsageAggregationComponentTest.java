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

import static api.BillableUsageTestHelper.createTallySummaryWithCurrentTotal;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.MessageValidators;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import domain.RemittanceStatus;
import java.time.Duration;
import java.util.List;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BillableUsageAggregationComponentTest extends BaseBillableUsageComponentTest {

  private static final double BILLING_FACTOR = ROSA.getBillingFactor(CORES);

  /**
   * Drain hourly windows left by prior CT runs on shared compose Kafka. An early flush while only
   * some billable usages are in the window permanently splits the hourly rollup.
   */
  @BeforeEach
  void drainStaleAggregationWindows() {
    service.flushBillableUsageAggregationTopic();
  }

  @Test
  @TestPlanName("billable-usage-aggregation-TC001")
  public void shouldAggregateMultipleTalliesIntoSingleHourlyMessage() {
    // Given: No contract coverage; a billing account ID shared across all tallies
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
    String billingAccountId = RandomUtils.generateRandom();

    double[] currentTotals = {10.0, 16.0, 23.0};
    TallySummary[] tallies = new TallySummary[currentTotals.length];

    for (int i = 0; i < currentTotals.length; i++) {
      double previousTotal = i > 0 ? currentTotals[i - 1] : 0.0;
      tallies[i] =
          createTallySummaryWithCurrentTotal(
              orgId,
              ROSA.getName(),
              CORES.toString(),
              currentTotals[i] - previousTotal,
              currentTotals[i],
              BillingProvider.AWS,
              billingAccountId);
    }

    // When: Publish all tallies quickly so Kafka Streams sees every billable usage in the same
    // hourly window before any flush can close it (sequential publish widens that race).
    for (TallySummary tally : tallies) {
      kafkaBridge.produceKafkaMessage(TALLY, tally);
    }

    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA.getName()), 3);
    for (BillableUsage billableUsage : billableUsages) {
      waitForRemittanceStatus(billableUsage.getTallyId().toString(), RemittanceStatus.PENDING);
    }

    // Then: Flush once per retry and require a complete hourly aggregate. Never attach flush to
    // waitForKafkaMessage polls — that fires every second and can close the window early.
    double expectedTotalValue =
        Math.ceil(currentTotals[currentTotals.length - 1] * BILLING_FACTOR);

    AwaitilityUtils.untilAsserted(
        () -> {
          service.flushBillableUsageAggregationTopic();
          BillableUsageAggregate aggregate =
              kafkaBridge
                  .waitForKafkaMessage(
                      BILLABLE_USAGE_HOURLY_AGGREGATE,
                      MessageValidators.billableUsageAggregateMatchesComplete(
                          orgId, expectedTotalValue, currentTotals.length),
                      1,
                      AwaitilitySettings.using(Duration.ofMillis(500), Duration.ofSeconds(15)))
                  .getLast();

          BillableUsageAggregateKey key = aggregate.getAggregateKey();
          assertNotNull(key, "Aggregate key should not be null");
          assertEquals(orgId, key.getOrgId(), "Aggregate org ID should match");
          assertEquals(ROSA.getName(), key.getProductId(), "Aggregate product ID should match");
          assertEquals(CORES.toString(), key.getMetricId(), "Aggregate metric ID should match");
          assertEquals(
              billingAccountId,
              key.getBillingAccountId(),
              "Aggregate billing account ID should match");
          assertEquals("aws", key.getBillingProvider(), "Aggregate billing provider should match");
          assertEquals(
              expectedTotalValue,
              aggregate.getTotalValue().doubleValue(),
              0.001,
              "Total value should be ceil(finalCurrentTotal × factor) = ceil(23 × 0.25) = 6");
          assertEquals(
              currentTotals.length,
              aggregate.getRemittanceUuids().size(),
              "Should have 3 remittance UUIDs (one per tally)");
        },
        AwaitilitySettings.using(Duration.ofSeconds(2), Duration.ofSeconds(60)));
  }
}
