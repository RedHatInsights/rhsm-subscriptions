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
package com.redhat.swatch.hbi.events.normalization;

import static com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts.RHSM_FACTS_NAMESPACE;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * Extracts HBI facts from an {@link com.redhat.swatch.hbi.events.dtos.hbi.HbiHost} and maps then to
 * appropriate swatch fact objects.
 */
public class Host {

  @Getter private final String orgId;
  @Getter private final UUID inventoryId;
  @Getter private final String subscriptionManagerId;
  @Getter private final String insightsId;
  @Getter private final String displayName;
  @Getter private final String providerId;
  @Getter private final String updatedDate;
  @Getter private final String staleTimestamp;
  @Getter private final SystemProfileFacts systemProfileFacts;

  private final RhsmFacts rhsmFacts;

  public Host(HbiHost host) {
    this.orgId = host.getOrgId();
    this.inventoryId = host.getId();
    this.subscriptionManagerId = host.getSubscriptionManagerId();
    this.insightsId = host.getInsightsId();
    this.displayName = host.getDisplayName();
    this.providerId = host.getProviderId();
    this.updatedDate = host.getUpdated();
    this.staleTimestamp = host.getStaleTimestamp();

    Map<String, HbiHostFacts> facts = getFacts(host);
    rhsmFacts =
        facts.containsKey(RHSM_FACTS_NAMESPACE)
            ? new RhsmFacts(facts.get(RHSM_FACTS_NAMESPACE))
            : null;

    systemProfileFacts = new SystemProfileFacts(host);
  }

  public Optional<RhsmFacts> getRhsmFacts() {
    return Optional.ofNullable(rhsmFacts);
  }

  private Map<String, HbiHostFacts> getFacts(HbiHost host) {
    return host.getFacts().stream()
        .collect(Collectors.toMap(HbiHostFacts::getNamespace, Function.identity()));
  }
}
