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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;

@Slf4j
@ApplicationScoped
public class DeleteHostHandler implements HbiEventHandler<HbiHostDeleteEvent> {

  private final ApplicationClock clock;
  private final ApplicationConfiguration config;
  private final FactNormalizer factNormalizer;
  private final MeasurementNormalizer measurementNormalizer;
  private final HbiHostRelationshipService relationshipService;
  private final ObjectMapper objectMapper;

  public DeleteHostHandler(ApplicationConfiguration config,
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
  public Class<HbiHostDeleteEvent> getHbiEventClass() {
    return HbiHostDeleteEvent.class;
  }

  /**
   * Updates the host relationships for the deleted host and sends a host deleted event
   * to swatch notifying that this host was deleted.
   *
   * @param hbiEvent the incoming HbiHostDeleteEvent
   * @return a list of Swatch {@link Event}s representing the Host changes resulting from
   *         the deletion of the HBI host.
   */
  @Override
  public List<Event> handleEvent(HbiEvent hbiEvent) {
    log.debug("Handling hbi host delete event: {}", hbiEvent);
    HbiHostDeleteEvent deleteEvent = (HbiHostDeleteEvent) hbiEvent;
    relationshipService.deleteHostRelationship(deleteEvent.getOrgId(), deleteEvent.getId());
    return List.of();
  }

  @Override
  public boolean skipEvent(HbiEvent hbiEvent) {
    // All delete events will be processed and will be forwarded to Swatch.
    return false;
  }
}
