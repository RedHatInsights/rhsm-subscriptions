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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.configuration.FeatureFlags;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.TallyStateRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Unit tests for the DISABLE_HOURLY_HOST_UPDATE feature flag in TallySnapshotController.
 *
 * <p>Verifies that when 'swatch.swatch-tally.disable-hourly-host-update' is enabled (true), the
 * metricUsageCollector.updateHosts() method is NOT called, while calculateUsage() continues to be
 * called normally.
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles({"worker", "test"})
class TallySnapshotControllerDisableHostUpdateTest implements ExtendWithEmbeddedKafka {

  private static final String ORG_ID = "test-org";
  private static final String SERVICE_TYPE = "OpenShift Cluster";

  @Mock EntityManager mockEntityManager;
  @MockitoBean EventRecordRepository eventRepo;
  @MockitoBean TallyStateRepository tallyStateRepo;
  @MockitoBean FeatureFlags featureFlags;
  @Autowired RetryTemplate collectorRetryTemplate;
  @MockitoSpyBean ApplicationClock clock;
  @MockitoBean TallySnapshotRepository snapshotRepo;
  @MockitoSpyBean MetricUsageCollector metricUsageCollector;
  @Autowired TallySnapshotController controller;

  private int numEventsCreated = 0;

  @BeforeEach
  void setupTest() {
    numEventsCreated = 0;
    when(eventRepo.getEntityManager()).thenReturn(mockEntityManager);
    when(tallyStateRepo.save(any())).thenAnswer(a -> a.getArgument(0));
    when(snapshotRepo.save(any())).thenAnswer(a -> a.getArgument(0));
    when(snapshotRepo.findByOrgIdAndProductIdInAndGranularityAndSnapshotDateBetween(
            any(), any(), any(), any(), any()))
        .thenReturn(Stream.empty());

    Mockito.doReturn(OffsetDateTime.of(2022, 1, 10, 15, 0, 0, 0, ZoneOffset.UTC)).when(clock).now();
  }

  @Test
  void whenDisableFlagIsFalse_thenUpdateHostsIsCalled() {
    // Given: DISABLE flag is false, meaning host updates should happen
    when(featureFlags.isEnabled(FeatureFlags.DISABLE_HOURLY_HOST_UPDATE)).thenReturn(false);

    OffsetDateTime snapshotHour = clock.startOfCurrentHour();
    EventRecord event = createEvent(UUID.randomUUID().toString(), snapshotHour, 42.0);
    when(eventRepo.fetchOrderedEventStream(ORG_ID, SERVICE_TYPE, clock.startOfToday().minusDays(1)))
        .thenReturn(Stream.of(event));

    // When: We produce hourly snapshots
    controller.produceHourlySnapshotsForOrg(ORG_ID);

    // Then: updateHosts SHOULD be called
    verify(metricUsageCollector, times(1)).updateHosts(eq(ORG_ID), eq(SERVICE_TYPE), anyList());
    verify(metricUsageCollector, times(1)).calculateUsage(anyList(), any());
  }

  @Test
  void whenDisableFlagIsTrue_thenUpdateHostsIsNotCalled() {
    // Given: DISABLE flag is true, meaning host updates should NOT happen
    when(featureFlags.isEnabled(FeatureFlags.DISABLE_HOURLY_HOST_UPDATE)).thenReturn(true);

    OffsetDateTime snapshotHour = clock.startOfCurrentHour();
    EventRecord event = createEvent(UUID.randomUUID().toString(), snapshotHour, 42.0);
    when(eventRepo.fetchOrderedEventStream(ORG_ID, SERVICE_TYPE, clock.startOfToday().minusDays(1)))
        .thenReturn(Stream.of(event));

    // When: We produce hourly snapshots
    controller.produceHourlySnapshotsForOrg(ORG_ID);

    // Then: updateHosts should NOT be called
    verify(metricUsageCollector, never()).updateHosts(eq(ORG_ID), eq(SERVICE_TYPE), anyList());

    // But: calculateUsage should still be called
    verify(metricUsageCollector, times(1)).calculateUsage(anyList(), any());
  }

  @Test
  void whenDisableFlagIsTrue_withEvents_thenUpdateHostsIsNeverCalled() {
    // Given: DISABLE flag is true
    when(featureFlags.isEnabled(FeatureFlags.DISABLE_HOURLY_HOST_UPDATE)).thenReturn(true);

    OffsetDateTime hour1 = clock.startOfCurrentHour();
    EventRecord event1 = createEvent(UUID.randomUUID().toString(), hour1, 42.0);

    // Return events for our specific service type, empty for others
    when(eventRepo.fetchOrderedEventStream(eq(ORG_ID), eq(SERVICE_TYPE), any()))
        .thenReturn(Stream.of(event1));
    when(eventRepo.fetchOrderedEventStream(
            eq(ORG_ID), argThat(s -> !SERVICE_TYPE.equals(s)), any()))
        .thenReturn(Stream.empty());

    // When: We produce hourly snapshots
    controller.produceHourlySnapshotsForOrg(ORG_ID);

    // Then: updateHosts should NOT be called at all
    verify(metricUsageCollector, never()).updateHosts(any(), any(), anyList());

    // But: calculateUsage should still be called (for the events we returned)
    verify(metricUsageCollector, atLeastOnce()).calculateUsage(anyList(), any());
  }

  @Test
  void whenDisableFlagIsFalse_withEvents_thenUpdateHostsIsCalled() {
    // Given: DISABLE flag is false
    when(featureFlags.isEnabled(FeatureFlags.DISABLE_HOURLY_HOST_UPDATE)).thenReturn(false);

    OffsetDateTime hour1 = clock.startOfCurrentHour();
    EventRecord event1 = createEvent(UUID.randomUUID().toString(), hour1, 42.0);

    // Return events for our specific service type, empty for others
    when(eventRepo.fetchOrderedEventStream(eq(ORG_ID), eq(SERVICE_TYPE), any()))
        .thenReturn(Stream.of(event1));
    when(eventRepo.fetchOrderedEventStream(
            eq(ORG_ID), argThat(s -> !SERVICE_TYPE.equals(s)), any()))
        .thenReturn(Stream.empty());

    // When: We produce hourly snapshots
    controller.produceHourlySnapshotsForOrg(ORG_ID);

    // Then: updateHosts should be called (at least once, since we have events)
    verify(metricUsageCollector, atLeastOnce()).updateHosts(any(), any(), anyList());
    verify(metricUsageCollector, atLeastOnce()).calculateUsage(anyList(), any());
  }

  private EventRecord createEvent(String instanceId, OffsetDateTime usageTimestamp, Double value) {
    Measurement measurement =
        new Measurement().withMetricId(MetricIdUtils.getCores().toString()).withValue(value);

    return new EventRecord(
        new Event()
            .withEventId(UUID.randomUUID())
            .withOrgId(ORG_ID)
            .withInstanceId(instanceId)
            .withEventId(UUID.randomUUID())
            .withRole(Role.OSD)
            .withProductTag(Set.of("OpenShift-dedicated-metrics"))
            .withTimestamp(usageTimestamp)
            .withServiceType(SERVICE_TYPE)
            .withMeasurements(Collections.singletonList(measurement))
            .withBillingProvider(Event.BillingProvider.RED_HAT)
            .withBillingAccountId(Optional.of("sellerAcct"))
            .withRecordDate(
                clock.now().plusMinutes(numEventsCreated++).truncatedTo(ChronoUnit.MICROS)));
  }
}
