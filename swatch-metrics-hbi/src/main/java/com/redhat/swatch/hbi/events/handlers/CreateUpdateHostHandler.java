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
package com.redhat.swatch.hbi.events.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.exception.UnrecoverableMessageProcessingException;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.events.normalization.NormalizedFacts;
import com.redhat.swatch.hbi.events.normalization.NormalizedMeasurements;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.services.HbiEventConsumer;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;

@Slf4j
@ApplicationScoped
public class CreateUpdateHostHandler implements HbiEventHandler<HbiHostCreateUpdateEvent> {

  private final ApplicationClock clock;
  private final ApplicationConfiguration config;
  private final FactNormalizer factNormalizer;
  private final MeasurementNormalizer measurementNormalizer;
  private final HbiHostRelationshipService relationshipService;
  private final ObjectMapper objectMapper;

  public CreateUpdateHostHandler(
      ApplicationConfiguration config,
      ApplicationClock clock,
      FactNormalizer factNormalizer,
      MeasurementNormalizer measurementNormalizer,
      HbiHostRelationshipService relationshipService,
      ObjectMapper objectMapper) {
    this.config = config;
    this.clock = clock;
    this.factNormalizer = factNormalizer;
    this.measurementNormalizer = measurementNormalizer;
    this.relationshipService = relationshipService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Class<HbiHostCreateUpdateEvent> getHbiEventClass() {
    return HbiHostCreateUpdateEvent.class;
  }

  @Override
  public List<Event> handleEvent(HbiEvent hbiEvent) {
    HbiHostCreateUpdateEvent hbiHostEvent = (HbiHostCreateUpdateEvent) hbiEvent;
    Host host = new Host(hbiHostEvent.getHost());

    OffsetDateTime eventTimestamp =
        Optional.ofNullable(hbiHostEvent.getTimestamp())
            .orElse(ZonedDateTime.now())
            .toOffsetDateTime();

    return updateHostRelationships(host, hbiHostEvent, eventTimestamp);
  }

  @Override
  public boolean skipEvent(HbiEvent hbiEvent) {
    HbiHostCreateUpdateEvent hbiHostEvent = (HbiHostCreateUpdateEvent) hbiEvent;
    Host host = new Host(hbiHostEvent.getHost());

    // NOTE: Filtering based org will be done before swatch events are persisted on ingestion.
    String billingModel = host.getRhsmFacts().map(RhsmFacts::getBillingModel).orElse(null);
    boolean validBillingModel = Objects.isNull(billingModel) || !"marketplace".equals(billingModel);
    if (!validBillingModel) {
      log.info(
          "Incoming HBI event will be skipped do to an invalid billing model: {}", billingModel);
      return true;
    }

    String hostType = host.getSystemProfileFacts().getHostType();
    boolean validHostType = Objects.isNull(hostType) || !"edge".equals(hostType);
    if (!validHostType) {
      log.info("Incoming HBI event will be skipped do to an invalid host type: {}", hostType);
      return true;
    }

    OffsetDateTime staleTimestamp =
        OffsetDateTime.parse(hbiHostEvent.getHost().getStaleTimestamp());
    boolean isNotStale =
        clock.now().isBefore(staleTimestamp.plusDays(config.getCullingOffset().toDays()));
    if (!isNotStale) {
      log.info("Incoming HBI event will be skipped because it is stale.");
      return true;
    }
    return false;
  }

  private List<Event> updateHostRelationships(
      Host targetHost, HbiHostCreateUpdateEvent originalHbiEvent, OffsetDateTime eventTimestamp) {
    NormalizedFacts facts = factNormalizer.normalize(targetHost);

    List<Event> toSend = new ArrayList<>();
    // Need to update the host relationship always.
    toSend.add(updateHostRelationship(facts, targetHost, originalHbiEvent, eventTimestamp));

    // Update the dependant relationships based on what type of host it is.
    if (facts.isHypervisor()) {
      toSend.addAll(updateHypervisorRelationships(facts, eventTimestamp));
    } else if (facts.isGuest() && !facts.isUnmappedGuest()) {
      updateGuestRelationships(facts, eventTimestamp).ifPresent(toSend::add);
    }
    return toSend;
  }

  private Optional<Event> updateGuestRelationships(
      NormalizedFacts guestFacts, OffsetDateTime eventTimestamp) {
    // Make sure that the guest's hypervisor data is up to date.
    Optional<HbiHostRelationship> hypervisor =
        relationshipService.getRelationship(guestFacts.getOrgId(), guestFacts.getHypervisorUuid());
    return hypervisor.map(h -> refreshHost("HYPERVISOR_UPDATED", h, eventTimestamp));
  }

  private List<Event> updateHypervisorRelationships(
      NormalizedFacts facts, OffsetDateTime eventTimestamp) {
    // Reprocess any unmapped guests and resend an event with updated measurements.
    return relationshipService
        .getUnmappedGuests(facts.getOrgId(), facts.getSubscriptionManagerId())
        .stream()
        .map(unmapped -> refreshGuest(unmapped, eventTimestamp))
        .toList();
  }

  /**
   * Refresh an unmapped guest's measurements and create a new Event representing these changes.
   *
   * @param unmapped the unmapped guests hypervisor relationship.
   * @return a new Event representing the host's new state.
   */
  private Event refreshGuest(HbiHostRelationship unmapped, OffsetDateTime hypervisorTimestamp) {
    return refreshHost("MAPPED_GUEST_UPDATE", unmapped, hypervisorTimestamp);
  }

  /**
   * Refresh a host's measurements and create a new Event representing these changes.
   *
   * @param hostRelationship the unmapped guests hypervisor relationship.
   * @return a new Event representing the host's new state.
   */
  private Event refreshHost(
      String eventType, HbiHostRelationship hostRelationship, OffsetDateTime refreshTimestamp) {
    try {
      Host host = new Host(objectMapper.readValue(hostRelationship.getFacts(), HbiHost.class));
      NormalizedFacts facts = factNormalizer.normalize(host);
      Event event = buildEvent(eventType, facts, host, refreshTimestamp);
      relationshipService.processHost(
          facts.getOrgId(),
          facts.getSubscriptionManagerId(),
          facts.getHypervisorUuid(),
          facts.isUnmappedGuest(),
          hostRelationship.getFacts());
      return event;
    } catch (JsonProcessingException e) {
      throw new UnrecoverableMessageProcessingException(
          "Unable to serialize host data from HBI host.", e);
    }
  }

  private Event updateHostRelationship(
      NormalizedFacts facts,
      Host targetHost,
      HbiHostCreateUpdateEvent hbiHostEvent,
      OffsetDateTime eventTimestamp) {
    try {
      relationshipService.processHost(
          facts.getOrgId(),
          facts.getSubscriptionManagerId(),
          facts.getHypervisorUuid(),
          facts.isUnmappedGuest(),
          objectMapper.writeValueAsString(hbiHostEvent.getHost()));
    } catch (JsonProcessingException e) {
      throw new UnrecoverableMessageProcessingException(
          "Unable to serialize host data from HBI host.", e);
    }

    return buildEvent(hbiHostEvent.getType().toUpperCase(), facts, targetHost, eventTimestamp);
  }

  private Event buildEvent(
      String eventType, NormalizedFacts facts, Host hbiHost, OffsetDateTime eventTimestamp) {

    NormalizedMeasurements measurements =
        measurementNormalizer.getMeasurements(
            facts,
            hbiHost.getSystemProfileFacts(),
            hbiHost.getRhsmFacts(),
            facts.getProductTags(),
            facts.isHypervisor(),
            facts.isUnmappedGuest());

    return new Event()
        .withServiceType(HbiEventConsumer.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConsumer.EVENT_SOURCE)
        .withEventType(String.format("HBI_HOST_%s", eventType.toUpperCase()))
        .withTimestamp(eventTimestamp)
        .withExpiration(Optional.of(eventTimestamp.plusHours(1)))
        .withOrgId(facts.getOrgId())
        .withInstanceId(facts.getInstanceId())
        .withInventoryId(Optional.ofNullable(facts.getInventoryId()))
        .withInsightsId(Optional.ofNullable(facts.getInsightsId()))
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
