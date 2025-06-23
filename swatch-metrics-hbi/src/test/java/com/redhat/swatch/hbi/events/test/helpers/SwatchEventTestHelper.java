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
package com.redhat.swatch.hbi.events.test.helpers;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.services.HbiEventConsumer;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;

@ApplicationScoped
public class SwatchEventTestHelper {

  private final ApplicationClock clock;

  public SwatchEventTestHelper(ApplicationClock clock) {
    this.clock = clock;
  }

  public Event buildPhysicalRhelEvent(
      HbiHost host,
      NormalizedEventType eventType,
      OffsetDateTime timestamp,
      boolean isHypervisor,
      List<Measurement> measurements) {
    return createExpectedEvent(
        host,
        eventType,
        timestamp,
        Sla.SELF_SUPPORT,
        Usage.DEVELOPMENT_TEST,
        HardwareType.PHYSICAL,
        false,
        false,
        isHypervisor,
        null,
        List.of("69"),
        Set.of("RHEL for x86"),
        measurements);
  }

  public Event createExpectedEvent(
      HbiHost host,
      NormalizedEventType eventType,
      OffsetDateTime timestamp,
      Sla sla,
      Usage usage,
      HardwareType hardwareType,
      boolean isVirtual,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      String hypervisorUuid,
      List<String> productIds,
      Set<String> tags,
      List<Measurement> measurements) {
    OffsetDateTime eventTimestamp = clock.startOfHour(timestamp);
    return new Event()
        .withServiceType(HbiEventConsumer.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConsumer.EVENT_SOURCE)
        .withEventType(eventType.toString())
        .withTimestamp(eventTimestamp)
        .withExpiration(Optional.of(eventTimestamp.plusHours(1)))
        .withLastSeen(OffsetDateTime.parse(host.getUpdated()))
        .withOrgId(host.getOrgId())
        .withInstanceId(host.getId().toString())
        .withInventoryId(Optional.of(host.id.toString()))
        .withInsightsId(Optional.ofNullable(host.insightsId))
        .withSubscriptionManagerId(Optional.of(host.subscriptionManagerId))
        .withDisplayName(Optional.ofNullable(host.displayName))
        .withSla(sla)
        .withUsage(usage)
        .withCloudProvider(null)
        .withHardwareType(hardwareType)
        .withHypervisorUuid(Optional.ofNullable(hypervisorUuid))
        .withProductTag(tags)
        .withProductIds(productIds)
        .withIsVirtual(isVirtual)
        .withIsUnmappedGuest(isUnmappedGuest)
        .withIsHypervisor(isHypervisor)
        .withMeasurements(measurements);
  }

  public List<Measurement> buildMeasurements(double cores, double sockets) {
    return List.of(
        new Measurement().withMetricId("cores").withValue(cores),
        new Measurement().withMetricId("sockets").withValue(sockets));
  }

  public Event createMinimalDeleteEvent(HbiHostDeleteEvent deleteEvent) {
    OffsetDateTime eventTimestamp =
        clock.startOfHour(deleteEvent.getTimestamp().toOffsetDateTime());
    String inventoryId =
        Objects.nonNull(deleteEvent.getId()) ? deleteEvent.getId().toString() : null;
    return new Event()
        .withServiceType(HbiEventConsumer.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConsumer.EVENT_SOURCE)
        .withEventType(NormalizedEventType.INSTANCE_DELETED.toString())
        .withTimestamp(eventTimestamp)
        .withExpiration(Optional.of(eventTimestamp.plusHours(1)))
        .withOrgId(deleteEvent.getOrgId())
        .withInventoryId(Optional.ofNullable(inventoryId))
        .withInstanceId(
            Objects.nonNull(deleteEvent.getId()) ? deleteEvent.getId().toString() : null)
        .withInsightsId(Optional.ofNullable(deleteEvent.getInsightsId()));
  }
}
