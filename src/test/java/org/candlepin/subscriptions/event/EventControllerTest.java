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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.security.OptInController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class EventControllerTest {
  @Autowired EventController eventController;

  @MockBean private EventRecordRepository eventRecordRepository;
  @MockBean private OptInController optInController;

  String eventRecord1;
  String eventRecord2;
  String eventRecord3;
  String eventRecord4;
  String eventRecord5;

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
  }

  @Test
  void testPersistServiceInstances_WhenValidPayload() {

    Set<String> eventRecords = new HashSet<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord3);
    eventController.persistServiceInstances(eventRecords);
    verify(optInController, times(2)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());

    ArgumentCaptor<Collection> eve = ArgumentCaptor.forClass(Collection.class);
    verify(eventRecordRepository).saveAll(eve.capture());
    List events = eve.getAllValues().get(0).stream().toList();
    assertEquals(2, events.size());
  }

  @Test
  void testPersistServiceInstances_ProcessValidPayloadAndSkipInvalidPayload() {

    Set<String> eventRecords = new HashSet<>();
    eventRecords.add(eventRecord1);
    eventRecords.add(eventRecord2);
    eventRecords.add(eventRecord3);
    eventRecords.add(eventRecord4);
    eventRecords.add(eventRecord5);
    eventController.persistServiceInstances(eventRecords);
    verify(optInController, times(3)).optInByOrgId(any(), any());
    when(eventRecordRepository.saveAll(any())).thenReturn(new ArrayList<>());
    ArgumentCaptor<Collection> eve = ArgumentCaptor.forClass(Collection.class);
    verify(eventRecordRepository).saveAll(eve.capture());
    List events = eve.getAllValues().get(0).stream().toList();
    assertEquals(3, events.size());
  }
}
