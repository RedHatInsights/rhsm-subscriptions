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
package com.redhat.swatch.hbi.events.services;

import static com.redhat.swatch.hbi.events.configuration.Channels.HBI_HOST_EVENTS_IN;

import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.hbi.events.dtos.HostCreateUpdateEvent;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class HbiEventConsumer {

  @Incoming(HBI_HOST_EVENTS_IN)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  public void consume(HostCreateUpdateEvent hbiHostEvent, KafkaMessageMetadata<?> metadata) {
    log.info(
        "Received host event from HBI - [{}|{}]: {}",
        metadata.getTimestamp(),
        metadata.getKey(),
        hbiHostEvent);


var event = new Event();

    String serviceType;
    OffsetDateTime measuredTime;
    OffsetDateTime expired;
    String displayName;
    String sla;
    String billingAccountId;
    Object role;
    String orgId;
    String instanceId;
    Object serviceLevel;

    String productTag;
    event
        .withServiceType(serviceType)
        .withTimestamp(measuredTime)
        .withExpiration(Optional.of(expired))
        .withDisplayName(Optional.of(displayName))
        .withSla(getSla(serviceLevel, orgId, instanceId))
        .withUsage(getUsage(usage, orgId, instanceId))
        .withBillingProvider(getBillingProvider(billingProvider, orgId, instanceId, role))
        .withBillingAccountId(Optional.ofNullable(billingAccountId))
        .withMeasurements(
            List.of(
                new Measurement().withMetricId(measuredMetric.getValue()).withValue(measuredValue)))
        .withRole(getRole(role, orgId, instanceId))
        .withEventSource(eventSource)
        .withEventType(MeteringEventFactory.getEventType(measuredMetric.getValue(), productTag))
        .withOrgId(orgId)
        .withInstanceId(instanceId)
        .withMeteringBatchId(meteringBatchId)
        .withProductTag(Set.of(productTag))
        .withProductIds(productIds)
        .withConversion(is3rdPartyMigrated);
  }





  }
}
