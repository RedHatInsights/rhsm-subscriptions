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
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
class TallySummaryPaygTest extends BaseTallyComponentTest {

  @Test
  @TestPlanName("tally-summary-payg-TC001")
  void testTallyHourlySummaryEmitsNoGranularityDaily() {
    // Given: Events within the last day for hourly tally
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);
    Event event3 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);
    Event event4 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    // When/Then: Polling for tally summaries with DAILY granularity should find 0 messages
    helpers.pollForTallySyncAndMessages(
        orgId,
        RHEL_FOR_X86_ELS_PAYG.productTag(),
        RHEL_FOR_X86_ELS_PAYG.metricIds().get(1),
        Granularity.DAILY,
        0,
        service,
        kafkaBridge);
  }

  @Test
  @TestPlanName("tally-summary-payg-TC002")
  void testTallyHourlySummaryEmitsGranularityHourly() {
    // Given: Events within the last day for hourly tally
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);
    Event event2 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);
    Event event3 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);
    Event event4 =
        helpers.createPaygEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    // When: Polling for tally summaries with HOURLY granularity
    List<TallySummary> summaries =
        helpers.pollForTallySyncAndMessages(
            orgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            RHEL_FOR_X86_ELS_PAYG.metricIds().get(1),
            Granularity.HOURLY,
            4,
            service,
            kafkaBridge);

    // Then: Should have 4 summaries and measurements should not be of type TOTAL
    summaries.stream()
        .flatMap(summary -> summary.getTallySnapshots().stream())
        .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
        .forEach(
            measurement ->
                Assertions.assertNotSame(
                    "TOTAL",
                    measurement.getHardwareMeasurementType().toUpperCase(),
                    "Hourly measurements should not be of type TOTAL"));
  }
}
