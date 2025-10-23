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

import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.model.Host;
import com.redhat.swatch.hbi.events.normalization.model.facts.RhsmFacts;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;

@Slf4j
@ApplicationScoped
public class CreateUpdateHostHandler implements HbiEventHandler<HbiHostCreateUpdateEvent> {

  private final HostEventHandlerService handlerService;
  private final ApplicationClock clock;
  private final ApplicationConfiguration config;

  public CreateUpdateHostHandler(
      HostEventHandlerService handlerService,
      ApplicationClock clock,
      ApplicationConfiguration config) {
    this.handlerService = handlerService;
    this.clock = clock;
    this.config = config;
  }

  @Override
  public Class<HbiHostCreateUpdateEvent> getHbiEventClass() {
    return HbiHostCreateUpdateEvent.class;
  }

  @Override
  public List<Event> handleEvent(HbiHostCreateUpdateEvent hbiHostEvent) {
    log.debug("Handling HBI host created/updated event {}", hbiHostEvent);

    Host host = new Host(hbiHostEvent.getHost());
    if (skipEvent(host)) {
      return List.of();
    }

    OffsetDateTime eventTimestamp =
        Optional.ofNullable(hbiHostEvent.getTimestamp())
            .orElse(ZonedDateTime.now())
            .toOffsetDateTime();

    return handlerService.updateHostRelationshipAndAllDependants(
        host, hbiHostEvent, eventTimestamp);
  }

  private boolean skipEvent(Host host) {
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

    if (isStale(host.getStaleTimestamp())) {
      log.warn("Incoming HBI event will be skipped because it is stale.");
      return true;
    }
    return false;
  }

  private boolean isStale(String hbiHostStaleTimestamp) {
    if (StringUtils.isBlank(hbiHostStaleTimestamp)) {
      return false;
    }
    OffsetDateTime staleTimestamp = OffsetDateTime.parse(hbiHostStaleTimestamp);
    return clock.now().isAfter(staleTimestamp.plusDays(config.getCullingOffset().toDays()));
  }
}
