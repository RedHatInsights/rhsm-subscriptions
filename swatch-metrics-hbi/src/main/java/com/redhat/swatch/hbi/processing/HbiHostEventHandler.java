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
package com.redhat.swatch.hbi.processing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.domain.HbiHostManager;
import com.redhat.swatch.hbi.domain.filtering.HostEventFilter;
import com.redhat.swatch.hbi.domain.normalization.FactNormalizer;
import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.dto.HbiHost;
import com.redhat.swatch.hbi.dto.HbiHostCreateUpdateEventDTO;
import com.redhat.swatch.hbi.egress.SwatchEventBuilder;
import com.redhat.swatch.hbi.exception.UnrecoverableMessageProcessingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;

@Slf4j
@ApplicationScoped
@Transactional
public class HbiHostEventHandler {

  private final FactNormalizer factNormalizer;
  private final HbiHostManager hbiHostManager;
  private final ObjectMapper objectMapper;
  private final HostEventFilter hbiEventFilter;
  private final SwatchEventBuilder swatchEventBuilder;

  @Inject
  public HbiHostEventHandler(
      FactNormalizer factNormalizer,
      HbiHostManager hbiHostManager,
      ObjectMapper objectMapper,
      HostEventFilter hbiEventFilter,
      SwatchEventBuilder swatchEventBuilder) {
    this.factNormalizer = factNormalizer;
    this.hbiHostManager = hbiHostManager;
    this.objectMapper = objectMapper;
    this.hbiEventFilter = hbiEventFilter;
    this.swatchEventBuilder = swatchEventBuilder;
  }

  public List<Event> handle(HbiHostCreateUpdateEventDTO hbiEvent) {
    Host host = new Host(hbiEvent.getHost());
    OffsetDateTime staleTimestamp = OffsetDateTime.parse(hbiEvent.getHost().getStaleTimestamp());

    if (hbiEventFilter.shouldSkip(host, staleTimestamp)) {
      log.debug("Filtered HBI host event: {}", hbiEvent);
      return List.of();
    }

    try {
      return translateHbiEvent(host, hbiEvent);
    } catch (UnrecoverableMessageProcessingException e) {
      log.warn("Unrecoverable HBI host event failure", e);
      return List.of();
    }
  }

  private List<Event> translateHbiEvent(Host host, HbiHostCreateUpdateEventDTO hbiEvent) {
    NormalizedFacts normalizedFacts = factNormalizer.normalize(host);
    OffsetDateTime timestamp =
        Optional.ofNullable(hbiEvent.getTimestamp()).orElse(ZonedDateTime.now()).toOffsetDateTime();

    try {
      hbiHostManager.processHost(normalizedFacts, objectMapper.writeValueAsString(hbiEvent));
    } catch (JsonProcessingException e) {
      throw new UnrecoverableMessageProcessingException("Failed to serialize HBI host payload", e);
    }

    List<Event> swatchEvents = new ArrayList<>();
    swatchEvents.add(
        swatchEventBuilder.build("CREATE_OR_UPDATE", normalizedFacts, host, timestamp));

    if (normalizedFacts.isHypervisor()) {
      hbiHostManager
          .findUnmappedGuests(
              normalizedFacts.getOrgId(), normalizedFacts.getSubscriptionManagerId())
          .stream()
          .map(guest -> buildRefreshedSwatchEvent("MAPPED_GUEST_UPDATE", guest, timestamp))
          .forEach(swatchEvents::add);
    } else if (normalizedFacts.isGuest() && !normalizedFacts.isUnmappedGuest()) {
      hbiHostManager
          .findHypervisorForGuest(normalizedFacts.getOrgId(), normalizedFacts.getHypervisorUuid())
          .map(hypervisor -> buildRefreshedSwatchEvent("HYPERVISOR_UPDATED", hypervisor, timestamp))
          .ifPresent(swatchEvents::add);
    }

    return swatchEvents;
  }

  private Event buildRefreshedSwatchEvent(
      String type,
      com.redhat.swatch.hbi.persistence.entity.HbiHost hostEntity,
      OffsetDateTime timestamp) {
    try {
      Host host = new Host(objectMapper.readValue(hostEntity.getHbiMessage(), HbiHost.class));
      NormalizedFacts facts = factNormalizer.normalize(host);
      return swatchEventBuilder.build(type, facts, host, timestamp);
    } catch (JsonProcessingException e) {
      throw new UnrecoverableMessageProcessingException(
          "Failed to deserialize refreshed host payload", e);
    }
  }
}
