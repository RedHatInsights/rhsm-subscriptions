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
package org.candlepin.subscriptions.db.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;

class EventKeyTest {

  @Test
  void testLookup() {
    OffsetDateTime now = OffsetDateTime.now();

    Event e1 = eventForInstanceId("instance1", now);
    Event e2 = eventForInstanceId("instance2", now);
    Event e3 = eventForInstanceId("instance3", now);

    Map<EventKey, Event> eventMap =
        Stream.of(e1, e2, e3).collect(Collectors.toMap(EventKey::fromEvent, Function.identity()));

    assertEquals(e1, eventMap.get(keyForInstanceId("instance1", now)));
    assertEquals(e2, eventMap.get(keyForInstanceId("instance2", now)));
    assertEquals(e3, eventMap.get(keyForInstanceId("instance3", now)));
  }

  @Test
  void testEquality() {
    OffsetDateTime now = OffsetDateTime.now();
    EventKey ek1 = new EventKey("org", "source", "type", "instance", now);
    EventKey ek2 = new EventKey("org", "source", "type", "instance", now);
    EventKey ek3 = new EventKey("org3", "source3", "type3", "instance3", now.minusDays(1));

    assertEquals(ek1, ek2);
    assertNotEquals(ek1, ek3);
  }

  private EventKey keyForInstanceId(String instanceId, OffsetDateTime timestamp) {
    return new EventKey("org", "source", "type", instanceId, timestamp);
  }

  private Event eventForInstanceId(String instanceId, OffsetDateTime timestamp) {
    return new Event()
        .withTimestamp(timestamp)
        .withOrgId("org")
        .withEventType("type")
        .withEventSource("source")
        .withInstanceId(instanceId);
  }
}
