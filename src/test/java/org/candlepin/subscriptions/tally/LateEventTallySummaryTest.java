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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.support.RetryTemplate;

@ExtendWith(MockitoExtension.class)
class LateEventTallySummaryTest {

  private static final String ORG_ID = "late-event-org";
  private static final String PRODUCT_ID = "ansible-aap-managed";
  private static final String BILLING_ACCOUNT_ID = "12345";
  private static final String TOPIC = "summary-topic";
  private static final TallyMeasurementKey MEASUREMENT_KEY =
      new TallyMeasurementKey(
          HardwareMeasurementType.PHYSICAL, MetricIdUtils.getManagedNodes().getValue());
  private static final TallyMeasurementKey COUNTER_MEASUREMENT_KEY =
      new TallyMeasurementKey(
          HardwareMeasurementType.PHYSICAL, MetricIdUtils.getInstanceHours().getValue());

  @Mock TallySnapshotRepository snapshotRepository;
  @Mock FeatureFlags featureFlags;
  @Mock KafkaTemplate<String, TallySummary> kafka;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void latePreviousMonthUsageMustPublishCorrectedMonthlyTotal(boolean primaryRowSearch) {
    // SWATCH-5820: September usage is processed in October. Ten hours of 20 nodes
    // total 200. With contract coverage of 60, billing has already remitted 140.
    List<TallySnapshot> storedSnapshots = new ArrayList<>();
    for (int day = 11; day <= 20; day++) {
      storedSnapshots.add(
          snapshot(OffsetDateTime.of(2026, 9, day, 13, 0, 0, 0, ZoneOffset.UTC), 20.0));
    }

    List<TallySummary> published = new ArrayList<>();
    SnapshotSummaryProducer producer =
        producerFor(storedSnapshots, published, primaryRowSearch, MEASUREMENT_KEY);

    producer.produceTallySummaryMessages(
        Map.of(ORG_ID, storedSnapshots),
        List.of(Granularity.HOURLY),
        SnapshotSummaryProducer.HOURLY_SNAP_FILTER);
    assertEquals(200.0, largestPublishedTotal(published), "Baseline usage must reach billing");

    // A newly recorded event adds 40 nodes at an earlier September hour. The
    // tally run publishes affected snapshots, so only this new hour is supplied.
    TallySnapshot lateSnapshot = snapshot(OffsetDateTime.parse("2026-09-12T11:00:00Z"), 40.0);
    storedSnapshots.add(lateSnapshot);
    assertEquals(
        240.0,
        storedSnapshots.stream()
            .mapToDouble(s -> s.getTallyMeasurements().get(MEASUREMENT_KEY))
            .sum());
    published.clear();

    producer.produceTallySummaryMessages(
        Map.of(ORG_ID, List.of(lateSnapshot)),
        List.of(Granularity.HOURLY),
        SnapshotSummaryProducer.HOURLY_SNAP_FILTER);

    // Billing needs an updated total of 240 to remit the missing 40 (240 - 60 - 140).
    // Currently only 60 is published: September 11's 20 plus the late hour's 40.
    // Assert the required observable output without prescribing whether tally
    // updates this message or also re-emits a later snapshot's summary.
    assertEquals(
        240.0,
        largestPublishedTotal(published),
        "SWATCH-5820: late usage must publish the corrected monthly total so billing can remit"
            + " the additional 40 nodes");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void repeatedCounterSnapshotUpdatesUseLatestValueRatherThanSumOfPublications(
      boolean primaryRowSearch) {
    TallySnapshot previous =
        snapshot(OffsetDateTime.parse("2026-09-20T12:00:00Z"), 30.0, COUNTER_MEASUREMENT_KEY);
    TallySnapshot updated =
        snapshot(OffsetDateTime.parse("2026-09-20T13:00:00Z"), 10.0, COUNTER_MEASUREMENT_KEY);
    List<TallySnapshot> storedSnapshots = List.of(previous, updated);
    List<TallySummary> published = new ArrayList<>();
    SnapshotSummaryProducer producer =
        producerFor(storedSnapshots, published, primaryRowSearch, COUNTER_MEASUREMENT_KEY);

    // Multiple publications represent revisions of one counter interval. They
    // are replacement values, not increments to add to previously emitted values.
    // Include replay and a downward correction so retaining a maximum also fails.
    double[] replacementValues = {10.0, 15.0, 20.0, 20.0, 5.0};
    double[] expectedPeriodTotals = {40.0, 45.0, 50.0, 50.0, 35.0};
    for (int i = 0; i < replacementValues.length; i++) {
      updated.setMeasurement(
          HardwareMeasurementType.PHYSICAL,
          MetricIdUtils.getInstanceHours(),
          replacementValues[i]);
      published.clear();

      producer.produceTallySummaryMessages(
          Map.of(ORG_ID, List.of(updated)),
          List.of(Granularity.HOURLY),
          SnapshotSummaryProducer.HOURLY_SNAP_FILTER);

      assertEquals(1, published.size());
      var emittedSnapshot = published.getFirst().getTallySnapshots().getFirst();
      assertEquals(updated.getId(), emittedSnapshot.getId(), "The snapshot identity is unchanged");
      assertEquals(updated.getSnapshotDate(), emittedSnapshot.getSnapshotDate());
      var measurement = emittedSnapshot.getTallyMeasurements().getFirst();
      assertEquals(COUNTER_MEASUREMENT_KEY.getMetricId(), measurement.getMetricId());
      assertEquals(replacementValues[i], measurement.getValue());
      assertEquals(
          expectedPeriodTotals[i],
          measurement.getCurrentTotal(),
          "The period total must include the earlier 30 plus only the latest snapshot value");
    }
  }

  private SnapshotSummaryProducer producerFor(
      List<TallySnapshot> storedSnapshots,
      List<TallySummary> published,
      boolean primaryRowSearch,
      TallyMeasurementKey measurementKey) {
    ApplicationClock clock =
        new ApplicationClock(
            Clock.fixed(Instant.parse("2026-10-13T18:00:00Z"), ZoneOffset.UTC));
    when(featureFlags.isEnabled(FeatureFlags.ENABLE_PRIMARY_ROW_SEARCHES))
        .thenReturn(primaryRowSearch);

    // Model the repository's inclusive range sum over persisted snapshots using
    // the actual query bounds, rather than stub a predetermined currentTotal.
    Answer<Double> sumMeasurements =
        invocation -> {
          OffsetDateTime beginning = invocation.getArgument(7);
          OffsetDateTime ending = invocation.getArgument(8);
          return storedSnapshots.stream()
              .filter(s -> !s.getSnapshotDate().isBefore(beginning))
              .filter(s -> !s.getSnapshotDate().isAfter(ending))
              .mapToDouble(s -> s.getTallyMeasurements().get(measurementKey))
              .sum();
        };
    if (primaryRowSearch) {
      when(snapshotRepository.sumMeasurementValueForPeriodWithPrimary(
              eq(ORG_ID),
              eq(PRODUCT_ID),
              eq(Granularity.HOURLY),
              eq(ServiceLevel.PREMIUM),
              eq(Usage.PRODUCTION),
              eq(BillingProvider.AWS),
              eq(BILLING_ACCOUNT_ID),
              any(OffsetDateTime.class),
              any(OffsetDateTime.class),
              eq(measurementKey)))
          .thenAnswer(sumMeasurements);
    } else {
      when(snapshotRepository.sumMeasurementValueForPeriod(
              eq(ORG_ID),
              eq(PRODUCT_ID),
              eq(Granularity.HOURLY),
              eq(ServiceLevel.PREMIUM),
              eq(Usage.PRODUCTION),
              eq(BillingProvider.AWS),
              eq(BILLING_ACCOUNT_ID),
              any(OffsetDateTime.class),
              any(OffsetDateTime.class),
              eq(measurementKey)))
          .thenAnswer(sumMeasurements);
    }

    doAnswer(
            invocation -> {
              published.add(invocation.getArgument(2));
              return CompletableFuture.completedFuture(null);
            })
        .when(kafka)
        .send(eq(TOPIC), eq(ORG_ID), any(TallySummary.class));

    TallySummaryProperties properties = new TallySummaryProperties();
    properties.setTopic(TOPIC);
    return new SnapshotSummaryProducer(
        kafka,
        new RetryTemplate(),
        properties,
        new TallySummaryMapper(snapshotRepository, clock, featureFlags));
  }

  private static double largestPublishedTotal(List<TallySummary> summaries) {
    return summaries.stream()
        .flatMap(summary -> summary.getTallySnapshots().stream())
        .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
        .filter(measurement -> MEASUREMENT_KEY.getMetricId().equals(measurement.getMetricId()))
        .mapToDouble(measurement -> measurement.getCurrentTotal())
        .max()
        .orElseThrow();
  }

  private static TallySnapshot snapshot(OffsetDateTime date, double value) {
    return snapshot(date, value, MEASUREMENT_KEY);
  }

  private static TallySnapshot snapshot(
      OffsetDateTime date, double value, TallyMeasurementKey measurementKey) {
    return TallySnapshot.builder()
        .id(UUID.randomUUID())
        .orgId(ORG_ID)
        .productId(PRODUCT_ID)
        .granularity(Granularity.HOURLY)
        .snapshotDate(date)
        .serviceLevel(ServiceLevel.PREMIUM)
        .usage(Usage.PRODUCTION)
        .billingProvider(BillingProvider.AWS)
        .billingAccountId(BILLING_ACCOUNT_ID)
        .isPrimary(true)
        .tallyMeasurements(new java.util.HashMap<>(Map.of(measurementKey, value)))
        .build();
  }
}
