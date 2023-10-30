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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;

class EventKeyTest {

  private ApplicationClock clock = new TestClockConfiguration().adjustableClock();

  @Test
  void testLookup() {
    Event e1 = eventForInstanceId("instance1");
    Event e2 = eventForInstanceId("instance2");
    Event e3 = eventForInstanceId("instance3");

    Map<EventKey, Event> eventMap =
        Stream.of(e1, e2, e3).collect(Collectors.toMap(EventKey::fromEvent, Function.identity()));

    assertEquals(e1, eventMap.get(keyForInstanceId("instance1")));
    assertEquals(e2, eventMap.get(keyForInstanceId("instance2")));
    assertEquals(e3, eventMap.get(keyForInstanceId("instance3")));
  }

  @Test
  void testEquality() {
    EventKey ek1 = new EventKey("org", "source", "type", "instance", clock.now());
    EventKey ek2 = new EventKey("org", "source", "type", "instance", clock.now());
    EventKey ek3 = new EventKey("org3", "source3", "type3", "instance3", clock.now().minusDays(1));

    assertEquals(ek1, ek2);
    assertNotEquals(ek1, ek3);
  }

  private EventKey keyForInstanceId(String instanceId) {
    return new EventKey("org", "source", "type", instanceId, clock.now());
  }

  private Event eventForInstanceId(String instanceId) {
    return (Event)
        new Event()
            .withTimestamp(clock.now())
            .withOrgId("org")
            .withEventType("type")
            .withEventSource("source")
            .withInstanceId(instanceId);
  }
}
