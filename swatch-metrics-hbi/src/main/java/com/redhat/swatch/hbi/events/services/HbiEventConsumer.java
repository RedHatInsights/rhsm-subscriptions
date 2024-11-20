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
import static com.redhat.swatch.hbi.events.configuration.Channels.SWATCH_EVENTS_OUT;

import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.FactNormalizer;
import com.redhat.swatch.hbi.events.normalization.NormalizedMeasurements;
import com.redhat.swatch.hbi.events.normalization.facts.HbiFactExtractor;
import com.redhat.swatch.hbi.events.normalization.facts.HostFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.kafka.EmitterService;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class HbiEventConsumer {

  private final FeatureFlags flags;

  @SuppressWarnings("java:S1068")
  private final EmitterService<Event> emitter;

  private final ApplicationClock clock;
  private final ApplicationConfiguration config;
  private final FactNormalizer factNormalizer;

  public HbiEventConsumer(
      @Channel(SWATCH_EVENTS_OUT) Emitter<Event> emitter,
      FeatureFlags flags,
      ApplicationClock clock,
      ApplicationConfiguration config,
      FactNormalizer factNormalizer) {
    this.emitter = new EmitterService<>(emitter);
    this.flags = flags;
    this.clock = clock;
    this.config = config;
    this.factNormalizer = factNormalizer;
  }

  @Incoming(HBI_HOST_EVENTS_IN)
  @RetryWithExponentialBackoff(
      maxRetries = "${SWATCH_EVENT_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${SWATCH_EVENT_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  public void consume(HbiEvent hbiEvent, KafkaMessageMetadata<?> metadata) {
    if (!(hbiEvent instanceof HbiHostCreateUpdateEvent)) {
      log.info("HBI Event not supported yet! Skipping: {}", hbiEvent.getType());
      return;
    }

    log.info("Received create/update host event from HBI - {}", hbiEvent);
    HbiHostCreateUpdateEvent hbiHostEvent = (HbiHostCreateUpdateEvent) hbiEvent;
    if (skipEvent(hbiHostEvent)) {
      log.debug("Event {} will be filtered.", hbiEvent);
      return;
    }

    HostFacts facts = factNormalizer.normalize(hbiHostEvent.getHost());
    var event =
        new Event()
            .withServiceType("HBI_HOST")
            .withEventSource("HBI_EVENT")
            .withEventType(String.format("HBI_HOST_%s", hbiHostEvent.getType().toUpperCase()))
            // TODO: Should the date be set to the modifiedOn date?
            //       Need to think about how this will translate to the "Host.lastSeenDate"
            //       when events are processed, and hosts are updated.
            .withTimestamp(clock.startOfCurrentHour())
            .withExpiration(Optional.of(clock.startOfCurrentHour().plusHours(1)))
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
            .withMeasurements(convertMeasurements(facts.getMeasurements()));

    // TODO OUTLIERS
    //        .withCorrelationIds()
    //        .withMeteringBatchId(meteringBatchId)
    //        .withAzureResourceId()
    //        .withAzureTenantId()
    //        .withAzureSubscriptionId()
    //        .withRole(getRole(role, orgId, instanceId))
    ;

    // NOTE: The following event fields are not provided by Inventory.
    //  * billingAccountId (defaulted to _ANY on tally)
    //  * billingProvider (defaulted to _ANY on tally)

    if (flags.emitEvents()) {
      log.info("Emitting HBI events to swatch!");
      emitter.send(Message.of(event));
    } else {
      log.info("Emitting HBI events to swatch is disabled. Not sending event.");
    }
  }

  private boolean skipEvent(HbiHostCreateUpdateEvent hbiHostEvent) {
    // NOTE: Filtering based org will be done before swatch events are persisted on ingestion.
    HbiFactExtractor extractor = new HbiFactExtractor(hbiHostEvent.getHost());

    Optional<RhsmFacts> rhsmFacts = extractor.getRhsmFacts();
    String billingModel = rhsmFacts.map(RhsmFacts::getBillingModel).orElse(null);
    boolean validBillingModel = Objects.isNull(billingModel) || !"marketplace".equals(billingModel);
    if (!validBillingModel) {
      log.info(
          "Incoming HBI event will be skipped do to an invalid billing model: {}", billingModel);
      return true;
    }

    String hostType = extractor.getSystemProfileFacts().getHostType();
    boolean validHostType = Objects.isNull(hostType) || !"edge".equals(hostType);
    if (!validHostType) {
      log.info("Incoming HBI event will be skipped do to an invalid host type: {}", hostType);
      return true;
    }

    OffsetDateTime staleTimestamp =
        OffsetDateTime.parse(hbiHostEvent.getHost().getStaleTimestamp());
    boolean isNotStale =
        clock.now().isBefore(staleTimestamp.plusDays(config.getCullingOffset().toDays()));
    if (!isNotStale) {
      log.info("Incoming HBI event will be skipped because it is stale.");
      return true;
    }
    return false;
  }

  private List<Measurement> convertMeasurements(NormalizedMeasurements measurements) {
    List<Measurement> applicableMeasurements = new ArrayList<>();
    measurements
        .getCores()
        .ifPresent(
            cores ->
                applicableMeasurements.add(
                    new Measurement().withMetricId("cores").withValue(Double.valueOf(cores))));
    measurements
        .getSockets()
        .ifPresent(
            sockets ->
                applicableMeasurements.add(
                    new Measurement().withMetricId("sockets").withValue(Double.valueOf(sockets))));
    return applicableMeasurements;
  }
}
