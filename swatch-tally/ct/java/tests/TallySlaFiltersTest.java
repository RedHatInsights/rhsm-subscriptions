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

import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TallySlaFiltersTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final List<Sla> slas = List.of(Sla.PREMIUM, Sla.STANDARD, Sla.SELF_SUPPORT);

  @Test
  public void testTallySlaFiltersCount() {
    OffsetDateTime now = OffsetDateTime.now();

    // Produce events for each SLA
    for (Sla sla : slas) {
      Event event =
          helpers.createEventWithTimestamp(
              orgId,
              UUID.randomUUID().toString(),
              now.minusHours(1).toString(),
              UUID.randomUUID().toString(),
              TEST_METRIC_ID,
              3.0f,
              sla,
              HardwareType.CLOUD,
              TEST_PRODUCT_ID,
              TEST_PRODUCT_TAG);
      kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
    }

    // Produce some "no SLA" event
    Event noSlaEvent =
        helpers.createEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            now.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            3.0f,
            Sla.__EMPTY__,
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, noSlaEvent);

    // Poll for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 4, service, kafkaBridge);

    // Compute counts for each SLA
    double finalFilterCount = 0;
    for (Sla sla : slas) {
      double value =
          helpers.getTallySummaryValue(
              tallySummaries,
              orgId,
              TEST_PRODUCT_TAG,
              TEST_METRIC_ID,
              Granularity.HOURLY,
              sla.toString());
      finalFilterCount += value;
    }

    // No-SLA bucket: sla = EMPTY
    double noSla =
        helpers.getTallySummaryValue(
            tallySummaries,
            orgId,
            TEST_PRODUCT_TAG,
            TEST_METRIC_ID,
            Granularity.HOURLY,
            Sla.__EMPTY__.toString());

    // All tally summary values total: no SLA filter applied
    double allTallySummaries =
        helpers.getTallySummaryValue(
            tallySummaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, null);

    // Verify count with SLA filters
    Assertions.assertEquals(allTallySummaries - finalFilterCount, noSla, 0.0001);
    Assertions.assertEquals(finalFilterCount + noSla, allTallySummaries, 0.0001);
  }
}
