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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;

class EventNormalizerTest {

  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();
  private static final EventNormalizer normalizer =
      new EventNormalizer(Mappers.getMapper(EventMapper.class));

  static Stream<Arguments> tagNormalizationArgs() {
    return Stream.of(
        Arguments.of(
            createEvent("Instance1", CLOCK.now(), Set.of("T1"), Map.of("cores", 4.0)),
            List.of(createEvent("Instance1", CLOCK.now(), Set.of("T1"), Map.of("cores", 4.0)))),
        Arguments.of(
            createEvent(
                "Instance1",
                CLOCK.now(),
                Set.of("T1"),
                Map.of("cores", 4.0, "instance-hours", 10.0)),
            List.of(
                createEvent("Instance1", CLOCK.now(), Set.of("T1"), Map.of("cores", 4.0)),
                createEvent(
                    "Instance1", CLOCK.now(), Set.of("T1"), Map.of("instance-hours", 10.0)))),
        Arguments.of(
            createEvent("Instance1", CLOCK.now(), Set.of("T1", "T2"), Map.of("cores", 4.0)),
            List.of(
                createEvent("Instance1", CLOCK.now(), Set.of("T1"), Map.of("cores", 4.0)),
                createEvent("Instance1", CLOCK.now(), Set.of("T2"), Map.of("cores", 4.0)))));
  }

  @ParameterizedTest
  @MethodSource("tagNormalizationArgs")
  void testNormalizeByTags(Event incoming, List<Event> expectedNormalizedEvents) {
    List<Event> normalized = normalizer.normalize(incoming);
    assertEvents(expectedNormalizedEvents, normalized);
  }

  private static Event createEvent(
      String instanceId, OffsetDateTime timestamp, Set<String> tags, Map<String, Double> metrics) {

    List<Measurement> measurements =
        metrics.entrySet().stream()
            .map(
                e ->
                    new Measurement()
                        .withMetricId(e.getKey())
                        .withUom(e.getKey())
                        .withValue(e.getValue()))
            .toList();

    return new Event()
        .withOrgId("org1")
        .withEventType("test_event_type")
        .withEventSource("test_source")
        .withServiceType("test_service_type")
        .withInstanceId(instanceId)
        .withProductTag(tags)
        .withMeasurements(measurements)
        .withTimestamp(timestamp);
  }

  private void assertEvents(List<Event> expected, List<Event> actual) {
    // Because we have no control over the generated Event.equals and Lists must be in the same
    // order,
    // we need to sort the internal collections of events in order to make equals work correctly
    // when comparing results.
    sortEventCollections(expected);
    sortEventCollections(actual);

    assertEquals(expected.size(), actual.size());
    for (Event e : expected) {
      assertTrue(actual.contains(e));
    }
  }

  private void sortEventCollections(List<Event> events) {
    events.forEach(
        event ->
            event.setMeasurements(
                event.getMeasurements().stream()
                    .sorted(Comparator.comparing(Measurement::getMetricId))
                    .toList()));
  }
}
