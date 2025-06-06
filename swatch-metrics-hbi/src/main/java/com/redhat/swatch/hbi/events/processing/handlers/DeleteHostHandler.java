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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.exception.UnrecoverableMessageProcessingException;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.NormalizedFacts;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import jakarta.inject.Singleton;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;

@Slf4j
@Singleton
public class DeleteHostHandler extends HostEventHandler<HbiHostDeleteEvent> {

  public static final String INSTANCE_DELETED_EVENT_TYPE = "INSTANCE_DELETED";

  public DeleteHostHandler(
      ApplicationConfiguration config,
      ApplicationClock clock,
      FactNormalizer factNormalizer,
      MeasurementNormalizer measurementNormalizer,
      HbiHostRelationshipService relationshipService,
      ObjectMapper objectMapper) {
    super(config, clock, factNormalizer, measurementNormalizer, relationshipService, objectMapper);
  }

  @Override
  public Class<HbiHostDeleteEvent> getHbiEventClass() {
    return HbiHostDeleteEvent.class;
  }

  /**
   * Updates the host relationships for the deleted host and sends a host deleted event to swatch
   * notifying that this host was deleted.
   *
   * @param deleteEvent the incoming HbiHostDeleteEvent
   * @return a list of Swatch {@link Event}s representing the Host changes resulting from the
   *     deletion of the HBI host.
   */
  @Override
  public List<Event> handleEvent(HbiHostDeleteEvent deleteEvent) {
    log.debug("Handling hbi host delete event: {}", deleteEvent);
    Optional<HbiHostRelationship> deleted =
        relationshipService.deleteHostRelationship(deleteEvent.getOrgId(), deleteEvent.getId());

    List<Event> hostChangeEvents = new ArrayList<>();

    if (deleted.isPresent()) {
      OffsetDateTime eventDate = deleteEvent.getTimestamp().toOffsetDateTime();
      // Add an event to notify that the host instance has been deleted.
      hostChangeEvents.add(createDeleteHostEvent(deleted.get(), eventDate));

      if (StringUtils.isBlank(deleted.get().getHypervisorUuid())) {
        // Update any mapped guest relationships that are still associated with this hypervisor
        // and may not yet be deleted.
        hostChangeEvents.addAll(
            updateMappedGuestRelationships(
                deleted.get().getOrgId(), deleted.get().getSubscriptionManagerId(), eventDate));
      } else {
        // Update the associated hypervisor if it exists.
        updateHypervisorRelationship(
                deleted.get().getOrgId(), deleted.get().getHypervisorUuid(), eventDate)
            .ifPresent(hostChangeEvents::add);
      }
    } else {
      // Because there was no existing relationship for this instance (never seen by this service),
      // we send a swatch delete event with whatever host data we know from the event. There will
      // be no hypervisor/guests updates required because the host was not seen by the service
      // and therefor has no impact.
      log.debug(
          "A host relationship did not exist for this HBI delete event. Sending minimal event to swatch.");
      hostChangeEvents.add(createMinimalDeleteEvent(deleteEvent));
    }
    return hostChangeEvents;
  }

  @Override
  public boolean skipEvent(HbiHostDeleteEvent deleteEvent) {
    // All delete events will be processed and will be forwarded to Swatch.
    return false;
  }

  private Event createDeleteHostEvent(
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

  private Event createMinimalDeleteEvent(HbiHostDeleteEvent deleteEvent) {
    return buildMinimalEvent(
        NormalizedEventType.INSTANCE_DELETED,
        deleteEvent.getOrgId(),
        deleteEvent.getId(),
        deleteEvent.getId().toString(),
        deleteEvent.getInsightsId(),
        deleteEvent.getTimestamp().toOffsetDateTime());
  }
}
