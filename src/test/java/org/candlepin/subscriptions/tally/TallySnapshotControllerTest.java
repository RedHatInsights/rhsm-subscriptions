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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
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
  @Autowired HostRepository hostRepository;
  @Autowired MetricUsageCollector usageCollector;
  @Autowired TallySnapshotController controller;

  @Test
  void verifyCorrectUpdatesWhenRetryOccurs() {
    // If a retry occurs due to an error condition, the Host.lastAppliedEventDate
    // should prevent the host from being updated multiple times, and the
    // AccountUsageCalculationCache should prevent Events from being calculated
    // multiple times in the same tally operation.
    //
    // This test will simulate an error when attempting to fetch the existing
    // calculations from the snapshots. When this happens, all Hosts in the
    // Event batch will have been updated, but no snapshots will have been created
    // until the retry happens.
    //
    // On retry, the Host updates will be skipped because they have already been
    // applied, and the calculations in the cache will be used based on the last
    // event record date set on the cache.

    TestingRetryListener retryListener = new TestingRetryListener();
    collectorRetryTemplate.registerListener(retryListener);

    OffsetDateTime firstSnapshotHour = clock.startOfCurrentHour();
    OffsetDateTime secondSnapshotHour = firstSnapshotHour.minusHours(1);

    String instance1Id = UUID.randomUUID().toString();
    EventRecord instance1Event1 =
        createEvent(instance1Id, firstSnapshotHour, createMeasurement(42.0));
    EventRecord instance2Event1 =
        createEvent(UUID.randomUUID().toString(), secondSnapshotHour, createMeasurement(20.0));
    EventRecord instance1Event2 =
        createEvent(instance1Id, firstSnapshotHour, createMeasurement(8.0));

    when(eventRepo.fetchEventsInBatchByRecordDate(
            ORG_ID, SERVICE_TYPE, clock.startOfCurrentHour(), props.getHourlyTallyEventBatchSize()))
        .thenReturn(List.of(instance1Event1, instance2Event1, instance1Event2));

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

    Map<String, Host> hosts =
        hostRepository.findAll().stream()
            .collect(Collectors.toMap(Host::getInstanceId, Function.identity()));
    assertEquals(2, hosts.size());

    // Host 1: monthly totals should be amended.
    // Host 2: monthly totals should remain the same.
    Host instance1 = hosts.get(instance1Id);
    assertEquals(
        50.0,
        instance1.getMonthlyTotal(
            InstanceMonthlyTotalKey.formatMonthId(instance1Event1.getTimestamp()),
            MetricIdUtils.getCores()));
    assertEquals(instance1.getLastAppliedEventRecordDate(), instance1Event2.getRecordDate());

    Host instance2 = hosts.get(instance2Event1.getInstanceId());
    assertEquals(
        20.0,
        instance2.getMonthlyTotal(
            InstanceMonthlyTotalKey.formatMonthId(instance2Event1.getTimestamp()),
            MetricIdUtils.getCores()));
    assertEquals(instance2.getLastAppliedEventRecordDate(), instance2Event1.getRecordDate());

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

  private EventRecord createEvent(
      String instanceId, OffsetDateTime usageTimestamp, Measurement measurement) {
    EventRecord eventRecord =
        new EventRecord(
            new Event()
                .withEventId(UUID.randomUUID())
                .withOrgId(ORG_ID)
                .withInstanceId(instanceId)
                .withEventId(UUID.randomUUID())
                .withRole(Event.Role.OSD)
                .withTimestamp(usageTimestamp)
                .withServiceType(SERVICE_TYPE)
                .withMeasurements(Collections.singletonList(measurement))
                .withBillingProvider(Event.BillingProvider.RED_HAT)
                .withBillingAccountId(Optional.of("sellerAcct")));
    // Truncate to MICROS because that's what the DB precision will be when
    // pulling dates from persisted Host records for comparison. Otherwise,
    // the extra precision points added to OffsetDateTime will cause the test
    // to fail.
    eventRecord.setRecordDate(clock.now().truncatedTo(ChronoUnit.MICROS));
    return eventRecord;
  }

  private Measurement createMeasurement(Double value) {
    return new Measurement().withUom(MetricIdUtils.getCores().toString()).withValue(value);
  }

  private void assertSnapshot(TallySnapshot snap, Double expectedValue) {
    assertEquals(
        expectedValue,
        snap.getMeasurement(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
  }

  /** A retry listener that tracks whether the RetryTemplate had failed during testing. */
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
