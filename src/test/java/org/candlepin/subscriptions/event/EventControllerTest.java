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

import static org.candlepin.subscriptions.event.EventController.INGESTED_USAGE_METRIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.configuration.registry.MetricId;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.security.OptInController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class EventControllerTest {
  @Mock EntityManager mockEntityManager;
  @Mock Query mockQuery;
  @Autowired ObjectMapper mapper;
  @MockitoBean private EventRecordRepository eventRecordRepository;
  @MockitoBean private OptInController optInController;
  @Captor private ArgumentCaptor<Collection<EventRecord>> eventsSaved;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private EventController eventController;

  String eventRecord1;
  String eventRecord2;
  String eventRecord3;
  String eventRecord4;
  String eventRecord5;
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
                       "metric_id": "Instance-hours",
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
                       "metric_id": "Instance-hours",
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
                       "metric_id": "Instance-hours",
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
                       "metric_id": "Instance-hours",
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
                       "metric_id": "Instance-hours",
                       "value": 1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
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
                       "metric_id": "Instance-hours",
                       "value": -1
                     }
                   ],
                   "service_type": "OpenShift Cluster"
                 }
        """;
    when(eventRecordRepository.getEntityManager()).thenReturn(mockEntityManager);
    when(mockEntityManager.createNativeQuery(anyString(), eq(EventRecord.class)))
        .thenReturn(mockQuery);
    when(mockQuery.setParameter(anyString(), any())).thenReturn(mockQuery);
    when(mockQuery.getResultList()).thenReturn(List.of());
  }

  @Test
  void testPersistServiceInstancesWhenValidPayload() {
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
  }

  @Test
  void testPersistServiceInstancesProcessValidPayloadAndSkipInvalidPayload() {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord3);
    eventRecords.add(eventRecord4);
    eventRecords.add(eventRecord5);
    assertThrows(
        BatchListenerFailedException.class,
        () -> eventController.persistServiceInstances(eventRecords));
    verify(optInController, times(2)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());
    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(2, events.size());
  }

  @Test
  void testPersistServiceInstancesSkipBadEventJson() {
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
  void testPersistServiceInstancesSkipEventsWithNegativeMeasurements() throws Exception {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecordNegativeMeasurement);
    EventRecord expectedEvent = new EventRecord(mapper.readValue(eventRecord1, Event.class));

    eventController.persistServiceInstances(eventRecords);

    verify(optInController, times(1)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());
    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(1, events.size());
    assertEquals(expectedEvent, events.get(0));
  }

  @Test
  void testPersistServiceInstancesSuccessfullyRetryFailedEventSave() throws Exception {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord5);

    EventRecord record1 = new EventRecord(mapper.readValue(eventRecord1, Event.class));
    EventRecord record2 = new EventRecord(mapper.readValue(eventRecord2, Event.class));
    EventRecord record5 = new EventRecord(mapper.readValue(eventRecord5, Event.class));

    List<EventRecord> failedEventList = List.of(record1, record2, record5);
    List<EventRecord> event1List = List.of(record1);
    List<EventRecord> event2List = List.of(record2);
    List<EventRecord> event5List = List.of(record5);

    when(eventRecordRepository.saveAll(failedEventList)).thenThrow(new RuntimeException());
    when(eventRecordRepository.saveAll(event1List)).thenReturn(event1List);
    when(eventRecordRepository.saveAll(event2List)).thenReturn(event2List);
    when(eventRecordRepository.saveAll(event5List)).thenReturn(event5List);

    // Error is caught and retry saving events individually.
    eventController.persistServiceInstances(eventRecords);

    // Since saveAll threw an Error we should try saving all records individually
    verify(eventRecordRepository, times(4)).saveAll(any());
    verify(eventRecordRepository).saveAll(failedEventList);
    verify(eventRecordRepository).saveAll(event1List);
    verify(eventRecordRepository).saveAll(event2List);
    verify(eventRecordRepository).saveAll(event5List);
  }

  @Test
  void testPersistServiceInstancesRetryFailedEventsSavesUntilError() throws Exception {
    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord5);

    EventRecord record1 = new EventRecord(mapper.readValue(eventRecord1, Event.class));
    EventRecord record2 = new EventRecord(mapper.readValue(eventRecord2, Event.class));
    EventRecord record5 = new EventRecord(mapper.readValue(eventRecord5, Event.class));

    List<EventRecord> failedEventList = List.of(record1, record2, record5);
    List<EventRecord> event1List = List.of(record1);
    List<EventRecord> event2List = List.of(record2);
    List<EventRecord> event5List = List.of(record5);

    when(eventRecordRepository.saveAll(failedEventList)).thenThrow(new RuntimeException());
    when(eventRecordRepository.saveAll(event1List)).thenReturn(event1List);
    // Throw an exception on the second record we try to save
    when(eventRecordRepository.saveAll(event2List)).thenThrow(new RuntimeException());

    // First is caught and retry saving events individually. Second exception is raised as
    // BatchListenerFailedException
    BatchListenerFailedException exception =
        assertThrows(
            BatchListenerFailedException.class,
            () -> eventController.persistServiceInstances(eventRecords));

    // Index should be 1 since we want to retry failed second event in this case
    assertEquals(1, exception.getIndex());

    // Last event is never attempted to save since second event fails
    verify(eventRecordRepository, times(3)).saveAll(any());
    verify(eventRecordRepository).saveAll(failedEventList);
    verify(eventRecordRepository).saveAll(event1List);
    verify(eventRecordRepository).saveAll(event2List);
    verify(eventRecordRepository, never()).saveAll(event5List);
  }

  @Test
  void testPersistServiceInstancesAzureBillingAccountIdSet() {
    List<String> eventRecords = new ArrayList<>();

    var azureEventRecord1 =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "7",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "cost-management",
                   "measurements": [
                     {
                       "metric_id": "Instance-hours",
                       "value": 1.0
                     }
                   ],
                   "service_type": "RHEL System",
                   "billing_provider": "azure",
                   "azure_subscription_id": "TestAzureSubscriptionId"
                }
        """;
    eventRecords.add(azureEventRecord1);
    eventController.persistServiceInstances(eventRecords);
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(1, events.size());
    assertEquals("TestAzureSubscriptionId", events.get(0).getEvent().getBillingAccountId().get());
  }

  @Test
  void testPersistServiceInstancesWhenProductTagExists() {
    List<String> eventRecords = new ArrayList<>();

    var invalidProductTagEventRecord1 =
        """
                    {
                     "sla":"Premium",
                     "org_id":"111111111",
                     "timestamp":"2024-06-10T10:00:00Z",
                     "conversion":false,
                     "event_type":"snapshot_rhel-for-x86-els-payg-addon_vCPUs",
                     "expiration":"2024-06-10T11:00:00Z",
                     "instance_id":"d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "product_tag":[
                        "dummy"
                     ],
                     "display_name":"automation__cluster_d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "event_source":"Premium",
                     "measurements":[
                        {
                           "value":4.0,
                           "metric_id":"vCPUs"
                        }
                     ],
                     "service_type":"RHEL System"
                  }
            """;

    var validProductTagEventRecord1 =
        """
                    {
                     "sla":"Premium",
                     "org_id":"111111111",
                     "timestamp":"2024-06-10T10:00:00Z",
                     "conversion":false,
                     "event_type":"snapshot_rhel-for-x86-els-payg-addon_vCPUs",
                     "expiration":"2024-06-10T11:00:00Z",
                     "instance_id":"d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "product_tag":[
                        "rhel-for-x86-els-payg-addon"
                     ],
                     "display_name":"automation__cluster_d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "event_source":"Premium",
                     "measurements":[
                        {
                           "value":4.0,
                           "metric_id":"vCPUs"
                        }
                     ],
                     "service_type":"RHEL System"
                  }
            """;
    eventRecords.add(validProductTagEventRecord1);
    eventRecords.add(invalidProductTagEventRecord1);
    eventController.persistServiceInstances(eventRecords);
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    List<EventRecord> events = eventsSaved.getAllValues().get(0).stream().toList();
    assertEquals(1, events.size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "prometheus",
        "rhelmeter",
        "urn:redhat:source:console:app:aap-controller-billing",
        "cost-management"
      })
  void testMeterRegistryCounter(String eventSource) {
    var validProductTagEventRecord1 =
        """
                    {
                     "sla":"Premium",
                     "org_id":"111111111",
                     "timestamp":"2024-06-10T10:00:00Z",
                     "conversion":false,
                     "event_type":"snapshot_rhel-for-x86-els-payg-addon_vCPUs",
                     "expiration":"2024-06-10T11:00:00Z",
                     "instance_id":"d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "product_tag":[
                        "rhel-for-x86-els-payg-addon"
                     ],
                     "display_name":"automation__cluster_d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "event_source":"%s",
                     "measurements":[
                        {
                           "value":4.0,
                           "metric_id":"vCPUs"
                        }
                     ],
                     "service_type":"RHEL System"
                  }
            """
            .formatted(eventSource);
    when(eventRecordRepository.saveAll(any()))
        .thenReturn(
            List.of(
                new EventRecord(
                    new Event()
                        .withEventId(UUID.randomUUID())
                        .withProductTag(Set.of("rhel-for-x86-els-payg-addon"))
                        .withMeasurements(
                            List.of(new Measurement().withMetricId("vCPUs").withValue(4.0)))
                        .withBillingProvider(Event.BillingProvider.AZURE)
                        .withEventSource(eventSource))));
    eventController.persistServiceInstances(List.of(validProductTagEventRecord1));

    verify(eventRecordRepository).saveAll(eventsSaved.capture());
    var meter =
        getIngestedUsageMetric("rhel-for-x86-els-payg-addon", "vCPUs", "azure", eventSource);
    assertTrue(meter.isPresent());
    assertEquals(4.0, meter.get().measure().iterator().next().getValue());
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

  @Test
  @SuppressWarnings("unchecked")
  void testPersistedEventHaveMetricIdNormalized() {
    List<String> eventRecords = new ArrayList<>();

    var azureEventRecord1 =
        """
                {
                   "sla": "Premium",
                   "role": "osd",
                   "org_id": "7",
                   "timestamp": "2023-05-02T00:00:00Z",
                   "event_type": "snapshot",
                   "expiration": "2023-05-02T01:00:00Z",
                   "instance_id": "e3a62bd1-fd00-405c-9401-f2288808588d",
                   "display_name": "automation_osd_cluster_e3a62bd1-fd00-405c-9401-f2288808588d",
                   "event_source": "cost-management",
                   "measurements": [
                     {
                       "metric_id": "Instance-hours",
                       "value": 1.0
                     }
                   ],
                   "service_type": "RHEL System",
                   "billing_provider": "azure",
                   "azure_subscription_id": "TestAzureSubscriptionId"
                }
        """;
    eventRecords.add(azureEventRecord1);
    eventController.persistServiceInstances(eventRecords);
    ArgumentCaptor<List<EventRecord>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventRecordRepository).saveAll(captor.capture());
    var records = captor.getAllValues().get(0);
    assertEquals(1, records.size());
    var eventRecord = records.get(0);
    assertEquals(1, eventRecord.getEvent().getMeasurements().size());
    var measurement = eventRecord.getEvent().getMeasurements().get(0);
    assertEquals("Instance-hours", measurement.getMetricId());
  }

  @Test
  void incomingHbiEventsAreFilteredByActiveOrgIdIfFromHbi() {
    String expectedActiveOrgId = "111111111";
    String expectedInactiveOrgId = "22222222";
    String expecedNonHbiOrgId = "33333333";

    var hbiEventWithActiveOrgId =
        """
                    {
                     "sla":"Premium",
                     "org_id":"%s",
                     "timestamp":"2024-06-10T10:00:00Z",
                     "conversion":false,
                     "event_type":"snapshot_rhel-for-x86-els-payg-addon_vCPUs",
                     "expiration":"2024-06-10T11:00:00Z",
                     "instance_id":"d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "product_tag":[
                        "rhel-for-x86-els-payg-addon"
                     ],
                     "display_name":"automation__cluster_d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "event_source":"Premium",
                     "measurements":[
                        {
                           "value":4.0,
                           "metric_id":"vCPUs"
                        }
                     ],
                     "service_type":"HBI_HOST"
                  }
            """
            .formatted(expectedActiveOrgId);

    var hbiEventWithInactiveOrgId =
        """
                    {
                     "sla":"Premium",
                     "org_id":"%s",
                     "timestamp":"2024-06-10T10:00:00Z",
                     "conversion":false,
                     "event_type":"snapshot_rhel-for-x86-els-payg-addon_vCPUs",
                     "expiration":"2024-06-10T11:00:00Z",
                     "instance_id":"d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "product_tag":[
                        "rhel-for-x86-els-payg-addon"
                     ],
                     "display_name":"automation__cluster_d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "event_source":"Premium",
                     "measurements":[
                        {
                           "value":4.0,
                           "metric_id":"vCPUs"
                        }
                     ],
                     "service_type":"HBI_HOST"
                  }
            """
            .formatted(expectedInactiveOrgId);

    var nonHbiEvent =
        """
                    {
                     "sla":"Premium",
                     "org_id":"%s",
                     "timestamp":"2024-06-10T10:00:00Z",
                     "conversion":false,
                     "event_type":"snapshot_rhel",
                     "expiration":"2024-06-10T11:00:00Z",
                     "instance_id":"d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "product_tag":[
                        "rhel-for-x86-els-payg-addon"
                     ],
                     "display_name":"automation__cluster_d147ddf2-be4a-4a59-acf7-7f222758b47c",
                     "event_source":"Premium",
                     "measurements":[
                        {
                           "value":4.0,
                           "metric_id":"vCPUs"
                        }
                     ],
                     "service_type":"RHEL Server"
                  }
            """
            .formatted(expecedNonHbiOrgId);

    List<String> eventRecords = new ArrayList<>();
    eventRecords.add(hbiEventWithActiveOrgId);
    eventRecords.add(hbiEventWithInactiveOrgId);
    eventRecords.add(nonHbiEvent);

    when(optInController.isOptedIn(expectedActiveOrgId)).thenReturn(true);
    when(optInController.isOptedIn(expectedInactiveOrgId)).thenReturn(false);

    eventController.persistServiceInstances(eventRecords);

    ArgumentCaptor<List<EventRecord>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventRecordRepository).saveAll(captor.capture());
    var records = captor.getAllValues().get(0);
    assertEquals(2, records.size());
    assertTrue(records.stream().anyMatch(r -> expectedActiveOrgId.equals(r.getOrgId())));
    assertTrue(records.stream().anyMatch(r -> expecedNonHbiOrgId.equals(r.getOrgId())));

    // Make sure that opt-in wasn't tried for HBI events.
    verify(optInController, times(1)).isOptedIn(expectedActiveOrgId);
    verify(optInController, never()).optInByOrgId(expectedActiveOrgId, OptInType.PROMETHEUS);
    verify(optInController, times(1)).isOptedIn(expectedInactiveOrgId);
    verify(optInController, never()).optInByOrgId(expectedInactiveOrgId, OptInType.PROMETHEUS);

    // Opt-in should have been attempted for non-HBI host.
    verify(optInController, never()).isOptedIn(expecedNonHbiOrgId);
    verify(optInController, times(1)).optInByOrgId(expecedNonHbiOrgId, OptInType.PROMETHEUS);
  }

  private Optional<Meter> getIngestedUsageMetric(
      String productTag, String metricId, String billingProvider, String eventSource) {
    return meterRegistry.getMeters().stream()
        .filter(
            m ->
                INGESTED_USAGE_METRIC.equals(m.getId().getName())
                    && productTag.equals(m.getId().getTag("product"))
                    && MetricId.fromString(metricId)
                        .getValue()
                        .equals(m.getId().getTag("metric_id"))
                    && billingProvider.equals(m.getId().getTag("billing_provider"))
                    && eventSource.equals(m.getId().getTag("event_source")))
        .findFirst();
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
