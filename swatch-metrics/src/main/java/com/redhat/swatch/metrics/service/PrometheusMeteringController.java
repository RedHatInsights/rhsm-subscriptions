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

import static com.redhat.swatch.metrics.util.MeteringEventFactory.createCleanUpEvent;

import com.redhat.swatch.clients.prometheus.api.model.QueryResultDataResultInner;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import com.redhat.swatch.metrics.exception.MeteringException;
import com.redhat.swatch.metrics.service.prometheus.PrometheusService;
import com.redhat.swatch.metrics.service.prometheus.model.QuerySummaryResult;
import com.redhat.swatch.metrics.service.promql.QueryBuilder;
import com.redhat.swatch.metrics.service.promql.QueryDescriptor;
import com.redhat.swatch.metrics.util.MeteringEventFactory;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.json.BaseEvent;
import org.candlepin.subscriptions.json.CleanUpEvent;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class PrometheusMeteringController {

  private static final String PROMETHEUS_QUERY_PARAM_INSTANCE_KEY = "instanceKey";

  private final PrometheusService prometheusService;
  private final Emitter<BaseEvent> emitter;
  private final ApplicationClock clock;
  private final MetricProperties metricProperties;
  private final SpanGenerator spanGenerator;
  private final QueryBuilder prometheusQueryBuilder;

  public PrometheusMeteringController(
      PrometheusService prometheusService,
      ApplicationClock clock,
      MetricProperties metricProperties,
      SpanGenerator spanGenerator,
      QueryBuilder prometheusQueryBuilder,
      @Channel("events-out") Emitter<BaseEvent> emitter) {
    this.prometheusService = prometheusService;
    this.clock = clock;
    this.metricProperties = metricProperties;
    this.spanGenerator = spanGenerator;
    this.prometheusQueryBuilder = prometheusQueryBuilder;
    this.emitter = emitter;
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
  @Retry
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
      Set<EventKey> eventsSent = new HashSet<>();
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

      log.info("Sent {} events for {} {} metrics.", eventsSent.size(), tag, metric);
      // Send event to delete any stale events found during the period
      sendCleanUpEvent(
          tag, orgId, metric, start.minusSeconds(metricProperties.step()), end, meteringBatchId);
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

  private void createEventFromDataAndSend(
      QueryResultDataResultInner item,
      Set<EventKey> eventsSent,
      String productTag,
      String orgId,
      UUID meteringBatchId,
      Metric tagMetric,
      SubscriptionDefinition subscriptionDefinition) {
    Map<String, String> labels = item.getMetric();
    String clusterId = labels.get(getPrometheusInstanceKeyFromMetric(productTag, tagMetric));
    String sla = labels.get("support");
    String usage = labels.get("usage");

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

    List<String> productIds = extractProductIdsFromProductLabel(product);
    if (!productIds.isEmpty()) {
      // Force lookup of productTag later to use productIds instead of role
      role = null;
    }

    String billingProvider = labels.get("billing_marketplace");
    String billingAccountId = labels.get("billing_marketplace_account");

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
              productIds);
      // Send if and only if it has not been sent yet.
      // Related to https://github.com/RedHatInsights/rhsm-subscriptions/pull/374.
      if (eventsSent.add(EventKey.fromEvent(event))) {
        sendToServiceInstanceTopic(event);
      }
    }
  }

  private void sendCleanUpEvent(
      String productTag,
      String orgId,
      Metric tagMetric,
      OffsetDateTime start,
      OffsetDateTime end,
      UUID meteringBatchId) {
    sendToServiceInstanceTopic(
        createCleanUpEvent(
            orgId,
            MeteringEventFactory.getEventType(tagMetric.getId(), productTag),
            metricProperties.eventSource(),
            start,
            end,
            meteringBatchId));
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
      List<String> productIds) {
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
        productIds);
    return event;
  }

  private void sendToServiceInstanceTopic(BaseEvent event) {
    if (event instanceof Event eventToSend) {
      log.debug(
          "Sending event with id {} for organization {}",
          eventToSend.getEventId(),
          eventToSend.getOrgId());
    } else if (event instanceof CleanUpEvent) {
      log.debug("Sending clean-up event for organization {}", event.getOrgId());
    }

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

  protected List<String> extractProductIdsFromProductLabel(String product) {
    List<String> productIds = new ArrayList<>();

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
      productIds = Arrays.asList(product.split(","));
    }
    return productIds;
  }
}
