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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ResolvedEventMapperTest {

  private final ResolvedEventMapper mapper = Mappers.getMapper(ResolvedEventMapper.class);

  @Test
  void testUpdateIgnoresSpecificFields() {
    Event dest = new Event();
    Event source = createFullEvent();

    mapper.update(dest, source);

    // These fields should be ignored in update()
    assertNull(dest.getEventId());
    assertNull(dest.getRecordDate());
    assertNull(dest.getMeasurements());

    // Other fields should be copied
    assertEquals(source.getEventSource(), dest.getEventSource());
    assertEquals(source.getEventType(), dest.getEventType());
    assertEquals(source.getInstanceId(), dest.getInstanceId());
    assertEquals(source.getTimestamp(), dest.getTimestamp());
    assertEquals(source.getAzureResourceId(), dest.getAzureResourceId());
    assertEquals(source.getAzureTenantId(), dest.getAzureTenantId());
  }

  @Test
  void testCopyIgnoresSpecificFieldsButPreservesRecordDate() {
    Event dest = new Event();
    Event source = createFullEvent();

    mapper.copy(dest, source);

    // These fields should be ignored in copy()
    assertNull(dest.getEventId());
    assertNull(dest.getMeasurements());
    assertNull(dest.getProductTag());

    // RecordDate should now be preserved (this is the bug fix)
    assertEquals(source.getRecordDate(), dest.getRecordDate());

    // Other fields should be copied
    assertEquals(source.getEventSource(), dest.getEventSource());
    assertEquals(source.getEventType(), dest.getEventType());
    assertEquals(source.getInstanceId(), dest.getInstanceId());
    assertEquals(source.getTimestamp(), dest.getTimestamp());
    assertEquals(source.getAzureResourceId(), dest.getAzureResourceId());
    assertEquals(source.getAzureTenantId(), dest.getAzureTenantId());
  }

  @Test
  void testCopyPreservesRecordDateFromDatabaseEvent() {
    // Simulate a database-retrieved event with recordDate
    Event databaseEvent = createFullEvent();
    OffsetDateTime recordDate = OffsetDateTime.now().minusHours(1);
    databaseEvent.setRecordDate(recordDate);

    Event flattenedEvent = new Event();
    mapper.copy(flattenedEvent, databaseEvent);

    // Critical: recordDate should be preserved during flattening
    assertNotNull(flattenedEvent.getRecordDate());
    assertEquals(recordDate, flattenedEvent.getRecordDate());
  }

  @Test
  void testCopyHandlesNullRecordDate() {
    Event source = createFullEvent();
    source.setRecordDate(null);

    Event dest = new Event();
    mapper.copy(dest, source);

    // Should handle null recordDate gracefully
    assertNull(dest.getRecordDate());
  }

  @Test
  void testUpdateStillIgnoresRecordDate() {
    // Verify that update() still ignores recordDate (only copy() was fixed)
    Event dest = new Event();
    Event source = createFullEvent();
    OffsetDateTime recordDate = OffsetDateTime.now().minusHours(2);
    source.setRecordDate(recordDate);

    mapper.update(dest, source);

    // update() should still ignore recordDate
    assertNull(dest.getRecordDate());
  }

  @Test
  void testCopyWithComplexEventData() {
    Event source = createFullEvent();
    OffsetDateTime recordDate = OffsetDateTime.now().minusMinutes(30);
    source.setRecordDate(recordDate);
    source.setProductTag(Set.of("RHEL", "OpenShift"));
    source.setMeasurements(
        List.of(
            new Measurement().withMetricId("cores").withValue(8.0),
            new Measurement().withMetricId("instance-hours").withValue(24.0)));

    Event dest = new Event();
    mapper.copy(dest, source);

    // Verify ignored fields
    assertNull(dest.getEventId());
    assertNull(dest.getMeasurements());
    assertNull(dest.getProductTag());

    // Verify preserved recordDate
    assertEquals(recordDate, dest.getRecordDate());

    // Verify other copied fields
    assertEquals(source.getEventSource(), dest.getEventSource());
    assertEquals(source.getInstanceId(), dest.getInstanceId());
  }

  private Event createFullEvent() {
    return new Event()
        .withEventId(UUID.randomUUID())
        .withRecordDate(OffsetDateTime.now())
        .withMeasurements(List.of(new Measurement().withMetricId("cores").withValue(12.0)))
        .withProductTag(Set.of("TestTag"))
        .withEventSource("Event Source")
        .withEventType("Event Type")
        .withAzureResourceId(Optional.of("Azure Resource Id"))
        .withAzureTenantId(Optional.of("Azure Tenant ID"))
        .withInstanceId("Instance ID")
        .withTimestamp(OffsetDateTime.now())
        .withOrgId("test-org-123");
  }
}
