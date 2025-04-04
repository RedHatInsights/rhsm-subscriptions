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
package com.redhat.swatch.hbi.domain.normalization;

import static com.redhat.swatch.hbi.domain.normalization.facts.QpcFacts.QPC_FACTS_NAMESPACE;
import static com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts.RHSM_FACTS_NAMESPACE;
import static com.redhat.swatch.hbi.domain.normalization.facts.SatelliteFacts.SATELLITE_FACTS_NAMESPACE;

import com.redhat.swatch.hbi.domain.normalization.facts.QpcFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.SatelliteFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.dto.HbiHost;
import com.redhat.swatch.hbi.dto.HbiHostFacts;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;

/** Extracts HBI facts from an {@link HbiHost} and maps then to appropriate swatch fact objects. */
@AllArgsConstructor
@Builder
public class Host {

  private final HbiHost hbiHost;
  private final RhsmFacts rhsmFacts;
  private final SatelliteFacts satelliteFacts;
  private final QpcFacts qpcFacts;
  private final SystemProfileFacts systemProfileFacts;

  public Host(HbiHost host) {
    this.hbiHost = host;
    Map<String, HbiHostFacts> facts = getFacts(host);
    rhsmFacts =
        facts.containsKey(RHSM_FACTS_NAMESPACE)
            ? new RhsmFacts(facts.get(RHSM_FACTS_NAMESPACE))
            : null;

    satelliteFacts =
        facts.containsKey(SATELLITE_FACTS_NAMESPACE)
            ? new SatelliteFacts(facts.get(SATELLITE_FACTS_NAMESPACE))
            : null;

    qpcFacts =
        facts.containsKey(QPC_FACTS_NAMESPACE)
            ? new QpcFacts(facts.get(QPC_FACTS_NAMESPACE))
            : null;

    systemProfileFacts = new SystemProfileFacts(host);
  }

  public HbiHost getHbiHost() {
    return hbiHost;
  }

  public Optional<RhsmFacts> getRhsmFacts() {
    return Optional.ofNullable(rhsmFacts);
  }

  public Optional<SatelliteFacts> getSatelliteFacts() {
    return Optional.ofNullable(satelliteFacts);
  }

  public Optional<QpcFacts> getQpcFacts() {
    return Optional.ofNullable(qpcFacts);
  }

  public SystemProfileFacts getSystemProfileFacts() {
    return systemProfileFacts;
  }

  private Map<String, HbiHostFacts> getFacts(HbiHost host) {
    return host.getFacts().stream()
        .collect(Collectors.toMap(HbiHostFacts::getNamespace, Function.identity()));
  }
}
