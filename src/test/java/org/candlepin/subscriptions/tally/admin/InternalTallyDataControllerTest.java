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
package org.candlepin.subscriptions.tally.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.tally.AccountResetService;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.billing.BillableUsageController;
import org.candlepin.subscriptions.tally.billing.ContractsController;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class InternalTallyDataControllerTest {
  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();
  private static final String ORG_ID = "org1";

  @MockBean ContractsController contractsController;
  @MockBean BillableUsageController billableUsageController;
  @MockBean AccountResetService accountResetService;
  @MockBean TallySnapshotController snapshotController;
  @MockBean CaptureSnapshotsTaskManager tasks;
  @MockBean EventRecordRepository eventRepo;
  @Autowired EventController eventController;
  @Autowired InternalTallyDataController controller;
  @Autowired ObjectMapper mapper;

  @Test
  void testDeleteDataAssociatedWithOrg() {
    controller.deleteDataAssociatedWithOrg(ORG_ID);
    verify(contractsController).deleteContractsWithOrg(ORG_ID);
    verify(billableUsageController).deleteRemittancesWithOrg(ORG_ID);
    verify(accountResetService).deleteDataForOrg(ORG_ID);
  }

  @Test
  void testTallyOrg() {
    controller.tallyOrg(ORG_ID);
    verify(tasks).updateOrgSnapshots(ORG_ID);
  }

  @Test
  void testTallyOrgSync() {
    controller.tallyOrgSync(ORG_ID);
    verify(snapshotController).produceSnapshotsForOrg(ORG_ID);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testEventWithNullInstanceIdIsSkipped() throws JsonProcessingException {
    Event event =
        new Event()
            .withEventType("test-event")
            .withOrgId("org123")
            .withEventSource("TEST_SOURCE")
            .withTimestamp(CLOCK.now())
            .withMeasurements(List.of(new Measurement().withMetricId("Cores").withValue(1.0)));

    List<Event> events = List.of(event);
    String json = mapper.writeValueAsString(events);
    ArgumentCaptor<List<EventRecord>> eventRecordCaptor = ArgumentCaptor.forClass(List.class);
    assertEquals("Events saved", controller.saveEvents(json));

    verify(eventRepo).saveAll(eventRecordCaptor.capture());
    assertTrue(eventRecordCaptor.getValue().isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testEventsWithNegativeMeasurementSkipped() throws JsonProcessingException {
    Event event =
        new Event()
            .withEventType("test-event")
            .withOrgId("org123")
            .withEventSource("TEST_SOURCE")
            .withInstanceId("1234")
            .withTimestamp(CLOCK.now())
            .withMeasurements(List.of(new Measurement().withMetricId("Cores").withValue(-1.0)));

    List<Event> events = List.of(event);
    String json = mapper.writeValueAsString(events);
    ArgumentCaptor<List<EventRecord>> eventRecordCaptor = ArgumentCaptor.forClass(List.class);
    assertEquals("Events saved", controller.saveEvents(json));

    verify(eventRepo).saveAll(eventRecordCaptor.capture());
    assertTrue(eventRecordCaptor.getValue().isEmpty());
  }

  @Test
  @SuppressWarnings("unchecked")
  void testOnlyValidEventIsPersisted() throws JsonProcessingException {
    Event event =
        new Event()
            .withConversion(true)
            .withProductIds(List.of("204"))
            .withEventType("test-event")
            .withOrgId("org123")
            .withEventSource("TEST_SOURCE")
            .withInstanceId("1234")
            .withTimestamp(CLOCK.now())
            .withMeasurements(List.of(new Measurement().withMetricId("Cores").withValue(1.0)));

    Event invalidEvent =
        new Event()
            .withEventType("test-event")
            .withOrgId("org123")
            .withEventSource("TEST_SOURCE")
            .withInstanceId("1234")
            .withTimestamp(CLOCK.now())
            .withMeasurements(List.of(new Measurement().withMetricId("Cores").withValue(-1.0)));

    List<Event> events = List.of(event, invalidEvent);

    ArgumentCaptor<List<EventRecord>> eventRecordCaptor = ArgumentCaptor.forClass(List.class);
    String json = mapper.writeValueAsString(events);
    assertEquals("Events saved", controller.saveEvents(json));

    verify(eventRepo).saveAll(eventRecordCaptor.capture());
    List<EventRecord> savedEvents = eventRecordCaptor.getValue();
    assertEquals(1, savedEvents.size());
    assertEquals(new EventRecord(event), savedEvents.get(0));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSavedEventsHavingUomAreNormalizedToMetricId() throws JsonProcessingException {
    Event event =
        new Event()
            .withConversion(true)
            .withProductIds(List.of("204"))
            .withEventType("test-event")
            .withOrgId("org123")
            .withEventSource("TEST_SOURCE")
            .withInstanceId("1234")
            .withTimestamp(CLOCK.now())
            .withMeasurements(List.of(new Measurement().withUom("Cores").withValue(1.0)));

    ArgumentCaptor<List<EventRecord>> eventRecordCaptor = ArgumentCaptor.forClass(List.class);
    String json = mapper.writeValueAsString(List.of(event));
    assertEquals("Events saved", controller.saveEvents(json));

    verify(eventRepo).saveAll(eventRecordCaptor.capture());
    List<EventRecord> savedEvents = eventRecordCaptor.getValue();
    assertEquals(1, savedEvents.size());
    var actual = savedEvents.get(0);
    assertEquals(1, actual.getEvent().getMeasurements().size());
    var actualMeasurement = actual.getEvent().getMeasurements().get(0);
    assertNull(actualMeasurement.getUom());
    assertEquals("Cores", actualMeasurement.getMetricId());
  }
}
