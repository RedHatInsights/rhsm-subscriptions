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

import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class ResolvedEventMapperTest {

  private final EventMapper mapper = Mappers.getMapper(EventMapper.class);

  @Test
  void testMapperIgnoresFieldsWeDoNotWantToCopyFields() {
    Event dest = new Event();
    Event target = createEvent();
    mapper.toUnpersistedWithoutMeasurements(dest, target);

    assertNull(dest.getEventId());
    assertNull(dest.getRecordDate());
    assertNull(dest.getMeasurements());
  }

  private Event createEvent() {
    return new Event()
        .withEventId(UUID.randomUUID())
        .withRecordDate(OffsetDateTime.now())
        .withMeasurements(List.of(new Measurement().withUom("cores").withValue(12.0)))
        .withEventSource("Event Source")
        .withEventType("Event Type")
        .withAzureResourceId(Optional.of("Azure Resource Id"))
        .withAzureTenantId(Optional.of("Azure Tennant ID"))
        .withInstanceId("Instance ID")
        .withTimestamp(OffsetDateTime.now());
  }
}
