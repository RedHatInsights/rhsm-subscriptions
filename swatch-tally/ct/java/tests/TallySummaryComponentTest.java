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
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_UNCONVERTED;

import api.MessageValidators;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
public class TallySummaryComponentTest extends BaseTallyComponentTest {

  @Test
  public void testTallyNightlySummaryEmitsGranularityDaily() {
    final String testInventoryId = UUID.randomUUID().toString();

    helpers.seedNightlyTallyHostBuckets(
        orgId, RHEL_FOR_X86_ELS_UNCONVERTED.productTag(), testInventoryId, service);

    // Trigger nightly tally (PUT snapshots for org)
    service.tallyOrg(orgId);

    // Wait for tally summary messages with DAILY granularity
    kafkaBridge.waitForKafkaMessage(
        TALLY,
        MessageValidators.tallySummaryMatches(
            orgId,
            RHEL_FOR_X86_ELS_UNCONVERTED.productTag(),
            RHEL_FOR_X86_ELS_UNCONVERTED.metricIds().get(1),
            Granularity.DAILY),
        1);
  }

  @Test
  public void testTallyNightlySummaryHasNoTotalMeasurements() {
    final String testInventoryId = UUID.randomUUID().toString();

    helpers.seedNightlyTallyHostBuckets(
        orgId, RHEL_FOR_X86_ELS_UNCONVERTED.productTag(), testInventoryId, service);

    service.tallyOrg(orgId);

    AwaitilitySettings kafkaConsumerTimeout =
        AwaitilitySettings.using(Duration.ofMillis(500), Duration.ofSeconds(60));
    List<TallySummary> summaries =
        kafkaBridge.waitForKafkaMessage(
            TALLY,
            MessageValidators.tallySummaryMatches(
                orgId,
                RHEL_FOR_X86_ELS_UNCONVERTED.productTag(),
                RHEL_FOR_X86_ELS_UNCONVERTED.metricIds().get(1),
                Granularity.DAILY),
            1,
            kafkaConsumerTimeout);

    Assertions.assertFalse(summaries.isEmpty());
    summaries.stream()
        .flatMap(summary -> summary.getTallySnapshots().stream())
        .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
        .forEach(
            measurement ->
                Assertions.assertNotEquals(
                    "TOTAL",
                    measurement.getHardwareMeasurementType().toUpperCase(),
                    "Nightly snapshots should not emit TOTAL measurement type"));
  }

  @Test
  public void testTallyHourlySummaryEmitsNoGranularityDaily() {
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last day for hourly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

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
  public void testTallyHourlySummaryEmitsGranularityHourly() {
    final String testInstanceId = UUID.randomUUID().toString();
    final String testEventId = UUID.randomUUID().toString();

    // Create events within the last day for hourly tally
    OffsetDateTime now = OffsetDateTime.now();
    Event event1 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(1).toString(), testEventId, 1.0f);

    Event event2 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(2).toString(), testEventId, 1.0f);

    Event event3 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(3).toString(), testEventId, 1.0f);

    Event event4 =
        helpers.createEventWithTimestamp(
            orgId, testInstanceId, now.minusHours(4).toString(), testEventId, 1.0f);

    // Produce events to Kafka
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event1);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event2);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event3);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event4);

    // Polls then extracts measurements from resulting snapshots.
    // Ensures that the appropriate number of summaries is present
    // and that no measurements are of type 'TOTAL'
    helpers
        .pollForTallySyncAndMessages(
            orgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            RHEL_FOR_X86_ELS_PAYG.metricIds().get(1),
            Granularity.HOURLY,
            4,
            service,
            kafkaBridge)
        .stream()
        .flatMap(summary -> summary.getTallySnapshots().stream())
        .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
        .forEach(
            measurement ->
                Assertions.assertNotSame(
                    "TOTAL", measurement.getHardwareMeasurementType().toUpperCase()));
  }
}
