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

import static com.redhat.swatch.hbi.events.constants.HbiEventConstants.EVENT_SERVICE_TYPE;
import static com.redhat.swatch.hbi.events.constants.HbiEventConstants.EVENT_SOURCE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.exception.UnrecoverableMessageProcessingException;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.events.normalization.model.Host;
import com.redhat.swatch.hbi.events.normalization.model.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.model.NormalizedFacts;
import com.redhat.swatch.hbi.events.normalization.model.NormalizedMeasurements;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;

@AllArgsConstructor
@ApplicationScoped
public class HostEventHandlerService {

  private final ApplicationClock clock;
  private final FactNormalizer factNormalizer;
  private final MeasurementNormalizer measurementNormalizer;
  private final HbiHostRelationshipService relationshipService;
  private final ObjectMapper objectMapper;

  /**
   * Refresh a host's measurements and create a new Event representing these changes.
   *
   * @param eventType the type marker for this event.
   * @param hostRelationship the unmapped guests hypervisor relationship.
   * @param refreshTimestamp the timestamp for the new swatch event.
   * @return a new Event representing the host's new state.
   */
  private Event refreshHost(
      NormalizedEventType eventType,
      HbiHostRelationship hostRelationship,
      OffsetDateTime refreshTimestamp) {
    try {
      Host host =
          new Host(objectMapper.readValue(hostRelationship.getLatestHbiEventData(), HbiHost.class));
      NormalizedFacts facts = factNormalizer.normalize(host);
      Event event = buildEvent(eventType, facts, host, refreshTimestamp);
      relationshipService.processHost(
          facts.getOrgId(),
          facts.getInventoryId(),
          facts.getSubscriptionManagerId(),
          facts.getHypervisorUuid(),
          facts.isUnmappedGuest(),
          hostRelationship.getLatestHbiEventData());
      return event;
    } catch (JsonProcessingException e) {
      throw new UnrecoverableMessageProcessingException(
          "Unable to serialize host data from HBI host.", e);
    }
  }

  public List<Event> updateHostRelationshipAndAllDependants(
      Host targetHost, HbiHostCreateUpdateEvent originalHbiEvent, OffsetDateTime eventTimestamp) {
    NormalizedFacts facts = factNormalizer.normalize(targetHost);

    List<Event> toSend = new ArrayList<>();
    // Need to update the host relationship always.
    toSend.add(updateHostRelationship(facts, targetHost, originalHbiEvent, eventTimestamp));

    // Update the dependant relationships based on what type of host it is.
    if (facts.isHypervisor()) {
      // Incoming event was for a hypervisor. Update all existing guest relationships
      // for this host so that the mapped/unmapped status of the guests change.
      toSend.addAll(
          updateUnmappedGuestRelationships(
              facts.getOrgId(), facts.getSubscriptionManagerId(), eventTimestamp));
    } else if (facts.isGuest() && !facts.isUnmappedGuest()) {
      // Incoming event was for a mapped guest. Update the hypervisor relationship
      // so that any applicable changes are sent via the swatch event.
      updateHypervisorRelationship(facts.getOrgId(), facts.getHypervisorUuid(), eventTimestamp)
          .ifPresent(toSend::add);
    }
    return toSend;
  }

  /**
   * Updates the target host's current relationship, or creates a new one if it does not exist.
   *
   * @param facts the normalized facts of the target host.
   * @param targetHost the target HBI host data from the HBI event.
   * @param hbiHostEvent the host event that triggered the update.
   * @param eventTimestamp the originating event timestamp.
   * @return a new swatch event representing the changes to the host.
   */
  private Event updateHostRelationship(
      NormalizedFacts facts,
      Host targetHost,
      HbiHostCreateUpdateEvent hbiHostEvent,
      OffsetDateTime eventTimestamp) {
    try {
      // TODO Can the Host event be omitted?
      relationshipService.processHost(
          facts.getOrgId(),
          facts.getInventoryId(),
          facts.getSubscriptionManagerId(),
          facts.getHypervisorUuid(),
          facts.isUnmappedGuest(),
          objectMapper.writeValueAsString(hbiHostEvent.getHost()));
    } catch (JsonProcessingException e) {
      throw new UnrecoverableMessageProcessingException(
          "Unable to serialize host data from HBI host.", e);
    }

    return buildEvent(NormalizedEventType.from(hbiHostEvent), facts, targetHost, eventTimestamp);
  }

  public Event createDeleteHostEvent(
      HbiHostRelationship deletedHostRelationship, OffsetDateTime deleteTimestamp) {
    Host host;
    try {
      host =
          new Host(
              objectMapper.readValue(
                  deletedHostRelationship.getLatestHbiEventData(), HbiHost.class));
    } catch (Exception e) {
      throw new UnrecoverableMessageProcessingException(
          "Unable to serialize host data from HBI host.", e);
    }

    NormalizedFacts facts = factNormalizer.normalize(host);
    return buildEvent(NormalizedEventType.INSTANCE_DELETED, facts, host, deleteTimestamp);
  }

  public Optional<HbiHostRelationship> removeHostRelationship(String orgId, UUID inventoryUuid) {
    return relationshipService.deleteHostRelationship(orgId, inventoryUuid);
  }

