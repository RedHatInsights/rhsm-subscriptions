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
package org.candlepin.subscriptions.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.security.OptInController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class EventControllerTest {
  @Mock EntityManager mockEntityManager;
  @Autowired EventController eventController;
  @Autowired ObjectMapper mapper;
  @MockBean private EventRecordRepository eventRecordRepository;
  @MockBean private OptInController optInController;
  @Captor private ArgumentCaptor<Collection<EventRecord>> eventsSaved;

  String eventRecord1;
  String eventRecord2;
  String eventRecord3;
  String eventRecord4;
  String eventRecord5;
  String azureEventRecord1;
  String eventRecordNegativeMeasurement;

  @BeforeEach
  void setup() {
    eventRecord1 =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "4",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot_redhat.com:openshift_dedicated:cluster_hour",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "prometheus",
                   "measurements": [
                     {
                       "uom": "Instance-hours",
                       "value": 1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
                """;
    eventRecord2 =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "6",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot_redhat.com:openshift_dedicated:cluster_hour",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "prometheus",
                   "measurements": [
                     {
                       "uom": "Instance-hours",
                       "value": 1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
                """;
    eventRecord3 =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "6",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot_redhat.com:openshift_dedicated:cluster_hour",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "prometheus",
                   "measurements": [
                     {
                       "uom": "Instance-hours",
                       "value": 1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
                """;
    eventRecord4 =
        """
                {
                   "sla": "Premium1",
                   "role": "osd",
                   "org_id": "6",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot_redhat.com:openshift_dedicated:cluster_hour",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "prometheus",
                   "measurements": [
                     {
                       "uom": "Instance-hours",
                       "value": 1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
                """;
    eventRecord5 =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "7",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot_redhat.com:openshift_dedicated:cluster_hour",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "prometheus",
                   "measurements": [
                     {
                       "uom": "Instance-hours",
                       "value": 1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
                """;

    azureEventRecord1 =
        """
                {
                   "sla": "Premium",
                   "role": "Red Hat Enterprise Linux Server",
                   "org_id": "7",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "cost-management",
                   "measurements": [
                     {
                       "uom": "vCPUs",
                       "value": 1.0
                     }
                   ],
                   "service_type": "RHEL System",
                   "billing_provider": "azure",
                   "azure_tenant_id": "TestAzureTenantId",
                   "azure_subscription_id": "TestAzureSubscriptionId"
                }
        """;
    eventRecordNegativeMeasurement =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "8",
                   "timestamp": "2023-05-02T10:00:00Z",
                   "event_type": "snapshot_redhat.com:openshift_dedicated:cluster_hour",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "prometheus",
                   "measurements": [
                     {
                       "uom": "Instance-hours",
                       "value": -1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
        """;
    when(eventRecordRepository.getEntityManager()).thenReturn(mockEntityManager);
  }

  @Test
  void testPersistServiceInstances_WhenValidPayload() {

    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord3);
    eventController.persistServiceInstances(eventRecords);
    verify(optInController, times(2)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(2, events.size());
    verifyDeletionOfStaleEventsIsNotDone();
  }

  @Test
  void testPersistServiceInstances_ProcessValidPayloadAndSkipInvalidPayload() {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord3);
    eventRecords.add(eventRecord4);
    eventRecords.add(eventRecord5);
    BatchListenerFailedException exception =
        assertThrows(
            BatchListenerFailedException.class,
            () -> eventController.persistServiceInstances(eventRecords));
    verify(optInController, times(2)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());
    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(2, events.size());
    verifyDeletionOfStaleEventsIsNotDone();
  }

  @Test
  void testPersistServiceInstances_SkipBadEventJson() {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add("badData");
    eventRecords.add(eventRecord3);

    BatchListenerFailedException exception =
        assertThrows(
            BatchListenerFailedException.class,
            () -> eventController.persistServiceInstances(eventRecords));

    // Exception should be thrown at index 3, skipping the bad json record.
    assertEquals(3, exception.getIndex());

    verify(optInController, times(2)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    // Should save first 2 successful events.
    assertEquals(2, events.size());
  }

  @Test
  void testPersistServiceInstances_SkipEventsWithNegativeMeasurements() throws Exception {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecordNegativeMeasurement);
    EventRecord expectedEvent = new EventRecord(mapper.readValue(eventRecord1, Event.class));

    eventController.persistServiceInstances(eventRecords);

    verify(optInController, times(2)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());
    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(1, events.size());
    assertEquals(expectedEvent, events.get(0));
    verifyDeletionOfStaleEventsIsNotDone();
  }

  @Test
  void testPersistServiceInstances_SuccessfullyRetryFailedEventSave() {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord5);

    when(eventRecordRepository.saveAll(any())).thenThrow(new RuntimeException());
    when(eventRecordRepository.save(any())).thenReturn(new EventRecord());

    // Error is caught and retry saving events individually.
    eventController.persistServiceInstances(eventRecords);

    // Since saveAll threw an Error we should try saving all records individually
    verify(eventRecordRepository, times(3)).save(any());
  }

  @Test
  void testPersistServiceInstances_RetryFailedEventsSavesUntilError() {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord5);

    when(eventRecordRepository.saveAll(any())).thenThrow(new RuntimeException());
    when(eventRecordRepository.save(any()))
        .thenReturn(new EventRecord())
        // Throw an exception on the second record we try to save
        .thenThrow(new RuntimeException());

    // First is caught and retry saving events individually. Second exception is raised as
    // BatchListenerFailedException
    BatchListenerFailedException exception =
        assertThrows(
            BatchListenerFailedException.class,
            () -> eventController.persistServiceInstances(eventRecords));

    // Index should be 1 since we want to retry failed second event in this case
    assertEquals(1, exception.getIndex());
    // Last event is never attempted to save since second event fails
    verify(eventRecordRepository, times(2)).save(any());
  }

  @Test
  void testPersistServiceInstances_AzureBillingAccountIdSet() {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(azureEventRecord1);
    eventController.persistServiceInstances(eventRecords);
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(1, events.size());
    assertEquals(
        "TestAzureTenantId;TestAzureSubscriptionId",
        events.get(0).getEvent().getBillingAccountId().get());
  }

  @Test
  void testProcessEventsInBatches_processesAllEvents() {
    List<EventRecord> all = new LinkedList<>();
    for (int i = 0; i < 10; i++) {
      all.add(new EventRecord());
    }

    OffsetDateTime now = OffsetDateTime.now();
    when(eventRecordRepository.fetchOrderedEventStream("org123", "serviceType", now))
        .thenReturn(all.stream());

    final int batchSize = 3;
    BatchedEventCounter counter = new BatchedEventCounter();
    eventController.processEventsInBatches(
        "org123",
        "serviceType",
        now,
        batchSize,
        events -> {
          counter.increment(events.size());
        });

    List<Integer> finalBatchCount = counter.getCounts();
    assertEquals(4, finalBatchCount.size());
    // Verify the number of events in each batch.
    assertEquals(batchSize, finalBatchCount.get(0));
    assertEquals(batchSize, finalBatchCount.get(1));
    assertEquals(batchSize, finalBatchCount.get(2));
    assertEquals(1, finalBatchCount.get(3));
  }

  private void verifyDeletionOfStaleEventsIsDone() {
    verify(eventRecordRepository)
        .deleteStaleEvents(
            eq("7"),
            eq("prometheus"),
            eq("snapshot_redhat.com:openshift_dedicated:cluster_hour"),
            eq(UUID.fromString("e3a62bd1-fd00-405c-9401-f2288808588d")),
            any(),
            any());
  }

  private void verifyDeletionOfStaleEventsIsNotDone() {
    verify(eventRecordRepository, times(0))
        .deleteStaleEvents(any(), any(), any(), any(), any(), any());
  }

  private class BatchedEventCounter {
    private final List<Integer> counts = new LinkedList<>();

    void increment(int eventCount) {
      counts.add(eventCount);
    }

    List<Integer> getCounts() {
      return counts;
    }
  }
}
