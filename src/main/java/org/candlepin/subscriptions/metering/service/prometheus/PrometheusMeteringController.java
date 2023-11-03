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
package org.candlepin.subscriptions.metering.service.prometheus;

import static org.candlepin.subscriptions.metering.MeteringEventFactory.createCleanUpEvent;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.constraints.NotNull;
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
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.metering.service.prometheus.model.QuerySummaryResult;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.util.SpanGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** A controller class that defines the business logic related to any metrics that are gathered. */
@Component
public class PrometheusMeteringController {

  private static final Logger log = LoggerFactory.getLogger(PrometheusMeteringController.class);
  private static final String PROMETHEUS_QUERY_PARAM_INSTANCE_KEY = "instanceKey";

  private final PrometheusService prometheusService;
  private final PrometheusEventsProducer eventsProducer;
  private final ApplicationClock clock;
  private final MetricProperties metricProperties;
  private final RetryTemplate openshiftRetry;
  private final OptInController optInController;

  private final SpanGenerator spanGenerator;
  private final QueryBuilder prometheusQueryBuilder;

  @SuppressWarnings("java:S107")
  public PrometheusMeteringController(
      ApplicationClock clock,
      MetricProperties metricProperties,
      PrometheusService service,
      QueryBuilder queryBuilder,
      PrometheusEventsProducer eventsProducer,
      @Qualifier("openshiftMetricRetryTemplate") RetryTemplate openshiftRetry,
      OptInController optInController,
      @Qualifier("meteringBatchIdGenerator") SpanGenerator spanGenerator) {
    this.clock = clock;
    this.metricProperties = metricProperties;
    this.prometheusService = service;
    this.prometheusQueryBuilder = queryBuilder;
    this.eventsProducer = eventsProducer;
    this.openshiftRetry = openshiftRetry;
    this.optInController = optInController;
    this.spanGenerator = spanGenerator;
  }

  // Suppressing this sonar issue because we need to log plus throw an exception on retry
  // otherwise we never know that we have failed during the retry cycle until all attempts
  // are exhausted.
  @SuppressWarnings("java:S2139")
  @Timed("rhsm-subscriptions.metering.openshift")
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
    log.debug("Ensuring orgId={} has been set up for syncing/reporting.", orgId);
    ensureOptIn(orgId);
    openshiftRetry.execute(
        context -> {
          try {
            log.info("Collecting metrics for orgId={}: {} {}", orgId, tag, metric);
            Set<EventKey> eventsSent = new HashSet<>();
            QuerySummaryResult metricData =
                prometheusService.runRangeQuery(
                    buildPromQLForMetering(orgId, tagMetric.get()),
                    startDate,
                    end,
                    metricProperties.getStep(),
                    metricProperties.getQueryTimeout(),
                    item ->
                        sendEventFromData(
                            item,
                            eventsSent,
                            tag,
                            orgId,
                            meteringBatchId,
                            tagMetric.get(),
                            subDefOptional.get()));

            if (StatusType.ERROR.equals(metricData.getStatus())) {
              throw new MeteringException(
                  String.format(
                      "Unable to fetch %s %s %s metrics: %s",
                      tag, instanceKey, metric, metricData.getError()));
            }

            log.info("Sent {} events for {} {} metrics.", eventsSent.size(), tag, metric);
            // Send event to delete any stale events found during the period
            sendCleanUpEvent(
                tag,
                orgId,
                tagMetric.get(),
                startDate.minusSeconds(metricProperties.getStep()),
                end,
                meteringBatchId);

            return null;
          } catch (Exception e) {
            log.warn(
                "Exception thrown while updating {} {} {} metrics. [Attempt: {}]: {}",
                tag,
                instanceKey,
                metric,
                context.getRetryCount() + 1,
                e.getMessage());
            throw e;
          }
        });
  }

  private void sendEventFromData(
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

    // TODO update this note
    // NOTE: Role comes from the product label despite its name. The values set
    // here are NOT engineering or swatch product IDs. They map to the roles in
    // the swatch-product-configuration library. For openshift, the values will
    // be 'ocp' or 'osd'.
    String role = product == null ? resourceName : product;

    List<String> productIds = extractProductIdsFromProductLabel(product);

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
      OffsetDateTime eventDate = eventTermDate.minusSeconds(metricProperties.getStep());

      Event event =
          createOrUpdateEvent(
              orgId,
              productIds,
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
              meteringBatchId);
      // Send if and only if it has not been sent yet.
      // Related to https://github.com/RedHatInsights/rhsm-subscriptions/pull/374.
      if (eventsSent.add(EventKey.fromEvent(event))) {
        eventsProducer.produce(event);
      }
    }
  }

  @NotNull
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

  private void sendCleanUpEvent(
      String productTag,
      String orgId,
      Metric tagMetric,
      OffsetDateTime start,
      OffsetDateTime end,
      UUID meteringBatchId) {
    eventsProducer.produce(
        createCleanUpEvent(
            orgId,
            MeteringEventFactory.getEventType(tagMetric.getId(), productTag),
            metricProperties.getEventSource(),
            start,
            end,
            meteringBatchId));
  }

  private void ensureOptIn(String orgId) {
    try {
      optInController.optInByOrgId(orgId, OptInType.PROMETHEUS);
    } catch (Exception e) {
      log.warn("Error while attempting to automatically opt-in orgId={}", orgId);
      log.debug("Opt-in error for orgId=" + orgId, e);
    }
  }

  @SuppressWarnings("java:S107")
  private Event createOrUpdateEvent(
      String orgId,
      List<String> productIds,
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
      UUID meteringBatchId) {
    Event event = new Event();
    MeteringEventFactory.updateMetricEvent(
        event,
        orgId,
        instanceId,
        sla,
        usage,
        role,
        metricProperties.getEventSource(),
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

    if (!StringUtils.hasText(instanceKey)) {
      throw new IllegalArgumentException(
          String.format(
              "Could not find the `instanceKey` prometheus query param for %s-%s metric. ",
              productTag, metric.getId()));
    }
    return instanceKey;
  }
}