  protected Optional<Event> updateHypervisorRelationship(
      String orgId, String hypervisorUuid, OffsetDateTime eventTimestamp) {
    // Make sure that the guest's hypervisor data is up to date.
    Optional<HbiHostRelationship> hypervisor =
        relationshipService.findHypervisor(orgId, hypervisorUuid);
    return hypervisor.map(
        h -> refreshHost(NormalizedEventType.INSTANCE_UPDATED, h, eventTimestamp));
  }

  protected List<Event> updateUnmappedGuestRelationships(
      String orgId, String hypervisorSubscriptionManagerId, OffsetDateTime eventTimestamp) {
    if (StringUtils.isBlank(hypervisorSubscriptionManagerId)) {
      // A host can only be a hypervisor if it is mapped by subscriptionManagerId.
      return List.of();
    }

    // Reprocess any unmapped guests and resend an event with updated measurements.
    return relationshipService.getUnmappedGuests(orgId, hypervisorSubscriptionManagerId).stream()
        .map(
            unmapped ->
                refreshGuest(NormalizedEventType.INSTANCE_UPDATED, unmapped, eventTimestamp))
        .toList();
  }

  protected List<Event> updateMappedGuestRelationships(
      String orgId, String hypervisorSubscriptionManagerId, OffsetDateTime eventTimestamp) {
    if (StringUtils.isBlank(hypervisorSubscriptionManagerId)) {
      // A host can only be a hypervisor if it is mapped by subscriptionManagerId.
      return List.of();
    }

    // Reprocess any mapped guests and resend an event with updated measurements.
    return relationshipService.getMappedGuests(orgId, hypervisorSubscriptionManagerId).stream()
        .map(
            unmapped ->
                refreshGuest(NormalizedEventType.INSTANCE_UPDATED, unmapped, eventTimestamp))
        .toList();
  }

  /**
   * Refresh an unmapped guest's measurements and create a new Event representing these changes.
   *
   * @param guestRelationship the unmapped guests hypervisor relationship.
   * @return a new Event representing the host's new state.
   */
  private Event refreshGuest(
      NormalizedEventType eventType,
      HbiHostRelationship guestRelationship,
      OffsetDateTime hypervisorTimestamp) {
    return refreshHost(eventType, guestRelationship, hypervisorTimestamp);
  }

  protected Event buildEvent(
      NormalizedEventType eventType,
      NormalizedFacts facts,
      Host hbiHost,
      OffsetDateTime eventTimestamp) {

    NormalizedMeasurements measurements =
        measurementNormalizer.getMeasurements(
            facts,
            hbiHost.getSystemProfileFacts(),
            hbiHost.getRhsmFacts(),
            facts.getProductTags(),
            facts.isHypervisor(),
            facts.isUnmappedGuest());

    return buildMinimalEvent(
            eventType,
            facts.getOrgId(),
            facts.getInventoryId(),
            facts.getInstanceId(),
            facts.getInsightsId(),
            eventTimestamp)
        .withInstanceId(facts.getInstanceId())
        .withSubscriptionManagerId(Optional.ofNullable(facts.getSubscriptionManagerId()))
        .withDisplayName(Optional.ofNullable(facts.getDisplayName()))
        .withSla(Objects.nonNull(facts.getSla()) ? Sla.fromValue(facts.getSla()) : null)
        .withUsage(Objects.nonNull(facts.getUsage()) ? Usage.fromValue(facts.getUsage()) : null)
        .withConversion(facts.is3rdPartyMigrated())
        .withHypervisorUuid(Optional.ofNullable(facts.getHypervisorUuid()))
        .withCloudProvider(facts.getCloudProvider())
        .withHardwareType(facts.getHardwareType())
        .withProductIds(facts.getProductIds().stream().toList())
        .withProductTag(facts.getProductTags())
        .withMeasurements(convertMeasurements(measurements))
        .withIsVirtual(facts.isVirtual())
        .withIsUnmappedGuest(facts.isUnmappedGuest())
        .withIsHypervisor(facts.isHypervisor())
        .withLastSeen(facts.getLastSeen());
  }

  protected Event buildMinimalEvent(
      NormalizedEventType eventType,
      String orgId,
      UUID inventoryId,
      String instanceId,
      String insightsId,
      OffsetDateTime timestamp) {
    OffsetDateTime eventTimestamp = clock.startOfHour(timestamp);
    return new Event()
        .withServiceType(EVENT_SERVICE_TYPE)
        .withEventSource(EVENT_SOURCE)
        .withEventType(eventType.toString())
        .withTimestamp(eventTimestamp)
        .withExpiration(Optional.of(eventTimestamp.plusHours(1)))
        .withOrgId(orgId)
        .withInventoryId(
            Optional.ofNullable(Objects.nonNull(inventoryId) ? inventoryId.toString() : null))
        .withInstanceId(instanceId)
        .withInsightsId(Optional.ofNullable(insightsId));
  }

  private List<Measurement> convertMeasurements(NormalizedMeasurements measurements) {
    List<Measurement> applicableMeasurements = new ArrayList<>();
    measurements
        .getCores()
        .ifPresent(
            cores ->
                applicableMeasurements.add(
                    new Measurement().withMetricId("cores").withValue(Double.valueOf(cores))));
    measurements
        .getSockets()
        .ifPresent(
            sockets ->
                applicableMeasurements.add(
                    new Measurement().withMetricId("sockets").withValue(Double.valueOf(sockets))));
    return applicableMeasurements;
  }
}
