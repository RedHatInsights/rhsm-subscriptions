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
package com.redhat.swatch.hbi.egress;

import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.domain.normalization.NormalizedMeasurements;
import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;

@ApplicationScoped
public class SwatchEventBuilder {

  private static final String EVENT_SERVICE_TYPE = "HBI_HOST";
  private static final String EVENT_SOURCE = "HBI_EVENT";
  private static final String EVENT_TYPE_PREFIX = "HBI_HOST_";

  private final MeasurementNormalizer measurementNormalizer;

  @Inject
  public SwatchEventBuilder(MeasurementNormalizer measurementNormalizer) {
    this.measurementNormalizer = measurementNormalizer;
  }

  public Event build(
      String eventCategory, NormalizedFacts facts, Host host, OffsetDateTime timestamp) {
    NormalizedMeasurements measurements =
        measurementNormalizer.getMeasurements(
            facts,
            host.getSystemProfileFacts(),
            host.getRhsmFacts(),
            facts.getProductTags(),
            facts.isHypervisor(),
            facts.isUnmappedGuest());

    return convertFactsToEvent(eventCategory, facts, timestamp, measurements);
  }

  private Event convertFactsToEvent(
      String eventCategory,
      NormalizedFacts facts,
      OffsetDateTime timestamp,
      NormalizedMeasurements measurements) {
    return new Event()
        .withServiceType(EVENT_SERVICE_TYPE)
        .withEventSource(EVENT_SOURCE)
        .withEventType(EVENT_TYPE_PREFIX + eventCategory.toUpperCase())
        .withTimestamp(timestamp)
        .withExpiration(Optional.of(timestamp.plusHours(1)))
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
        .withMeasurements(toSwatchMeasurements(measurements))
        .withIsVirtual(facts.isVirtual())
        .withIsUnmappedGuest(facts.isUnmappedGuest())
        .withIsHypervisor(facts.isHypervisor())
        .withLastSeen(facts.getLastSeen());
  }

  private List<Measurement> toSwatchMeasurements(NormalizedMeasurements measurements) {
    List<Measurement> result = new ArrayList<>();
    measurements
        .getCores()
        .ifPresent(
            c -> result.add(new Measurement().withMetricId("cores").withValue(c.doubleValue())));
    measurements
        .getSockets()
        .ifPresent(
            s -> result.add(new Measurement().withMetricId("sockets").withValue(s.doubleValue())));
    return result;
  }
}
