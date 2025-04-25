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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.exception.UnrecoverableMessageProcessingException;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.NormalizedFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;

@Slf4j
@Singleton
public class CreateUpdateHostHandler extends HostEventHandler<HbiHostCreateUpdateEvent> {

  public CreateUpdateHostHandler(
      ApplicationConfiguration config,
      ApplicationClock clock,
      FactNormalizer factNormalizer,
      MeasurementNormalizer measurementNormalizer,
      HbiHostRelationshipService relationshipService,
      ObjectMapper objectMapper) {
    super(config, clock, factNormalizer, measurementNormalizer, relationshipService, objectMapper);
  }

  @Override
  public Class<HbiHostCreateUpdateEvent> getHbiEventClass() {
    return HbiHostCreateUpdateEvent.class;
  }

  @Override
  public List<Event> handleEvent(HbiHostCreateUpdateEvent hbiHostEvent) {
    log.debug("Handling HBI host created/updated event {}", hbiHostEvent);

    Host host = new Host(hbiHostEvent.getHost());

    OffsetDateTime eventTimestamp =
        Optional.ofNullable(hbiHostEvent.getTimestamp())
            .orElse(ZonedDateTime.now())
            .toOffsetDateTime();

    return updateHostRelationships(host, hbiHostEvent, eventTimestamp);
  }

  @Override
  public boolean skipEvent(HbiHostCreateUpdateEvent hbiHostEvent) {
    Host host = new Host(hbiHostEvent.getHost());

    // NOTE: Filtering based org will be done before swatch events are persisted on ingestion.
    String billingModel = host.getRhsmFacts().map(RhsmFacts::getBillingModel).orElse(null);
    boolean validBillingModel = Objects.isNull(billingModel) || !"marketplace".equals(billingModel);
    if (!validBillingModel) {
      log.warn(
          "Incoming HBI event will be skipped do to an invalid billing model: {}", billingModel);
      return true;
    }

    String hostType = host.getSystemProfileFacts().getHostType();
    boolean validHostType = Objects.isNull(hostType) || !"edge".equals(hostType);
    if (!validHostType) {
      log.warn("Incoming HBI event will be skipped do to an invalid host type: {}", hostType);
      return true;
    }

    OffsetDateTime staleTimestamp =
        OffsetDateTime.parse(hbiHostEvent.getHost().getStaleTimestamp());
    boolean isNotStale =
        clock.now().isBefore(staleTimestamp.plusDays(config.getCullingOffset().toDays()));
    if (!isNotStale) {
      log.warn("Incoming HBI event will be skipped because it is stale.");
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

  private Event updateHostRelationship(
      NormalizedFacts facts,
      Host targetHost,
      HbiHostCreateUpdateEvent hbiHostEvent,
      OffsetDateTime eventTimestamp) {
    try {
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
}
