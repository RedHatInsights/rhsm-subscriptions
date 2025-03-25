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
package com.redhat.swatch.hbi.events.services;

import static com.redhat.swatch.hbi.events.configuration.Channels.HBI_HOST_EVENTS_IN;
import static com.redhat.swatch.hbi.events.configuration.Channels.SWATCH_EVENTS_OUT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
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
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
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
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class HbiEventConsumer {

  public static final String EVENT_SERVICE_TYPE = "HBI_HOST";
  public static final String EVENT_SOURCE = "HBI_EVENT";
  private final FeatureFlags flags;

  @SuppressWarnings("java:S1068")
  private final EmitterService<Event> emitter;

  private final ApplicationClock clock;
  private final ApplicationConfiguration config;
  private final FactNormalizer factNormalizer;
  private final MeasurementNormalizer measurementNormalizer;
  private final HbiHostRelationshipService hbiHostRelationshipService;
  private final ObjectMapper objectMapper;

  public HbiEventConsumer(
      @Channel(SWATCH_EVENTS_OUT) Emitter<Event> emitter,
      FeatureFlags flags,
      ApplicationClock clock,
      ApplicationConfiguration config,
      FactNormalizer factNormalizer,
      MeasurementNormalizer measurementNormalizer,
      HbiHostRelationshipService hbiHostRelationshipService,
      ObjectMapper objectMapper) {
    this.emitter = new EmitterService<>(emitter);
    this.flags = flags;
    this.clock = clock;
    this.config = config;
    this.factNormalizer = factNormalizer;
    this.measurementNormalizer = measurementNormalizer;
    this.hbiHostRelationshipService = hbiHostRelationshipService;
    this.objectMapper = objectMapper;
  }

  @Incoming(HBI_HOST_EVENTS_IN)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  public void consume(HbiEvent hbiEvent, KafkaMessageMetadata<?> metadata) {
    if (!(hbiEvent instanceof HbiHostCreateUpdateEvent)) {
      log.info("HBI Event not supported yet! Skipping: {}", hbiEvent.getType());
      return;
    }

    log.info("Received create/update host event from HBI - {}", hbiEvent);
    HbiHostCreateUpdateEvent hbiHostEvent = (HbiHostCreateUpdateEvent) hbiEvent;

    Host host = new Host(hbiHostEvent.getHost());

    if (skipEvent(
        host.getRhsmFacts().map(RhsmFacts::getBillingModel).orElse(null),
        host.getSystemProfileFacts().getHostType(),
        OffsetDateTime.parse(hbiHostEvent.getHost().getStaleTimestamp()))) {
      log.debug("HBI event {} will be filtered.", hbiEvent);
      return;
    }

    OffsetDateTime eventTimestamp =
        Optional.ofNullable(hbiHostEvent.getTimestamp())
            .orElse(ZonedDateTime.now())
            .toOffsetDateTime();

    try {

      // Need to update the relationship always.
      List<Event> toSend = updateHostRelationships(host, hbiHostEvent, eventTimestamp);

      if (flags.emitEvents()) {
        log.info("Emitting {} HBI events to swatch!", toSend.size());
        toSend.forEach(eventToSend -> emitter.send(Message.of(eventToSend)));
      } else {
        log.info(
            "Emitting HBI events to swatch is disabled. Not sending {} events.", toSend.size());
        toSend.forEach(eventToSend -> log.info("EVENT: {}", eventToSend));
      }
    } catch (UnrecoverableMessageProcessingException e) {
      log.warn("Unrecoverable message when processing incoming HBI event.", e);
    }
  }

  @Transactional
  public List<Event> updateHostRelationships(
      Host targetHost, HbiHostCreateUpdateEvent originalHbiEvent, OffsetDateTime eventTimestamp) {
    NormalizedFacts facts = factNormalizer.normalize(targetHost);

    List<Event> toSend = new ArrayList<>();
    toSend.add(updateHostRelationship(facts, targetHost, originalHbiEvent, eventTimestamp));

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
        hbiHostRelationshipService.getRelationship(
            guestFacts.getOrgId(), guestFacts.getHypervisorUuid());
    return hypervisor.map(h -> refreshHost("HYPERVISOR_UPDATED", h, eventTimestamp));
  }

  private List<Event> updateHypervisorRelationships(
      NormalizedFacts facts, OffsetDateTime eventTimestamp) {
    // Reprocess any unmapped guests and resend an event with updated measurements.
    return hbiHostRelationshipService
        .getUnmappedGuests(facts.getOrgId(), facts.getSubscriptionManagerId())
        .stream()
        .map(unmapped -> refreshGuest(unmapped, eventTimestamp))
        .toList();
  }

  private Event updateHostRelationship(
      NormalizedFacts facts,
      Host targetHost,
      HbiHostCreateUpdateEvent hbiHostEvent,
      OffsetDateTime eventTimestamp) {
    try {
      hbiHostRelationshipService.processHost(
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
      hbiHostRelationshipService.processHost(
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

  private boolean skipEvent(String billingModel, String hostType, OffsetDateTime staleTimestamp) {
    // NOTE: Filtering based org will be done before swatch events are persisted on ingestion.
    boolean validBillingModel = Objects.isNull(billingModel) || !"marketplace".equals(billingModel);
    if (!validBillingModel) {
      log.info(
          "Incoming HBI event will be skipped do to an invalid billing model: {}", billingModel);
      return true;
    }

    boolean validHostType = Objects.isNull(hostType) || !"edge".equals(hostType);
    if (!validHostType) {
      log.info("Incoming HBI event will be skipped do to an invalid host type: {}", hostType);
      return true;
    }

    boolean isNotStale =
        clock.now().isBefore(staleTimestamp.plusDays(config.getCullingOffset().toDays()));
    if (!isNotStale) {
      log.info("Incoming HBI event will be skipped because it is stale.");
      return true;
    }
    return false;
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
        .withServiceType(EVENT_SERVICE_TYPE)
        .withEventSource(EVENT_SOURCE)
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
}
