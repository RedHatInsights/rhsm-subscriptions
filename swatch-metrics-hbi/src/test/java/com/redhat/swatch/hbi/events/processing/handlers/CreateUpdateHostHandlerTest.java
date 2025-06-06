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
package com.redhat.swatch.hbi.events.processing.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationshipRepository;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestData;
import com.redhat.swatch.hbi.events.test.helpers.HbiEventTestHelper;
import com.redhat.swatch.hbi.events.test.helpers.SwatchEventTestHelper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateUpdateHostHandlerTest {
  @Mock private HbiHostRelationshipRepository relationshipRepo;
  private HbiEventTestHelper hbiEventHelper;
  private SwatchEventTestHelper swatchEventHelper;
  private CreateUpdateHostHandler handler;

  @BeforeEach
  void setUp() {
    hbiEventHelper = new HbiEventTestHelper();
    swatchEventHelper = new SwatchEventTestHelper(hbiEventHelper.getClock());

    HbiHostRelationshipService relService =
        new HbiHostRelationshipService(hbiEventHelper.getClock(), relationshipRepo);
    FactNormalizer factNormalizer =
        new FactNormalizer(hbiEventHelper.getClock(), hbiEventHelper.getConfig(), relService);
    MeasurementNormalizer measurementNormalizer =
        new MeasurementNormalizer(hbiEventHelper.getConfig());
    handler =
        new CreateUpdateHostHandler(
            hbiEventHelper.getConfig(),
            hbiEventHelper.getClock(),
            factNormalizer,
            measurementNormalizer,
            relService,
            hbiEventHelper.getObjectMapper());
  }

  static Stream<Arguments> skipEventTestArgs() {
    ApplicationClock clock = new ApplicationClock();
    OffsetDateTime notStale = clock.now().plusMonths(1);
    OffsetDateTime stale = clock.now().minusMonths(2);
    return Stream.of(
        // staleTimestamp, billingModel, hostType, shouldBeFiltered
        Arguments.of(notStale, "marketplace", "good_host_type", true),
        // Valid events should not be filtered.
        Arguments.of(notStale, "annual", "good_host_type", false),
        // Stale hosts should be filtered.
        Arguments.of(stale, "annual", "good_host_type", true),
        // Hosts with type 'edge' should be filtered.
        Arguments.of(notStale, "annual", "edge", true),
        // Nulls are considered valid when filtering.
        Arguments.of(notStale, null, null, false),
        // Empty strings are considered valid when filtering.
        Arguments.of(notStale, "", "", false));
  }

  @ParameterizedTest
  @MethodSource("skipEventTestArgs")
  void testSkipEvent(
      OffsetDateTime staleTimestamp,
      String billingModel,
      String hostType,
      boolean shouldBeFiltered) {
    var hbiEvent =
        hbiEventHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    hbiEvent.getHost().setStaleTimestamp(staleTimestamp.toString());
    setBillingModel(hbiEvent, billingModel);
    setHostType(hbiEvent, hostType);

    assertEquals(shouldBeFiltered, handler.skipEvent(hbiEvent));
  }

  @Test
  void testPhysicalHostCreated() {
    var hbiEvent =
        hbiEventHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();
    Event expected =
        swatchEventHelper.buildPhysicalRhelEvent(
            hbiEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            eventTimestamp,
            false,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    List<Event> events = handler.handleEvent(hbiEvent);
    assertEquals(1, events.size());
    assertEquals(expected, events.get(0));

    HbiHostRelationship relationship = hbiEventHelper.relationshipFromHbiEvent(hbiEvent);
    verify(relationshipRepo, times(1)).persist(relationship);
  }

  @Test
  void testPhysicalHypervisorHostCreated() {
    var hbiEvent =
        hbiEventHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    when(relationshipRepo.guestCount(
            hbiEvent.getHost().getOrgId(), hbiEvent.getHost().getSubscriptionManagerId()))
        .thenReturn(1L);

    OffsetDateTime eventTimestamp = hbiEvent.getTimestamp().toOffsetDateTime();
    Event expected =
        swatchEventHelper.buildPhysicalRhelEvent(
            hbiEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            eventTimestamp,
            true,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    List<Event> events = handler.handleEvent(hbiEvent);
    assertEquals(1, events.size());
    assertEquals(expected, events.get(0));

    HbiHostRelationship relationship = hbiEventHelper.relationshipFromHbiEvent(hbiEvent);
    verify(relationshipRepo, times(1)).persist(relationship);
  }

  @Test
  void testUnmappedGuestCreated() {
    // Hypervisor has not yet been reported. The guest is tracked as unmapped.
    String hypervisorUuid = UUID.randomUUID().toString();
    var guestCreatedEvent =
        hbiEventHelper.createTemplatedGuestCreatedEvent(
            "testOrg", UUID.randomUUID(), UUID.randomUUID(), hypervisorUuid);
    var guestRelationship =
        hbiEventHelper.virtualRelationshipFromHbiEvent(guestCreatedEvent, hypervisorUuid, true);

    OffsetDateTime guestCreatedTimestamp = guestCreatedEvent.getTimestamp().toOffsetDateTime();
    Event expectedUnmappedGuestEvent =
        swatchEventHelper.createExpectedEvent(
            guestCreatedEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            guestCreatedTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            // Expected that the host is unmapped
            true,
            false,
            hypervisorUuid,
            List.of("69"),
            Set.of("RHEL for x86"),
            // Virtual unmapped RHEL account for a single socket (system profile sockets == 2
            // in test data).
            swatchEventHelper.buildMeasurements(1.0, 1.0));

    List<Event> events = handler.handleEvent(guestCreatedEvent);
    assertEquals(1, events.size());
    assertEquals(expectedUnmappedGuestEvent, events.get(0));

    verify(relationshipRepo, times(1)).persist(any(HbiHostRelationship.class));
    verify(relationshipRepo, times(1)).persist(guestRelationship);
  }

  @Test
  void testIncomingHypervisorReCalculatesAllUnmappedGuests() throws Exception {
    var hypervisorEvent =
        hbiEventHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    var hypervisorRelationship = withHypervisor(hypervisorEvent);

    var guest1 =
        hbiEventHelper.createTemplatedGuestCreatedEvent(
            hypervisorRelationship.getOrgId(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            hypervisorEvent.getHost().getSubscriptionManagerId());

    var guest2 =
        hbiEventHelper.createTemplatedGuestCreatedEvent(
            hypervisorRelationship.getOrgId(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            hypervisorEvent.getHost().getSubscriptionManagerId());

    var guestRelationships =
        withUnmappedGuests(
            List.of(guest1, guest2), hypervisorRelationship.getSubscriptionManagerId());

    OffsetDateTime hypervisorEventTimestamp = hypervisorEvent.getTimestamp().toOffsetDateTime();
    var hypervisorHbiHost = hypervisorEvent.getHost();

    Event expectedHypervisorEvent =
        swatchEventHelper.buildPhysicalRhelEvent(
            hypervisorHbiHost,
            NormalizedEventType.INSTANCE_CREATED,
            hypervisorEventTimestamp,
            true,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    Event expectedMappedGuest1Event =
        swatchEventHelper.createExpectedEvent(
            guest1.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            hypervisorEventTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            // Expected that the guest is mapped since the hypervisor relationship was known.
            false,
            false,
            hypervisorEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            swatchEventHelper.buildMeasurements(1.0, 2.0));

    Event expectedMappedGuest2Event =
        swatchEventHelper.createExpectedEvent(
            guest2.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            hypervisorEventTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            // Expected that the guest is mapped since the hypervisor relationship was known.
            false,
            false,
            hypervisorEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            swatchEventHelper.buildMeasurements(1.0, 2.0));

    List<Event> events = handler.handleEvent(hypervisorEvent);
    assertEquals(3, events.size());
    assertEquals(expectedHypervisorEvent, events.get(0));
    assertEquals(expectedMappedGuest1Event, events.get(1));
    assertEquals(expectedMappedGuest2Event, events.get(2));

    // Verify that the relationships were updated correctly.
    verify(relationshipRepo, times(3)).persist(any(HbiHostRelationship.class));

    // Initial creation of the hypervisor relationship.
    verify(relationshipRepo, times(1)).persist(hypervisorRelationship);

    // The guest relationships should have been switched to a mapped guests.
    assertEquals(2, guestRelationships.size());
    guestRelationships.forEach(
        guestRelationship -> {
          guestRelationship.setUnmappedGuest(false);
          verify(relationshipRepo, times(1)).persist(guestRelationship);
        });
  }

  @Test
  void testIncomingGuestReCalculatesHypervisor() {
    var hypervisorEvent =
        hbiEventHelper.getCreateUpdateEvent(HbiEventTestData.getPhysicalRhelHostCreatedEvent());
    var hypervisorRelationship = withHypervisor(hypervisorEvent);

    var guestEvent =
        hbiEventHelper.createTemplatedGuestCreatedEvent(
            hypervisorRelationship.getOrgId(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            hypervisorRelationship.getSubscriptionManagerId());
    mockGuestFactNormalization(guestEvent);

    var guestRelationship =
        hbiEventHelper.virtualRelationshipFromHbiEvent(
            guestEvent, hypervisorRelationship.getSubscriptionManagerId(), false);

    OffsetDateTime expectedTimestamp = guestEvent.getTimestamp().toOffsetDateTime();
    Event expectedGuestEvent =
        swatchEventHelper.createExpectedEvent(
            guestEvent.getHost(),
            NormalizedEventType.INSTANCE_CREATED,
            expectedTimestamp,
            Sla.SELF_SUPPORT,
            Usage.DEVELOPMENT_TEST,
            HardwareType.VIRTUAL,
            true,
            // Expected that the guest is mapped since the hypervisor relationship was known.
            false,
            false,
            hypervisorEvent.getHost().getSubscriptionManagerId(),
            List.of("69"),
            Set.of("RHEL for x86"),
            swatchEventHelper.buildMeasurements(1.0, 2.0));

    Event expectedHypervisorUpdateEvent =
        swatchEventHelper.buildPhysicalRhelEvent(
            hypervisorEvent.getHost(),
            NormalizedEventType.INSTANCE_UPDATED,
            expectedTimestamp,
            true,
            swatchEventHelper.buildMeasurements(2.0, 2.0));

    List<Event> events = handler.handleEvent(guestEvent);
    assertEquals(2, events.size());
    assertEquals(expectedGuestEvent, events.get(0));
    assertEquals(expectedHypervisorUpdateEvent, events.get(1));

    verify(relationshipRepo, times(2)).persist(any(HbiHostRelationship.class));
    verify(relationshipRepo, times(1)).persist(guestRelationship);
    verify(relationshipRepo, times(1)).persist(hypervisorRelationship);
  }

  private void mockGuestFactNormalization(HbiHostCreateUpdateEvent hostEvent) {
    String orgId = hostEvent.getHost().getOrgId();
    String subscriptionManagerId = hostEvent.getHost().getSubscriptionManagerId();

    // Used by relationship service to determine isHypervisor in the FactNormalizer.
    when(relationshipRepo.guestCount(orgId, subscriptionManagerId)).thenReturn(0L);
  }

  private void setBillingModel(HbiHostCreateUpdateEvent event, String billingModel) {
    hbiEventHelper.setFact(
        event, RhsmFacts.RHSM_FACTS_NAMESPACE, RhsmFacts.BILLING_MODEL, billingModel);
  }

  private void setHostType(HbiHostCreateUpdateEvent event, String hostType) {
    event.getHost().getSystemProfile().put(SystemProfileFacts.HOST_TYPE_FACT, hostType);
  }

  private HbiHostRelationship withHypervisor(HbiHostCreateUpdateEvent hypervisorEvent) {
    String orgId = hypervisorEvent.getHost().getOrgId();
    String hypervisorUuid = hypervisorEvent.getHost().getSubscriptionManagerId();
    // Used by relationship service to determine isHypervisor in the FactNormalizer.
    when(relationshipRepo.guestCount(orgId, hypervisorUuid)).thenReturn(1L);

    HbiHostRelationship hypervisorRelationship =
        hbiEventHelper.relationshipFromHbiEvent(hypervisorEvent);
    // Used by relationship service to determine the hypervisor for a guest in the FactNormalizer.
    when(relationshipRepo.findByOrgIdAndSubscriptionManagerId(orgId, hypervisorUuid))
        .thenReturn(Optional.of(hypervisorRelationship));
    return hypervisorRelationship;
  }

  private List<HbiHostRelationship> withUnmappedGuests(
      List<HbiHostCreateUpdateEvent> guestEvents, String hypervisorUuid) {
    List<HbiHostRelationship> unmappedGuestRelationships =
        guestEvents.stream()
            .map(e -> hbiEventHelper.virtualRelationshipFromHbiEvent(e, hypervisorUuid, true))
            .toList();

    unmappedGuestRelationships.forEach(
        relationship -> {
          assertTrue(relationship.isUnmappedGuest());
        });

    unmappedGuestRelationships.stream()
        .collect(Collectors.groupingBy(HbiHostRelationship::getOrgId))
        .forEach(
            (orgId, relationships) ->
                when(relationshipRepo.findUnmappedGuests(orgId, hypervisorUuid))
                    .thenReturn(relationships));

    return unmappedGuestRelationships;
  }
}
