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
package com.redhat.swatch.metrics.service;

import com.redhat.swatch.clients.prometheus.api.model.QueryResultDataResultInner;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.ProductTagLookupParams;
import com.redhat.swatch.kafka.EmitterService;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import com.redhat.swatch.metrics.exception.MeteringException;
import com.redhat.swatch.metrics.service.prometheus.PrometheusService;
import com.redhat.swatch.metrics.service.prometheus.model.QuerySummaryResult;
import com.redhat.swatch.metrics.service.promql.QueryBuilder;
import com.redhat.swatch.metrics.service.promql.QueryDescriptor;
import com.redhat.swatch.metrics.util.MeteringEventFactory;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class PrometheusMeteringController {

  private static final String PROMETHEUS_QUERY_PARAM_INSTANCE_KEY = "instanceKey";
  private static final String PRODUCT_TAG = "productTag";

  private final PrometheusService prometheusService;
  private final EmitterService<Event> emitter;
  private final ApplicationClock clock;
  private final MetricProperties metricProperties;
  private final SpanGenerator spanGenerator;
  private final QueryBuilder prometheusQueryBuilder;
  private final MeterRegistry registry;

  public PrometheusMeteringController(
      PrometheusService prometheusService,
      ApplicationClock clock,
      MetricProperties metricProperties,
      SpanGenerator spanGenerator,
      QueryBuilder prometheusQueryBuilder,
      MeterRegistry registry,
      @Channel("events-out") Emitter<Event> emitter) {
    this.prometheusService = prometheusService;
    this.clock = clock;
    this.metricProperties = metricProperties;
    this.spanGenerator = spanGenerator;
    this.prometheusQueryBuilder = prometheusQueryBuilder;
    this.registry = registry;
    this.emitter = new EmitterService<>(emitter);
  }

  public void collectMetrics(
      String tag, MetricId metric, String orgId, OffsetDateTime start, OffsetDateTime end) {
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(tag);
    if (subDefOptional.isEmpty()) {
      throw new BadRequestException(String.format("Invalid product tag specified: %s", tag));
    }
    Optional<Metric> tagMetric =
        subDefOptional.flatMap(subDef -> subDef.getMetric(metric.getValue()));
    if (tagMetric.isEmpty()) {
      throw new UnsupportedOperationException(
          String.format("Unable to find tag %s and metric %s!", tag, metric));
    }

    if (!Objects.nonNull(subDefOptional.get().getServiceType())) {
      throw new UnsupportedOperationException(
          String.format("Unable to determine service type for tag %s.", tag));
    }

    // Span ID is used to identify all the events that have been triggered by the same process.
    // This is specially useful to allow deleting stale events with a different span ID.
    UUID meteringBatchId = spanGenerator.generate();

    // Get the instance key from the prometheus query params.
    String instanceKey = getPrometheusInstanceKeyFromMetric(tag, tagMetric.get());

    /* Adjust the range for the prometheus range query API. Range query returns a data point at the
    startDate, and then an additional data point for each increment of `step` that is <= endDate.
    Because our prometheus queries use a range vector (look back) of 1h, we need to add an hour to
    the start of our range to get datapoints which represents the time range we're gathering data
    for. We also ensure that the start of the range is on an hourly boundary (defensive programming
    - it should already be)
     */
    OffsetDateTime startDate = clock.startOfHour(start).plusHours(1);
    collectMetricsForRange(
        tag,
        orgId,
        tagMetric.get(),
        subDefOptional.get(),
        meteringBatchId,
        instanceKey,
        startDate,
        end,
        new AtomicInteger(1));
  }

  @SuppressWarnings("java:S107")
  @Retry(maxDuration = 120, durationUnit = ChronoUnit.SECONDS)
  public void collectMetricsForRange(
      String tag,
      String orgId,
      Metric metric,
      SubscriptionDefinition subDef,
      UUID meteringBatchId,
      String instanceKey,
      OffsetDateTime start,
      OffsetDateTime end,
      AtomicInteger counter) {
    try {
      log.info("Collecting metrics for orgId={}: {} {}", orgId, tag, metric);
      Sample sample = Timer.start(registry);
      AtomicInteger eventsSent = new AtomicInteger(0);
      QuerySummaryResult metricData =
          prometheusService.runRangeQuery(
              buildPromQLForMetering(orgId, metric),
              start,
              end,
              metricProperties.step(),
              metricProperties.queryTimeout(),
              item ->
                  createEventFromDataAndSend(
                      item, eventsSent, tag, orgId, meteringBatchId, metric, subDef));

      if (StatusType.ERROR.equals(metricData.getStatus())) {
        throw new MeteringException(
            String.format(
                "Unable to fetch %s %s %s metrics: %s",
                tag, instanceKey, metric, metricData.getError()));
      }

      updateMetrics(tag, sample, metricData, eventsSent);

      log.info("Sent {} events for {} {} metrics.", eventsSent.get(), tag, metric);
    } catch (Exception e) {
      log.warn(
          "Exception thrown while updating {} {} {} metrics. [Attempt: {}]: {}",
          tag,
          instanceKey,
          metric,
          counter.incrementAndGet(),
          e.getMessage());
      throw e;
    }
  }

  private void updateMetrics(
      String tag, Sample sample, QuerySummaryResult metricData, AtomicInteger eventsSent) {
    sample.stop(
        registry.timer(
            "metrics.collection.timer",
            PRODUCT_TAG,
            tag,
            "status",
            metricData.getStatus().toString()));
    Gauge.builder("metrics.events.count", eventsSent::get)
        .baseUnit(BaseUnits.EVENTS)
        .tags(PRODUCT_TAG, tag)
        .register(registry);
  }

  private void createEventFromDataAndSend(
      QueryResultDataResultInner item,
      AtomicInteger eventsSent,
      String productTag,
      String orgId,
      UUID meteringBatchId,
      Metric tagMetric,
      SubscriptionDefinition subscriptionDefinition) {
    Map<String, String> labels = item.getMetric();
    String clusterId = labels.get(getPrometheusInstanceKeyFromMetric(productTag, tagMetric));
    String sla = labels.get("support");
    String usage = labels.get("usage");
    String displayName = Optional.ofNullable(labels.get("display_name")).orElse(clusterId);

    // These were added as an edge case with RHODS as it doesn't have product as a
    // label in prometheus
    String product = labels.get("product");
    String resourceName = labels.get("resource_name");

    // NOTE: With data sourced from openshift telemeter instance, role comes from the product label
    // despite its name. The values set here are NOT engineering or swatch product IDs. They map to
    // the roles in the swatch-product-configuration library. For openshift, the values will be
    // 'ocp' or 'osd'. For rhel data sourced from rhelemeter, role isn't applicable and the  product
    // label contains engineering ids used to map to a product tag in swatch-product-configuration
    // library.
    String role = product == null ? resourceName : product;

    Set<Integer> productIds = extractProductIdsFromProductLabel(product);
    if (!productIds.isEmpty()) {
      // Force lookup of productTag later to use productIds instead of role
      role = null;
    }

    boolean is3rdPartyMigrated = Boolean.parseBoolean(labels.get("conversions_success"));

    // Since the promql is shared by all tags in a configuration file, conversions_success isn't
    // filtered on during promql.  This means that we end up running the same promql twice and would
    // end up with duplicate events for a system.  EventReconciliation on ingestion will handle
    // this, but we don't want to extraneously store and process events for performance reasons.  If
    // conversions_success doesn't match the isMigrated attribute for the tag whose context we're
    // currently working in, skip creating an event.

    if (Boolean.TRUE.equals(Variant.findByTag(productTag).get().getIsMigrationProduct())
        != is3rdPartyMigrated) {

      var msg =
          """
          Ignoring extraneous data returned from promql.  Skipping event creation.
          product tag we're collecting data for={}. Data from metric productIds={}, role={}, isMigrated {}
          """;

      log.debug(msg, productTag, productIds, role, is3rdPartyMigrated);
      return;
    }

    var matchingTags =
        SubscriptionDefinition.getAllProductTags(
            ProductTagLookupParams.builder()
                .engIds(productIds)
                .role(role)
                .metricIds(Set.of(tagMetric.getId()))
                .isPaygEligibleProduct(true)
                .is3rdPartyMigration(is3rdPartyMigrated)
                .build());

    if (!matchingTags.contains(productTag)) {
      log.warn(
          "Starting product tag {} does not match derived product tags {} based on data contents.",
          productTag,
          matchingTags);

      if (matchingTags.isEmpty()) {
        role = null; // No match at all, clear role for fallback logic
      } else {
        log.warn("Mismatched product tags. Clearing productTag to trigger reconciliation.");
        productTag = null; // Clear to allow ingestion to rederive correct context
      }
    }

    log.info("Creating Event for product {}", productTag);

    String billingProvider = labels.get("billing_marketplace");
    String billingAccountId;

    // extract azure IDs
    String azureSubscriptionId = labels.get("azure_subscription_id");
    if (StringUtils.isNotEmpty(azureSubscriptionId)) {
      billingAccountId = azureSubscriptionId;
    } else {
      billingAccountId = labels.get("billing_marketplace_account");
    }

    // For the openshift metrics, we expect our results to be a 'matrix'
    // vector [(instant_time,value), ...] so we only look at the result's
    // getValues() data.
    for (List<BigDecimal> measurement : item.getValues()) {
      BigDecimal time = measurement.get(0);
      BigDecimal value = measurement.get(1);

      OffsetDateTime eventTermDate = clock.dateFromUnix(time);
      // Need to subtract the step because we are averaging and the metric value
      // actually represents the end of the measured period. The start of the
      // event should be at the beginning.
      OffsetDateTime eventDate = eventTermDate.minusSeconds(metricProperties.step());

      Event event =
          createOrUpdateEvent(
              orgId,
              clusterId,
              sla,
              usage,
              role,
              eventDate,
              eventTermDate,
              subscriptionDefinition.getServiceType(),
              billingProvider,
              billingAccountId,
              MetricId.fromString(tagMetric.getId()),
              value,
              productTag,
              meteringBatchId,
              productIds.stream().map(String::valueOf).collect(Collectors.toList()),
              displayName,
              is3rdPartyMigrated);

      eventsSent.getAndIncrement();
      sendToServiceInstanceTopic(event);
    }
  }

  @SuppressWarnings("java:S107")
  private Event createOrUpdateEvent(
      String orgId,
      String instanceId,
      String sla,
      String usage,
      String role,
      OffsetDateTime measuredDate,
      OffsetDateTime expired,
      String serviceType,
      String billingProvider,
      String billingAccountId,
      MetricId metric,
      BigDecimal value,
      String productTag,
      UUID meteringBatchId,
      List<String> productIds,
      String displayName,
      boolean is3rdPartyMigrated) {
    Event event = new Event();
    MeteringEventFactory.updateMetricEvent(
        event,
        orgId,
        instanceId,
        sla,
        usage,
        role,
        metricProperties.eventSource(),
        measuredDate,
        expired,
        serviceType,
        billingProvider,
        billingAccountId,
        metric,
        value.doubleValue(),
        productTag,
        meteringBatchId,
        productIds,
        displayName,
        is3rdPartyMigrated);
    return event;
  }

  private void sendToServiceInstanceTopic(Event event) {
    log.debug("Sending event with id {} for organization {}", event.getEventId(), event.getOrgId());

    OutgoingKafkaRecordMetadata<?> metadata =
        OutgoingKafkaRecordMetadata.builder().withKey(event.getOrgId()).build();
    emitter.send(Message.of(event).addMetadata(metadata));
  }

  private String buildPromQLForMetering(String orgId, Metric tagMetric) {
    QueryDescriptor descriptor = new QueryDescriptor(tagMetric);
    descriptor.addRuntimeVar("orgId", orgId);
    return prometheusQueryBuilder.build(descriptor);
  }

  private String getPrometheusInstanceKeyFromMetric(String productTag, Metric metric) {
    String instanceKey = null;
    if (metric.getPrometheus() != null && metric.getPrometheus().getQueryParams() != null) {
      instanceKey =
          metric.getPrometheus().getQueryParams().get(PROMETHEUS_QUERY_PARAM_INSTANCE_KEY);
    }

    if (StringUtils.isEmpty(instanceKey)) {
      throw new IllegalArgumentException(
          String.format(
              "Could not find the `instanceKey` prometheus query param for %s-%s metric. ",
              productTag, metric.getId()));
    }
    return instanceKey;
  }

  protected Set<Integer> extractProductIdsFromProductLabel(String product) {
    Set<Integer> productIds = new HashSet<>();

    /*
      The regular expression pattern matches a sequence of numbers separated by commas with optional spaces in between.

      Explanation:
      ^          : Asserts the start of the line.
      \d+        : Matches one or more digits (0-9).
      (          : Starts a group that contains the following sequence:
        ,        : Matches a comma.
        \s*      : Matches zero or more whitespace characters.
        \d+      : Matches one or more digits (0-9).
      )*+        : The whole group can repeat zero or more times (including the comma followed by digits),
                   and the possessive quantifier (*+) prevents excessive backtracking.

      Example:
      - 123,456,789: Matches a sequence of numbers separated by commas and optional spaces: 123, 456, 789.
      - 1,23,4:     Matches a sequence of numbers separated by commas and optional spaces: 1, 23, 4.
      - 100:        Matches a single number: 100.

      Note: This pattern may lead to catastrophic backtracking for extremely large inputs due to its repetitive nature.
    */

    String pattern = "^\\d+(,\\s*\\d+)*+";

    boolean isEngIdList = product != null && product.matches(pattern);

    if (isEngIdList) {
      productIds =
          Arrays.stream(product.split(",")).map(Integer::parseInt).collect(Collectors.toSet());
    }
    return productIds;
  }
}
