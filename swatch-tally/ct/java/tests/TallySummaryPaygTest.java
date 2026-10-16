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
import static utils.TallyTestProducts.ROSA;

import api.MessageValidators;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.TallyMeasurement;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TallySummaryPaygTest extends BaseTallyComponentTest {

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
    var measurements =
        summaries.stream()
            .flatMap(summary -> summary.getTallySnapshots().stream())
            .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
            .toList();

    Assertions.assertFalse(measurements.isEmpty(), "Expected hourly measurements");
    Assertions.assertTrue(
        measurements.stream()
            .noneMatch(m -> "TOTAL".equalsIgnoreCase(m.getHardwareMeasurementType())),
        "Hourly measurements should not be of type TOTAL");
  }

  @Test
  @TestPlanName("tally-summary-payg-TC003")
  void shouldIncludeLaterHourInEarlierSummaryCurrentTotal() {
    // Given: Two ROSA events in one UTC month and one in the following month
    YearMonth previousMonth = YearMonth.from(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(1);
    OffsetDateTime earlierHour = previousMonth.atDay(15).atTime(10, 0).atOffset(ZoneOffset.UTC);
    OffsetDateTime laterHour = earlierHour.plusHours(1);
    OffsetDateTime nextMonthHour =
        previousMonth.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

    String metricId = ROSA.metricIds().get(1);
    String summaryMetric = metricId.replace('-', '_');
    service.createOptInConfig(orgId);
    Event earlierEvent = givenRosaEvent(earlierHour, 2.0f);
    Event laterEvent = givenRosaEvent(laterHour, 3.0f);
    Event nextMonthEvent = givenRosaEvent(nextMonthHour, 7.0f);

    // When: All three events are ingested and hourly tally runs
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, earlierEvent);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, laterEvent);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, nextMonthEvent);
    givenEventsPersisted(3);
    service.performHourlyTallyForOrg(orgId);

    // Then: The next-month value is excluded from the earlier summaries
    TallySummary earlierSummary = thenSummaryForHour(earlierHour, summaryMetric);
    TallySummary laterSummary = thenSummaryForHour(laterHour, summaryMetric);
    TallySummary nextMonthSummary = thenSummaryForHour(nextMonthHour, summaryMetric);
    thenHourHasMonthlyTotal(earlierSummary, earlierHour, summaryMetric, 2.0, 5.0);
    thenHourHasMonthlyTotal(laterSummary, laterHour, summaryMetric, 3.0, 5.0);
    thenHourHasMonthlyTotal(nextMonthSummary, nextMonthHour, summaryMetric, 7.0, 7.0);
  }

  @Test
  @TestPlanName("tally-summary-payg-TC004")
  void shouldIncludeExistingMonthSnapshotsInLateArrivalTotal() {
    // Given: Events from the 15th and 20th are ingested and tallied
    YearMonth previousMonth = YearMonth.from(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(1);
    OffsetDateTime earlierHour = previousMonth.atDay(15).atTime(10, 0).atOffset(ZoneOffset.UTC);
    OffsetDateTime middleHour = previousMonth.atDay(17).atTime(10, 0).atOffset(ZoneOffset.UTC);
    OffsetDateTime laterHour = previousMonth.atDay(20).atTime(10, 0).atOffset(ZoneOffset.UTC);

    String metricId = ROSA.metricIds().get(1);
    String summaryMetric = metricId.replace('-', '_');
    service.createOptInConfig(orgId);
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS, givenRosaEvent(earlierHour, 2.0f));
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS, givenRosaEvent(laterHour, 4.0f));
    givenEventsPersisted(2);
    service.performHourlyTallyForOrg(orgId);

    TallySummary laterSummary = thenSummaryForHour(laterHour, summaryMetric);
    thenHourHasMonthlyTotal(laterSummary, laterHour, summaryMetric, 4.0, 6.0);

    // When: An event from the 17th arrives and hourly tally runs again
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS, givenRosaEvent(middleHour, 3.0f));
    givenEventsPersisted(3);
    service.performHourlyTallyForOrg(orgId);

    // Then: The new summary includes all three event values
    TallySummary middleSummary = thenSummaryForHour(middleHour, summaryMetric);
    thenHourHasMonthlyTotal(middleSummary, middleHour, summaryMetric, 3.0, 9.0);
  }

  @Test
  @TestPlanName("tally-summary-payg-TC005")
  void shouldUseUtcMonthForNonUtcEventCurrentTotal() {
    // Given: An event dated in one local month falls in the following UTC month
    YearMonth snapshotMonth = YearMonth.from(OffsetDateTime.now(ZoneOffset.UTC)).minusMonths(1);
    OffsetDateTime monthStartUtc = snapshotMonth.atDay(1).atTime(3, 30).atOffset(ZoneOffset.UTC);
    OffsetDateTime nonUtcEventTime = monthStartUtc.withOffsetSameInstant(ZoneOffset.ofHours(-4));
    OffsetDateTime laterHour = snapshotMonth.atDay(20).atTime(10, 0).atOffset(ZoneOffset.UTC);
    String summaryMetric = ROSA.metricIds().get(1).replace('-', '_');
    service.createOptInConfig(orgId);

    // When: Both events are ingested and hourly tally runs
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS, givenRosaEvent(nonUtcEventTime, 2.0f));
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS, givenRosaEvent(laterHour, 3.0f));
    givenEventsPersisted(2);
    service.performHourlyTallyForOrg(orgId);

    // Then: The first event is tallied in the UTC month with the later event
    OffsetDateTime firstHourUtc = monthStartUtc.truncatedTo(ChronoUnit.HOURS);
    TallySummary firstSummary = thenSummaryForHour(firstHourUtc, summaryMetric);
    TallySummary laterSummary = thenSummaryForHour(laterHour, summaryMetric);
    Assertions.assertTrue(
        firstSummary.getTallySnapshots().stream()
            .anyMatch(snapshot -> firstHourUtc.equals(snapshot.getSnapshotDate())));
    thenHourHasMonthlyTotal(firstSummary, firstHourUtc, summaryMetric, 2.0, 5.0);
    thenHourHasMonthlyTotal(laterSummary, laterHour, summaryMetric, 3.0, 5.0);
  }

  private Event givenRosaEvent(OffsetDateTime timestamp, float value) {
    return helpers.createPaygEventWithTimestamp(
        orgId,
        UUID.randomUUID().toString(),
        timestamp.toString(),
        UUID.randomUUID().toString(),
        ROSA.metricIds().get(1),
        value,
        ROSA.productId(),
        ROSA.productTag());
  }

  private void givenEventsPersisted(int expectedCount) {
    AwaitilityUtils.untilIsTrue(
        () -> {
          try (Connection connection = swatchDatabase.getConnection();
              PreparedStatement statement =
                  connection.prepareStatement("SELECT COUNT(*) FROM events WHERE org_id = ?")) {
            statement.setString(1, orgId);
            try (ResultSet results = statement.executeQuery()) {
              results.next();
              return results.getInt(1) >= expectedCount;
            }
          }
        });
  }

  private TallySummary thenSummaryForHour(OffsetDateTime hour, String metric) {
    return kafkaBridge.waitForKafkaMessage(
        TALLY,
        MessageValidators.tallySummaryMatchesHour(
            orgId, ROSA.productTag(), metric, Granularity.HOURLY, hour));
  }

  private void thenHourHasMonthlyTotal(
      TallySummary summary,
      OffsetDateTime hour,
      String metric,
      double expectedValue,
      double expectedTotal) {
    String product = ROSA.productId();
    List<TallyMeasurement> measurements =
        summary.getTallySnapshots().stream()
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
