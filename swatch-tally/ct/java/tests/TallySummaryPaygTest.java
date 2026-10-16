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
import static utils.TallyTestProducts.ROSA;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.TallyMeasurement;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

  @Test
  @TestPlanName("tally-summary-payg-TC003")
  void shouldIncludeLaterHourInEarlierSummaryCurrentTotal() {
    YearMonth previousMonth = YearMonth.from(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(1);
    OffsetDateTime earlierHour = previousMonth.atDay(15).atTime(10, 0).atOffset(ZoneOffset.UTC);
    OffsetDateTime laterHour = earlierHour.plusHours(1);

    String metricId = ROSA.metricIds().get(1);
    String summaryMetric = metricId.replace('-', '_');
    service.createOptInConfig(orgId);
    Event earlierEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            earlierHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            2.0f,
            ROSA.productId(),
            ROSA.productTag());
    Event laterEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            laterHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            3.0f,
            ROSA.productId(),
            ROSA.productTag());
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, earlierEvent);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, laterEvent);

    List<TallySummary> summaries =
        helpers.pollForTallySyncAndMessages(
            orgId, ROSA.productTag(), summaryMetric, Granularity.HOURLY, 2, service, kafkaBridge);

    thenHourHasMonthlyTotal(summaries, earlierHour, summaryMetric, 2.0, 5.0);
    thenHourHasMonthlyTotal(summaries, laterHour, summaryMetric, 3.0, 5.0);
  }

  @Test
  @TestPlanName("tally-summary-payg-TC004")
  void shouldIncludeExistingMonthSnapshotsInLateArrivalTotal() {
    YearMonth previousMonth = YearMonth.from(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(1);
    OffsetDateTime earlierHour = previousMonth.atDay(15).atTime(10, 0).atOffset(ZoneOffset.UTC);
    OffsetDateTime middleHour = previousMonth.atDay(17).atTime(10, 0).atOffset(ZoneOffset.UTC);
    OffsetDateTime laterHour = previousMonth.atDay(20).atTime(10, 0).atOffset(ZoneOffset.UTC);

    String metricId = ROSA.metricIds().get(1);
    String summaryMetric = metricId.replace('-', '_');
    String tag = ROSA.productTag();
    Granularity hourly = Granularity.HOURLY;
    String ingressTopic = SWATCH_SERVICE_INSTANCE_INGRESS;
    service.createOptInConfig(orgId);
    Event earlierEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            earlierHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            2.0f,
            ROSA.productId(),
            ROSA.productTag());
    Event laterEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            laterHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            4.0f,
            ROSA.productId(),
            ROSA.productTag());
    kafkaBridge.produceKafkaMessage(ingressTopic, earlierEvent);
    kafkaBridge.produceKafkaMessage(ingressTopic, laterEvent);

    List<TallySummary> initialSummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, tag, summaryMetric, hourly, 2, service, kafkaBridge);

    thenHourHasMonthlyTotal(initialSummaries, laterHour, summaryMetric, 4.0, 6.0);

    Event middleEvent =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            middleHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            3.0f,
            ROSA.productId(),
            ROSA.productTag());
    kafkaBridge.produceKafkaMessage(ingressTopic, middleEvent);

    List<TallySummary> summaries =
        helpers.pollForTallySyncAndMessages(
            orgId, tag, summaryMetric, hourly, 3, service, kafkaBridge);

    thenHourHasMonthlyTotal(summaries, middleHour, summaryMetric, 3.0, 9.0);
  }

  private void thenHourHasMonthlyTotal(
      List<TallySummary> summaries,
      OffsetDateTime hour,
      String metric,
      double expectedValue,
      double expectedTotal) {
    String product = ROSA.productId();
    List<TallyMeasurement> measurements =
        summaries.stream()
            .flatMap(summary -> summary.getTallySnapshots().stream())
            .filter(snap -> product.equalsIgnoreCase(snap.getProductId()))
            .filter(snapshot -> hour.isEqual(snapshot.getSnapshotDate()))
            .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
            .filter(m -> metric.equalsIgnoreCase(m.getMetricId()))
            .toList();
    Assertions.assertFalse(measurements.isEmpty(), hour.toString());
    measurements.forEach(
        measurement -> {
          Assertions.assertEquals(expectedValue, measurement.getValue());
          Assertions.assertEquals(expectedTotal, measurement.getCurrentTotal());
        });
  }
}
