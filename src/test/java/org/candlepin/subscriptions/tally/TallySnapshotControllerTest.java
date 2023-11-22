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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class TallySnapshotControllerTest {

  private static final String ORG_ID = "test-org";
  private static final String SERVICE_TYPE = "OpenShift Cluster";

  @MockBean EventRecordRepository eventRepo;
  @Autowired RetryTemplate collectorRetryTemplate;
  @Autowired ApplicationClock clock;
  @Autowired ApplicationProperties props;
  @MockBean TallySnapshotRepository snapshotRepo;
  @Autowired MetricUsageCollector usageCollector;
  @Autowired TallySnapshotController controller;

  @BeforeEach
  void setupTest() {}

  @Test
  void verifyRecoveryWhenHostUpdateFailsAndRetryOccurs() {}

  @Test
  void verifyRecoveryWhenCalculationUpdateFailsAndRetryOccurs() {
    // Successfully calculate usage for a single event, and
    // simulate an exception looking up existing usage for the second.
    // On retry, the first event should be in the cache already, and should not
    // get applied a second time. The second event should be applied successfully.

    TestingRetryListener retryListener = new TestingRetryListener();
    collectorRetryTemplate.registerListener(retryListener);

    OffsetDateTime firstSnapshotHour = clock.startOfCurrentHour();
    OffsetDateTime secondSnapshotHour = firstSnapshotHour.minusHours(1);

    EventRecord event1 = createEvent(firstSnapshotHour, createMeasurement(42.0));
    EventRecord event2 = createEvent(secondSnapshotHour, createMeasurement(20.0));
    EventRecord event3 = createEvent(firstSnapshotHour, createMeasurement(8.0));

    when(eventRepo.fetchEventsInBatchByRecordDate(
            ORG_ID, SERVICE_TYPE, clock.startOfCurrentHour(), props.getHourlyTallyEventBatchSize()))
        .thenReturn(List.of(event1, event2, event3));

    when(snapshotRepo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .thenReturn(Stream.empty())
        .thenThrow(new RuntimeException("FORCED"))
        .thenReturn(Stream.empty())
        .thenReturn(Stream.empty())
        .thenReturn(Stream.empty());

    ArgumentCaptor<TallySnapshot> snapshotCaptor = ArgumentCaptor.forClass(TallySnapshot.class);
    when(snapshotRepo.save(snapshotCaptor.capture())).thenAnswer(input -> input.getArgument(0));

    controller.produceHourlySnapshotsForOrg(ORG_ID);

    assertTrue(retryListener.didRetryOccur(), "Expected a forced retry to occur but it didn't!!!");

    List<TallySnapshot> createdSnapshots = snapshotCaptor.getAllValues();
    assertEquals(48, createdSnapshots.size());

    TallySnapshot eventOneHourly =
        createdSnapshots.stream()
            .filter(
                s ->
                    Granularity.HOURLY.equals(s.getGranularity())
                        && s.getSnapshotDate().equals(firstSnapshotHour))
            .findFirst()
            .orElseThrow();
    assertSnapshot(eventOneHourly, 50.0);

    TallySnapshot secondHourly =
        createdSnapshots.stream()
            .filter(
                s ->
                    Granularity.HOURLY.equals(s.getGranularity())
                        && s.getSnapshotDate().equals(secondSnapshotHour))
            .findFirst()
            .orElseThrow();
    assertSnapshot(secondHourly, 20.0);

    TallySnapshot dailySnapshot =
        createdSnapshots.stream()
            .filter(
                s ->
                    Granularity.DAILY.equals(s.getGranularity())
                        && s.getSnapshotDate().equals(clock.startOfDay(firstSnapshotHour)))
            .findFirst()
            .orElseThrow();
    assertSnapshot(dailySnapshot, 70.0);
  }

  EventRecord createEvent(OffsetDateTime usageTimestamp, Measurement measurement) {
    EventRecord eventRecord =
        new EventRecord(
            new Event()
                .withEventId(UUID.randomUUID())
                .withOrgId(ORG_ID)
                .withInstanceId(UUID.randomUUID().toString())
                .withEventId(UUID.randomUUID())
                .withRole(Event.Role.OSD)
                .withTimestamp(usageTimestamp)
                .withServiceType(SERVICE_TYPE)
                .withMeasurements(Collections.singletonList(measurement))
                .withBillingProvider(Event.BillingProvider.RED_HAT)
                .withBillingAccountId(Optional.of("sellerAcct")));
    eventRecord.setRecordDate(clock.now());
    return eventRecord;
  }

  Measurement createMeasurement(Double value) {
    return new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(value);
  }

  private void assertSnapshot(TallySnapshot snap, Double expectedValue) {
    assertEquals(
        expectedValue,
        snap.getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
  }

  private class TestingRetryListener implements RetryListener {
    private boolean retryOccurred;

    @Override
    public void onError(RetryContext context, RetryCallback callback, Throwable throwable) {
      retryOccurred = true;
    }

    public boolean didRetryOccur() {
      return retryOccurred;
    }
  }
}
