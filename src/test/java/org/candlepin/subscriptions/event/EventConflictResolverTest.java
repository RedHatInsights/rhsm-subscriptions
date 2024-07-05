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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.AmendmentType;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventConflictResolverTest {
  private static final ApplicationClock CLOCK = new TestClockConfiguration().adjustableClock();
  private static final ObjectMapper MAPPER;

  static {
    MAPPER = new ObjectMapper();
    MAPPER.registerModule(new JavaTimeModule());
    MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
  }

  @Mock private EventRecordRepository repo;
  private EventConflictResolver resolver;

  @BeforeEach
  void setupTest() {
    this.resolver =
        new EventConflictResolver(repo, Mappers.getMapper(ResolvedEventMapper.class), CLOCK);
  }

  static Stream<Arguments> basicAmendmentsScenarios() {
    return Stream.of(
        // No event conflicts yields new result.
        Arguments.of(
            List.of(),
            List.of(event(Map.of("cores", 12.0))),
            List.of(event(Map.of("cores", 12.0)))),
        // Events with different timestamps do not conflict.
        Arguments.of(
            List.of(),
            List.of(
                event(Map.of("cores", 1.0)).withTimestamp(CLOCK.now()),
                event(Map.of("cores", 3.0)).withTimestamp(CLOCK.now().plusHours(2))),
            List.of(
                event(Map.of("cores", 1.0)).withTimestamp(CLOCK.now()),
                event(Map.of("cores", 3.0)).withTimestamp(CLOCK.now().plusHours(2)))),
        // Event conflict with identical measurement is deducted
        // TODO Make existing and incoming have different hardware type.
        Arguments.of(
            existing(event(Map.of("cores", 12.0))),
            List.of(event(Map.of("cores", 12.0))),
            List.of(
                deduction(Map.of("cores", -12.0)).withAmendmentType(AmendmentType.DEDUCTION),
                event(Map.of("cores", 12.0)))),
        // Duplicate incoming events results in a single deduction
        // TODO: Make existing and out different
        Arguments.of(
            List.of(),
            List.of(event(Map.of("cores", 1.0)), event(Map.of("cores", 1.0))),
            List.of(
                event(Map.of("cores", 1.0)),
                deduction(Map.of("cores", -1.0)).withAmendmentType(AmendmentType.DEDUCTION),
                event(Map.of("cores", 1.0)))),
        // Duplicate incoming events with incoming conflict is resolved.
        Arguments.of(
            List.of(),
            List.of(
                event(Map.of("cores", 1.0)),
                event(Map.of("cores", 1.0)),
                event(Map.of("cores", 5.0))),
            List.of(
                event(Map.of("cores", 1.0)),
                deduction(Map.of("cores", -1.0)),
                event(Map.of("cores", 1.0)),
                deduction(Map.of("cores", -1.0)),
                event(Map.of("cores", 5.0)))),
        // Incoming events with duplicate measurement resolves to single event.
        // Conflict with different measurement value, yields amendment plus incoming value.
        Arguments.of(
            existing(event(Map.of("cores", 5.0))),
            List.of(event(Map.of("cores", 15.0))),
            List.of(deduction(Map.of("cores", -5.0)), event(Map.of("cores", 15.0)))),
        // Event conflict with existing amendment resolves to additional amendment.
        Arguments.of(
            existing(
                event(Map.of("cores", 5.0)),
                deduction(Map.of("cores", -5.0)),
                event(Map.of("cores", 15.0))),
            List.of(event(Map.of("cores", 20.0))),
            List.of(deduction(Map.of("cores", -15.0)), event(Map.of("cores", 20.0)))),
        // Conflict with different measurement value yields amendment plus incoming value
        // for single instance only. Net new event for other instance.
        Arguments.of(
            // Instance 1
            existing(event(Map.of("cores", 5.0))),
            List.of(
                // Instance 1
                event(Map.of("cores", 15.0)),
                // Instance 2
                event(Map.of("cores", 5.0)).withInstanceId("instance_2")),
            List.of(
                deduction(Map.of("cores", -5.0)),
                event(Map.of("cores", 15.0)),
                event(Map.of("cores", 5.0)).withInstanceId("instance_2"))),
        // Conflict with different measurement value yields amendment plus incoming value
        // for both instance.
        Arguments.of(
            existing(
                event(Map.of("cores", 5.0)),
                event(Map.of("cores", 5.0)).withInstanceId("instance_2")),
            List.of(
                event(Map.of("cores", 15.0)),
                event(Map.of("cores", 10.0)).withInstanceId("instance_2")),
            List.of(
                deduction(Map.of("cores", -5.0)),
                event(Map.of("cores", 15.0)),
                deduction(Map.of("cores", -5.0)).withInstanceId("instance_2"),
                event(Map.of("cores", 10.0)).withInstanceId("instance_2"))),
        // Events with conflicts with multiple timestamps are resolved.
        Arguments.of(
            existing(
                event(Map.of("cores", 1.0)),
                event(Map.of("cores", 1.0)).withTimestamp(CLOCK.now().plusHours(2))),
            List.of(
                event(Map.of("cores", 2.0)),
                event(Map.of("cores", 4.0)).withTimestamp(CLOCK.now().plusHours(2))),
            List.of(
                deduction(Map.of("cores", -1.0)),
                event(Map.of("cores", 2.0)),
                deduction(Map.of("cores", -1.0)).withTimestamp(CLOCK.now().plusHours(2)),
                event(Map.of("cores", 4.0)).withTimestamp(CLOCK.now().plusHours(2)))));
  }

  @ParameterizedTest
  @MethodSource("basicAmendmentsScenarios")
  void testBasicAmendmentsScenario(
      List<EventArgument> expectedExisting,
      List<EventArgument> expectedIncoming,
      List<EventArgument> expectedResolved) {
    testResolutionScenario(expectedExisting, expectedIncoming, expectedResolved);
  }

  static Stream<Arguments> basicMultipleMeasurementAmendmentsScenarios() {
    return Stream.of(
        // Multiple existing events with different measurements and single incoming measurement
        // change results in amendment for only one measurement.
        Arguments.of(
            existing(event(Map.of("cores", 10.0)), event(Map.of("instance-hours", 2.0))),
            List.of(event(Map.of("cores", 12.0))),
            List.of(deduction(Map.of("cores", -10.0)), event(Map.of("cores", 12.0)))),
        // Single incoming event with multiple measurements amend each measurement.
        Arguments.of(
            existing(event(Map.of("cores", 10.0)), event(Map.of("instance-hours", 2.0))),
            List.of(event(Map.of("cores", 12.0, "instance-hours", 4.0))),
            List.of(
                deduction(Map.of("cores", -10.0)),
                deduction(Map.of("instance-hours", -2.0)),
                // The Event that is applied is not separated based on amendments.
                event(Map.of("cores", 12.0, "instance-hours", 4.0)))),
        // Single measurement amendment when existing conflicting event has multiple measurements.
        Arguments.of(
            existing(event(Map.of("cores", 10.0, "instance-hours", 2.0))),
            List.of(event(Map.of("cores", 12.0))),
            List.of(deduction(Map.of("cores", -10.0)), event(Map.of("cores", 12.0)))),
        Arguments.of(
            existing(event(Map.of("cores", 10.0, "instance-hours", 2.0))),
            List.of(event(Map.of("cores", 12.0, "instance-hours", 5.0))),
            List.of(
                deduction(Map.of("cores", -10.0)),
                deduction(Map.of("instance-hours", -2.0)),
                event(Map.of("cores", 12.0, "instance-hours", 5.0)))),
        Arguments.of(
            existing(event(Map.of("cores", 10.0, "instance-hours", 2.0))),
            List.of(
                event(Map.of("cores", 12.0)), event(Map.of("cores", 20.0, "instance-hours", 40.0))),
            List.of(
                deduction(Map.of("cores", -10.0)),
                event(Map.of("cores", 12.0)),
                deduction(Map.of("cores", -12.0)),
                deduction(Map.of("instance-hours", -2.0)),
                event(Map.of("cores", 20.0, "instance-hours", 40.0)))),
        Arguments.of(
            existing(event(Map.of("cores", 4.0, "instance-hours", 1.0))),
            List.of(
                event(Map.of("cores", 4.0, "instance-hours", 1.0)),
                event(Map.of("cores", 8.0, "instance-hours", 2.0))),
            List.of(
                deduction(Map.of("cores", -4.0)),
                deduction(Map.of("instance-hours", -1.0)),
                event(Map.of("cores", 4.0, "instance-hours", 1.0)),
                deduction(Map.of("cores", -4.0)),
                deduction(Map.of("instance-hours", -1.0)),
                event(Map.of("cores", 8.0, "instance-hours", 2.0)))));
  }

  @ParameterizedTest
  @MethodSource("basicMultipleMeasurementAmendmentsScenarios")
  void basicMultipleMeasurementAmendmentsScenario(
      List<EventArgument> expectedExisting,
      List<EventArgument> expectedIncoming,
      List<EventArgument> expectedResolved) {
    testResolutionScenario(expectedExisting, expectedIncoming, expectedResolved);
  }

  @Test
  void testConflictResolution_WillPreferMetricIdOverUomButSupportsBoth() {
    // NOTE We should never see a case where the metric_id and uom are different
    //      for a single measurement, but will test the edge case just in case.
    OffsetDateTime eventTimestamp = CLOCK.now();
    String instanceId = "instance1";
    EventRecord existingEvent =
        withExistingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                new Measurement().withUom("CoresIgnored").withMetricId("Cores").withValue(1.0),
                new Measurement().withMetricId("Instance-hours").withValue(5.0)));

    Event incomingEvent =
        withIncomingEvent(
            instanceId,
            eventTimestamp,
            List.of(
                // Should be applied to the existing Cores value.
                new Measurement().withUom("Cores").withValue(15.0),
                new Measurement().withUom("Instance-hours").withMetricId("").withValue(30.0)));

    when(repo.findConflictingEvents(Set.of(EventKey.fromEvent(existingEvent.getEvent()))))
        .thenReturn(List.of(existingEvent));

    List<EventRecord> resolved = resolver.resolveIncomingEvents(List.of(incomingEvent));
    assertEquals(3, resolved.size());
    assertDeductionEvent(resolved.get(0).getEvent(), instanceId, "Cores", -1.0);
    assertDeductionEvent(resolved.get(1).getEvent(), instanceId, "Instance-hours", -5.0);
    assertEquals(new EventRecord(incomingEvent), resolved.get(2));
  }

  static Stream<Arguments> equalMetricResolutionRequiredScenarios() {
    return Stream.of(
        Arguments.of(
            existing(event(Set.of("T1"), Map.of("cores", 6.0))),
            List.of(event(Set.of("T1"), Map.of("cores", 6.0))),
            List.of(
                deduction(Set.of("T1"), Map.of("cores", -6.0)),
                event(Set.of("T1"), Map.of("cores", 6.0)))),
        Arguments.of(
            existing(event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0))),
            List.of(event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0))),
            List.of(
                deduction(Set.of("T1"), Map.of("cores", -6.0)),
                deduction(Set.of("T2"), Map.of("cores", -6.0)),
                deduction(Set.of("T3"), Map.of("cores", -6.0)),
                event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T2"), Map.of("cores", 6.0)),
                event(Set.of("T3"), Map.of("cores", 6.0))),
            List.of(
                deduction(Set.of("T1"), Map.of("cores", -6.0)),
                deduction(Set.of("T2"), Map.of("cores", -6.0)),
                deduction(Set.of("T3"), Map.of("cores", -6.0)),
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T2"), Map.of("cores", 6.0)),
                event(Set.of("T3"), Map.of("cores", 6.0)))),
        Arguments.of(
            List.of(
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T2"), Map.of("cores", 6.0)),
                event(Set.of("T3"), Map.of("cores", 6.0))),
            List.of(event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0))),
            List.of(
                deduction(Set.of("T1"), Map.of("cores", -6.0)),
                deduction(Set.of("T2"), Map.of("cores", -6.0)),
                deduction(Set.of("T3"), Map.of("cores", -6.0)),
                event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0)))),
        Arguments.of(
            List.of(
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T1"), Map.of("instance-hours", 10.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T1"), Map.of("instance-hours", 10.0))),
            List.of(
                deduction(Set.of("T1"), Map.of("cores", -6.0)),
                deduction(Set.of("T1"), Map.of("instance-hours", -10.0)),
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T1"), Map.of("instance-hours", 10.0)))),
        Arguments.of(
            List.of(
                event(Set.of("T1", "T2"), Map.of("cores", 6.0)),
                event(Set.of("T2"), Map.of("instance-hours", 10.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", 6.0)),
                event(Set.of("T2"), Map.of("cores", 6.0)),
                event(Set.of("T2"), Map.of("instance-hours", 10.0))),
            List.of(
                deduction(Set.of("T1"), Map.of("cores", -6.0)),
                event(Set.of("T1"), Map.of("cores", 6.0)),
                deduction(Set.of("T2"), Map.of("cores", -6.0)),
                event(Set.of("T2"), Map.of("cores", 6.0)),
                deduction(Set.of("T2"), Map.of("instance-hours", -10.0)),
                event(Set.of("T2"), Map.of("instance-hours", 10.0)))));
  }

  @ParameterizedTest
  @MethodSource("equalMetricResolutionRequiredScenarios")
  void testEqualMetricResolutionRequiredScenarios(
      List<EventArgument> expectedExisting,
      List<EventArgument> expectedIncoming,
      List<EventArgument> expectedResolved) {
    testResolutionScenario(expectedExisting, expectedIncoming, expectedResolved);
  }

  static Stream<Arguments> tagResolutionScenarios() {
    return Stream.of(
        Arguments.of(
            List.of(event(Set.of("T1"), Map.of("cores", 10.0))),
            List.of(event(Set.of("T1"), Map.of("cores", 4.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("cores", 4.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 4.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 4.0)))),
        // Derived event is created when a measurement of the incoming event was
        // already covered by an existing event with a different tag set. In this
        // case, the incoming event is normalized into derived events with the
        // appropriate tag.
        Arguments.of(
            List.of(event(Set.of("T1"), Map.of("cores", 6.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 6.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -6.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 6.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0))),
            List.of(event(Set.of("T1"), Map.of("cores", 16.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -6.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("cores", 16.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2", "T3"), Map.of("cores", 6.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -6.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", -6.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 10.0)))),
        Arguments.of(
            List.of(event(Set.of("T1"), Map.of("cores", 6.0))),
            List.of(
                event(Set.of("T1", "T2"), Map.of("cores", 10.0)),
                event(Set.of("T2"), Map.of("cores", 8.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -6.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 10.0)),
                event(Set.of("T2"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", 8.0)))),
        Arguments.of(
            List.of(event(Set.of("T1"), Map.of("cores", 6.0))),
            List.of(
                event(Set.of("T1", "T2"), Map.of("cores", 10.0)),
                event(Set.of("T3"), Map.of("cores", 8.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -6.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 10.0)),
                event(Set.of("T3"), Map.of("cores", 8.0)))));
  }

  @ParameterizedTest
  @MethodSource("tagResolutionScenarios")
  void testTagResolutionScenarios(
      List<EventArgument> expectedExisting,
      List<EventArgument> expectedIncoming,
      List<EventArgument> expectedResolved) {
    testResolutionScenario(expectedExisting, expectedIncoming, expectedResolved);
  }

  static Stream<Arguments> multipleMetricTagScenarios() {
    return Stream.of(
        Arguments.of(
            List.of(),
            List.of(
                event(Set.of("T1"), Map.of("cores", 10.0)),
                event(Set.of("T1"), Map.of("instance-hours", 20.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", 10.0)),
                event(Set.of("T1"), Map.of("instance-hours", 20.0)))),
        Arguments.of(
            List.of(
                event(Set.of("T1"), Map.of("cores", 10.0)),
                event(Set.of("T1"), Map.of("instance-hours", 20.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 20.0, "instance-hours", 40.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 20.0, "instance-hours", 40.0)))),
        Arguments.of(
            List.of(event(Set.of("T1"), Map.of("cores", 10.0, "instance-hours", 4.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 4.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -4.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 4.0)))),
        Arguments.of(
            List.of(event(Set.of("T1"), Map.of("cores", 10.0, "instance-hours", 20.0))),
            List.of(event(Set.of("T1"), Map.of("cores", 20.0, "instance-hours", 40.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("cores", 20.0, "instance-hours", 40.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 20.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 20.0, "instance-hours", 40.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 20.0, "instance-hours", 40.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 20.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", 20.0, "instance-hours", 40.0)),
                event(Set.of("T2"), Map.of("cores", 20.0, "instance-hours", 40.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("cores", 20.0, "instance-hours", 40.0)),
                event(Set.of("T2"), Map.of("cores", 20.0, "instance-hours", 40.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 20.0))),
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 40.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 40.0)))),
        Arguments.of(
            List.of(event(Set.of("T1", "T2"), Map.of("cores", 10.0, "instance-hours", 20.0))),
            List.of(event(Set.of("T1", "T2", "T3"), Map.of("cores", 10.0, "instance-hours", 40.0))),
            List.of(
                event(Set.of("T1"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("cores", -10.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T2"), Map.of("instance-hours", -20.0))
                    .withAmendmentType(AmendmentType.DEDUCTION),
                event(Set.of("T1", "T2", "T3"), Map.of("cores", 10.0, "instance-hours", 40.0)))));
  }

  @ParameterizedTest
  @MethodSource("multipleMetricTagScenarios")
  void testMultipleMetricTagScenarios(
      List<EventArgument> expectedExisting,
      List<EventArgument> expectedIncoming,
      List<EventArgument> expectedResolved) {
    testResolutionScenario(expectedExisting, expectedIncoming, expectedResolved);
  }

  void testResolutionScenario(
      List<EventArgument> expectedExisting,
      List<EventArgument> expectedIncoming,
      List<EventArgument> expectedResolved) {
    Set<EventKey> conflictCheckKeys =
        expectedIncoming.stream()
            .map(ea -> EventKey.fromEvent(ea.toEvent()))
            .collect(Collectors.toSet());
    List<EventRecord> existingEventRecords =
        expectedExisting.stream().map(EventArgument::toRecord).toList();
    existingEventRecords.forEach(existing -> {});

    when(repo.findConflictingEvents(conflictCheckKeys)).thenReturn(existingEventRecords);

    List<Event> incomingEvents = expectedIncoming.stream().map(EventArgument::toEvent).toList();
    List<EventRecord> resolved = resolver.resolveIncomingEvents(incomingEvents);

    List<Event> expectedResolvedEvents =
        expectedResolved.stream().map(EventArgument::toEvent).toList();
    assertEvents(expectedResolvedEvents, resolved.stream().map(EventRecord::getEvent).toList());
  }

  private static Event createEvent(String instanceId, OffsetDateTime timestamp) {
    return new Event()
        .withOrgId("org1")
        .withEventType("test_event_type")
        .withEventSource("test_source")
        .withServiceType("test_service_type")
        .withInstanceId(instanceId)
        .withProductTag(Set.of("Tag1"))
        .withTimestamp(timestamp);
  }

  private EventRecord withExistingEvent(
      String instanceId, OffsetDateTime timestamp, List<Measurement> measurements) {
    Event existingEvent = withIncomingEvent(instanceId, timestamp, measurements);
    EventRecord existingEventRecord = new EventRecord(existingEvent);
    existingEventRecord.prePersist();
    return existingEventRecord;
  }

  private static Event withIncomingEvent(
      String instanceId, OffsetDateTime timestamp, List<Measurement> measurements) {
    Event existingEvent = createEvent(instanceId, timestamp);
    existingEvent.setMeasurements(measurements);
    return existingEvent;
  }

  private void assertMetricIdValue(Event event, String metricId, Double value) {
    List<Measurement> matching =
        event.getMeasurements().stream().filter(m -> metricId.equals(m.getMetricId())).toList();
    assertFalse(matching.isEmpty(), "Did not find an measurement matching metric_id: " + metricId);
    Measurement measurement = matching.get(0);
    assertEquals(metricId, measurement.getMetricId());
    assertEquals(value, measurement.getValue());
  }

  private void assertDeductionEvent(
      Event deductionEvent, String instanceId, String metricId, Double value) {
    assertEquals(instanceId, deductionEvent.getInstanceId());
    assertEquals(AmendmentType.DEDUCTION, deductionEvent.getAmendmentType());
    assertMetricIdValue(deductionEvent, metricId, value);
  }

  private void assertEvents(List<Event> expected, List<Event> actual) {
    // Because we have no control over the generated Event.equals and Lists must be in the same
    // order,
    // we need to sort the internal collections of events in order to make equals work correctly
    // when comparing results.
    sortEventCollections(expected);
    sortEventCollections(actual);

    assertEquals(expected.size(), actual.size(), printExpectation(expected, actual));
    for (Event e : expected) {
      assertTrue(actual.contains(e), printDoesNotContain(e, actual));
    }
  }

  private String printDoesNotContain(Event expected, List<Event> allEvents) {
    try {
      String expectedOutput = MAPPER.writeValueAsString(expected);
      String actualOutput = MAPPER.writeValueAsString(allEvents);
      return String.format("Event: %s%swas not found in: %s", expectedOutput, "\n", actualOutput);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private String printExpectation(List<Event> expected, List<Event> actual) {
    try {
      String expectedOutput = MAPPER.writeValueAsString(expected);
      String actualOutput = MAPPER.writeValueAsString(actual);
      return String.format("Expected: %s%sbut was: %s", expectedOutput, "\n", actualOutput);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
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

  public static class EventArgument {
    private AmendmentType amendmentType;
    private String instanceId = "instance_1";
    private OffsetDateTime timestamp = CLOCK.now();
    private final Set<String> tags;
    private final Map<String, Double> measurements;
    private OffsetDateTime recordDate;

    private EventArgument(Set<String> tags, Map<String, Double> measurements) {
      this.tags = tags;
      this.measurements = measurements;
    }

    private EventArgument(Map<String, Double> measurements) {
      this(Set.of("T1"), measurements);
    }

    Event toEvent() {
      Event event =
          withIncomingEvent(
              instanceId,
              timestamp,
              measurements.entrySet().stream()
                  .map(
                      entry ->
                          new Measurement()
                              .withMetricId(entry.getKey())
                              .withUom(entry.getKey())
                              .withValue(entry.getValue()))
                  .toList());
      event.setProductTag(tags);
      event.setAmendmentType(Objects.isNull(amendmentType) ? null : amendmentType);
      event.setRecordDate(recordDate);
      return event;
    }

    EventRecord toRecord() {
      EventRecord eventRecord = new EventRecord(toEvent());
      eventRecord.prePersist();
      return eventRecord;
    }

    EventArgument withInstanceId(String instanceId) {
      this.instanceId = instanceId;
      return this;
    }

    EventArgument withAmendmentType(AmendmentType amendmentType) {
      this.amendmentType = amendmentType;
      return this;
    }

    EventArgument withTimestamp(OffsetDateTime timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    EventArgument withRecordDate(OffsetDateTime recordDate) {
      this.recordDate = recordDate;
      return this;
    }
  }

  static List<EventArgument> existing(EventArgument... args) {
    IntStream.range(0, args.length)
        .forEach(i -> args[i].withRecordDate(CLOCK.now().plusSeconds(1 + i)));
    return Arrays.asList(args);
  }

  static EventArgument event(Set<String> tags, Map<String, Double> measurements) {
    return new EventArgument(tags, measurements);
  }

  static EventArgument event(Map<String, Double> measurements) {
    return new EventArgument(measurements);
  }

  static EventArgument resolved(Set<String> tags, Map<String, Double> measurements) {
    return new EventArgument(tags, measurements);
  }

  static EventArgument deduction(Set<String> tags, Map<String, Double> measurements) {
    return new EventArgument(tags, measurements).withAmendmentType(AmendmentType.DEDUCTION);
  }

  static EventArgument deduction(Map<String, Double> measurements) {
    return new EventArgument(measurements).withAmendmentType(AmendmentType.DEDUCTION);
  }
}
