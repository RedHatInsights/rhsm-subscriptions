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

import static api.BillableUsageTestHelper.createTallySummaryWithDefaults;
import static api.BillableUsageUnleashService.ENABLE_READ_ONLY;
import static api.MessageValidators.billableUsageMatchesTallyId;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.model.InfoFeatureFlag;
import com.redhat.swatch.component.tests.model.InfoFeatureFlags;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import java.time.Duration;
import java.util.List;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;

public class ReadOnlyModeComponentTest extends BaseBillableUsageComponentTest {

  private static final String TALLY_CHANNEL = "tally-summary";
  private static final String PAUSING_CONSUMER_LOG =
      "Pausing consumer '%s'".formatted(TALLY_CHANNEL);
  private static final String RESUMING_CONSUMER_LOG =
      "Resuming consumer '%s'".formatted(TALLY_CHANNEL);
  private static final double TALLY_VALUE = 8.0;

  @TestPlanName("read-only-TC001")
  @Test
  void shouldPauseAndResumeConsumerWithFeatureFlag() {
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // By default, the channel is consuming, so tallies produce billable usage
    TallySummary first = whenTallySummaryIsPublished();
    thenBillableUsageIsProducedForTally(first);

    // Then, we enable flag and wait for the service to pause the consumer
    unleash.enableReadOnly();
    service.logs().assertContains(PAUSING_CONSUMER_LOG);

    // And we confirm that the channel is NOT consuming messages
    TallySummary second = whenTallySummaryIsPublished();
    thenBillableUsageIsNotProducedForTally(second);

    // Disable flag and wait for the service to resume the consumer
    unleash.disableReadOnly();
    service.logs().assertContains(RESUMING_CONSUMER_LOG);

    // Then the pending message is processed after resume
    thenBillableUsageIsProducedForTally(second);
  }

  @TestPlanName("read-only-TC002")
  @Test
  void shouldExposeReadOnlyFlagOnInfoEndpoint() {
    unleash.enableReadOnly();

    InfoFeatureFlags featureFlags =
        service
            .getFeatureFlags()
            .orElseThrow(() -> new AssertionError("Management /info must expose feature-flags"));
    InfoFeatureFlag flag =
        featureFlags.getFlags().stream()
            .filter(entry -> ENABLE_READ_ONLY.equals(entry.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Read-only feature flag missing from /info"));

    assertEquals(ENABLE_READ_ONLY, flag.getName());
    assertTrue(flag.getEnabled());
  }

  private TallySummary whenTallySummaryIsPublished() {
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), TALLY_VALUE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private void thenBillableUsageIsProducedForTally(TallySummary tallySummary) {
    List<BillableUsage> usages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, billableUsageMatchesTallyId(tallySummary), 1);
    assertEquals(1, usages.size(), "Expected billable usage for tally " + tallySummary);
  }

  private void thenBillableUsageIsNotProducedForTally(TallySummary tallySummary) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0,
                kafkaBridge
                    .waitForKafkaMessage(
                        BILLABLE_USAGE,
                        billableUsageMatchesTallyId(tallySummary),
                        0,
                        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                    .size(),
                "Expected no billable usage while consumer is paused"));
  }
}
