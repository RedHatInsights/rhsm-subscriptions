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

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Usage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TallyUsageFiltersTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final List<Usage> usages = List.of(Usage.PRODUCTION, Usage.DEVELOPMENT_TEST, Usage.DISASTER_RECOVERY);

  @Test
  @TestPlanName("tally-usage-filters-TC001")
  public void testTallyUsageFiltersCount() {
    // Given: Events for each Usage type and one event with no Usage
    OffsetDateTime now = OffsetDateTime.now();
    for (Usage usage : usages) {
      publishEvent(now, usage);
    }
    publishEvent(now, Usage.__EMPTY__);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 4, service, kafkaBridge);

    // Then: Usage-filtered counts should sum to the total tally value
    double finalFilterCount =
        usages.stream().mapToDouble(usage -> tallyValue(tallySummaries, usage.toString())).sum();
    double noUsage = tallyValue(tallySummaries, Usage.__EMPTY__.toString());
    double allTallySummaries = tallyValue(tallySummaries, null);

    Assertions.assertEquals(
        allTallySummaries - finalFilterCount,
        noUsage,
        0.0001,
        "No-Usage value should equal total minus Usage-filtered values");
    Assertions.assertEquals(
        finalFilterCount + noUsage,
        allTallySummaries,
        0.0001,
        "Usage-filtered values plus no-Usage should equal total");
  }

  private void publishEvent(OffsetDateTime now, Usage usage) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            now.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            3.0f,
            null,
            usage,
            null,
            null,
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double tallyValue(List<TallySummary> summaries, String usage) {
    return helpers.getTallySummaryValueWithUsageFilter(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, usage);
  }
}
