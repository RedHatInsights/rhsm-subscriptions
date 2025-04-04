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
package com.redhat.swatch.hbi.domain.filtering;

import com.redhat.swatch.hbi.config.ApplicationProperties;
import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;

@Slf4j
@ApplicationScoped
public class HostEventFilter {

  private final ApplicationClock clock;
  private final ApplicationProperties settings;

  @Inject
  public HostEventFilter(ApplicationClock clock, ApplicationProperties settings) {
    this.clock = clock;
    this.settings = settings;
  }

  public boolean shouldSkip(Host host, OffsetDateTime staleTimestamp) {
    return isMarketplaceBilling(host) || isEdgeHost(host) || isStale(staleTimestamp);
  }

  private boolean isMarketplaceBilling(Host host) {
    String billingModel = host.getRhsmFacts().map(RhsmFacts::getBillingModel).orElse(null);

    boolean skip = "marketplace".equalsIgnoreCase(billingModel);
    if (skip) {
      log.info("Skipping event due to billing model: {}", billingModel);
    }
    return skip;
  }

  private boolean isEdgeHost(Host host) {
    String hostType = host.getSystemProfileFacts().getHostType();
    boolean skip = "edge".equalsIgnoreCase(hostType);
    if (skip) {
      log.info("Skipping event due to host type: {}", hostType);
    }
    return skip;
  }

  private boolean isStale(OffsetDateTime staleTimestamp) {
    boolean stale = clock.now().isAfter(staleTimestamp.plus(settings.getCullingOffset()));
    if (stale) {
      log.info("Skipping event because it is stale: {}", staleTimestamp);
    }
    return stale;
  }
}
