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
package com.redhat.swatch.hbi.events.ct.tests;

import static com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper.getMeasurementValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SatelliteFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.MessageValidators;
import com.redhat.swatch.hbi.events.ct.utils.SwatchEventHelper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class MetricsHbiMultipleReportersComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-multiple-reporters-TC001")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldPreferRecentRhsmSlaAndUsageOverSatellite(
      String hbiEventType, NormalizedEventType swatchEventType) {
    OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .apply(
                dualReporterPhysicalHostEventTemplate(
                    hbiEventType,
                    timestamp,
                    timestamp.minusHours(1),
                    "Premium",
                    "Production",
                    true,
                    true))
            .toEvent()
            .type(hbiEventType)
            .timestamp(timestamp)
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Recent RHSM values take precedence and product IDs from both sources are retained
    Event swatchEvent =
        expectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69", "250"),
            Set.of("Satellite Server"),
            "Premium",
            "Production");

    thenSwatchEventAppears(hbiEvent, swatchEventType, swatchEvent);
  }

  @TestPlanName("metrics-hbi-multiple-reporters-TC002")
  @ParameterizedTest
  @CsvSource({"created, INSTANCE_CREATED", "updated, INSTANCE_UPDATED"})
  void shouldUseSatelliteSlaAndUsageWhenRhsmSyncIsStale(
      String hbiEventType, NormalizedEventType swatchEventType) {
    OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .apply(
                dualReporterPhysicalHostEventTemplate(
                    hbiEventType,
                    timestamp,
                    timestamp.minusDays(30),
                    "Premium",
                    "Production",
                    true,
                    true))
            .toEvent()
            .type(hbiEventType)
            .timestamp(timestamp)
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Satellite fills SLA and Usage, and stale RHSM product IDs are omitted
    Event swatchEvent =
        expectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("250"),
            Set.of("Satellite Server"),
            "Standard",
            "Development/Test");

    thenSwatchEventAppears(hbiEvent, swatchEventType, swatchEvent);
  }

  private static Stream<Arguments> rhsmSlaAndUsageFallbackCases() {
    // Arguments: HBI type, SWatch type, RHSM SLA/usage, expected SLA/usage.
    // Each scenario repeats for create and update. Null means RHSM omitted that value.
    return Stream.of(
        // RHSM has Usage only: preserve Production and get Standard SLA from Satellite.
        Arguments.of(
            "created",
            NormalizedEventType.INSTANCE_CREATED,
            null,
            "Production",
            "Standard",
            "Production"),
        Arguments.of(
            "updated",
            NormalizedEventType.INSTANCE_UPDATED,
            null,
            "Production",
            "Standard",
            "Production"),
        // RHSM has SLA only: preserve Premium and get Development/Test usage from Satellite.
        Arguments.of(
            "created",
            NormalizedEventType.INSTANCE_CREATED,
            "Premium",
            null,
            "Premium",
            "Development/Test"),
        Arguments.of(
            "updated",
            NormalizedEventType.INSTANCE_UPDATED,
            "Premium",
            null,
            "Premium",
            "Development/Test"),
        // RHSM has neither value: both SLA and usage come from Satellite.
        Arguments.of(
            "created",
            NormalizedEventType.INSTANCE_CREATED,
            null,
            null,
            "Standard",
            "Development/Test"),
        Arguments.of(
            "updated",
            NormalizedEventType.INSTANCE_UPDATED,
            null,
            null,
            "Standard",
            "Development/Test"),
        // An unsupported RHSM SLA falls back independently while valid RHSM usage is retained.
        Arguments.of(
            "created",
            NormalizedEventType.INSTANCE_CREATED,
            "Unsupported SLA",
            "Production",
            "Standard",
            "Production"),
        Arguments.of(
            "updated",
            NormalizedEventType.INSTANCE_UPDATED,
            "Unsupported SLA",
            "Production",
            "Standard",
            "Production"),
        // An unsupported RHSM usage falls back independently while valid RHSM SLA is retained.
        Arguments.of(
            "created",
            NormalizedEventType.INSTANCE_CREATED,
            "Premium",
            "Unsupported Usage",
            "Premium",
            "Development/Test"),
        Arguments.of(
            "updated",
            NormalizedEventType.INSTANCE_UPDATED,
            "Premium",
            "Unsupported Usage",
            "Premium",
            "Development/Test"),
        // Unsupported RHSM values for both fields fall back to Satellite.
        Arguments.of(
            "created",
            NormalizedEventType.INSTANCE_CREATED,
            "Unsupported SLA",
            "Unsupported Usage",
            "Standard",
            "Development/Test"),
        Arguments.of(
            "updated",
            NormalizedEventType.INSTANCE_UPDATED,
            "Unsupported SLA",
            "Unsupported Usage",
            "Standard",
            "Development/Test"));
  }

  @TestPlanName("metrics-hbi-multiple-reporters-TC003")
  @ParameterizedTest
  @MethodSource("rhsmSlaAndUsageFallbackCases")
  void shouldResolveRhsmSlaAndUsageIndependently(
      String hbiEventType,
      NormalizedEventType swatchEventType,
      String rhsmSla,
      String rhsmUsage,
      String expectedSla,
      String expectedUsage) {
    OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.UTC);

    // Omit Satellite's role and installed product so RHSM product 69 is the only source of the tag.
    HbiHostCreateUpdateEvent hbiEvent =
        hostEvents
            .createHost(orgId)
            .apply(
                dualReporterPhysicalHostEventTemplate(
                    hbiEventType,
                    timestamp,
                    timestamp.minusHours(1),
                    rhsmSla,
                    rhsmUsage,
                    false,
                    false))
            .toEvent()
            .type(hbiEventType)
            .timestamp(timestamp)
            .buildCreateUpdate();

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Each missing RHSM value is filled independently from Satellite
    Event swatchEvent =
        expectedEvent(
            hbiEvent,
            swatchEventType,
            List.of("69"),
            Set.of("RHEL for x86"),
            expectedSla,
            expectedUsage);

    thenSwatchEventAppears(hbiEvent, swatchEventType, swatchEvent);
  }

  private void thenSwatchEventAppears(
      HbiHostCreateUpdateEvent hbiEvent, NormalizedEventType swatchEventType, Event expectedEvent) {
    String instanceId = hbiEvent.getHost().getId().toString();
    List<Event> actualEvents =
        kafkaBridge.waitForKafkaMessage(
            Topics.SWATCH_SERVICE_INSTANCE_INGRESS,
            MessageValidators.swatchEventMatches(orgId, instanceId, swatchEventType.toString()),
            1,
            AwaitilitySettings.defaults().onConditionNotMet(service::flushOutboxSynchronously));

    assertEquals(1, actualEvents.size(), "Expected exactly one matching SWatch event");
    Event actualEvent = actualEvents.getFirst();
    assertEquals(expectedEvent.getProductTag(), actualEvent.getProductTag());
    assertEquals(expectedEvent.getProductIds(), actualEvent.getProductIds());
    assertEquals(2.0, getMeasurementValue(actualEvent, "sockets"));
    assertEquals(4.0, getMeasurementValue(actualEvent, "cores"));
    assertFalse(actualEvent.getIsVirtual(), "Should be a physical host");
    assertEquals(expectedEvent.getSla(), actualEvent.getSla());
    assertEquals(expectedEvent.getUsage(), actualEvent.getUsage());
    assertTrue(SwatchEventHelper.eventsMatch(expectedEvent, actualEvent));
  }

  private static Function<HostBuilder, HostBuilder> dualReporterPhysicalHostEventTemplate(
      String hbiEventType,
      OffsetDateTime timestamp,
      OffsetDateTime rhsmSyncTimestamp,
      String rhsmSla,
      String rhsmUsage,
      boolean includeSatelliteProduct,
      boolean includeSatelliteRole) {
    return builder -> {
      RhsmFacts rhsmFacts =
          RhsmFacts.builder()
              .defaultFacts()
              .sla(rhsmSla)
              .usage(rhsmUsage)
              .syncTimestamp(rhsmSyncTimestamp.toString())
              .build();

      var systemProfile =
          SystemProfileFacts.builder()
              .infrastructureType("physical")
              .arch("x86_64")
              .coresPerSocket(2)
              .numberOfSockets(2)
              .numberOfCpus(4);
      if (includeSatelliteProduct) {
        systemProfile.installedProductId("250");
      }

      var satelliteFacts = SatelliteFacts.builder().sla("Standard").usage("Development/Test");
      if (includeSatelliteRole) {
        satelliteFacts.role("Red Hat Enterprise Linux Server");
      }

      return builder
          .subscriptionManagerId(UUID.randomUUID().toString())
          .rhsmFacts(rhsmFacts)
          .satelliteFacts(satelliteFacts.build())
          .systemProfileFacts(systemProfile.build());
    };
  }

  private static Event expectedEvent(
      HbiHostCreateUpdateEvent hbiEvent,
      NormalizedEventType swatchEventType,
      List<String> productIds,
      Set<String> productTags,
      String sla,
      String usage) {
    return SwatchEventHelper.createExpectedEvent(
            hbiEvent, swatchEventType, productIds, productTags, false, false)
        .withSla(Sla.fromValue(sla))
        .withUsage(Usage.fromValue(usage));
  }
}
