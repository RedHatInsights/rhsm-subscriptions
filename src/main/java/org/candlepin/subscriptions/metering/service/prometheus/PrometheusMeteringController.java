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

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import io.micrometer.core.annotation.Timed;
import jakarta.ws.rs.BadRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.metering.MeteringException;
import org.candlepin.subscriptions.metering.service.prometheus.model.QuerySummaryResult;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** A controller class that defines the business logic related to any metrics that are gathered. */
@Component
public class PrometheusMeteringController {

  private static final Logger log = LoggerFactory.getLogger(PrometheusMeteringController.class);
  private static final String PROMETHEUS_QUERY_PARAM_INSTANCE_KEY = "instanceKey";

  private final PrometheusService prometheusService;
  private final EventController eventController;
  private final ApplicationClock clock;
  private final MetricProperties metricProperties;
  private final RetryTemplate openshiftRetry;
  private final OptInController optInController;
  private final QueryBuilder prometheusQueryBuilder;

  @SuppressWarnings("java:S107")
  public PrometheusMeteringController(
      ApplicationClock clock,
      MetricProperties metricProperties,
      PrometheusService service,
      QueryBuilder queryBuilder,
      EventController eventController,
      @Qualifier("openshiftMetricRetryTemplate") RetryTemplate openshiftRetry,
      OptInController optInController) {
    this.clock = clock;
    this.metricProperties = metricProperties;
    this.prometheusService = service;
    this.prometheusQueryBuilder = queryBuilder;
    this.eventController = eventController;
    this.openshiftRetry = openshiftRetry;
    this.optInController = optInController;
  }

  // Suppressing this sonar issue because we need to log plus throw an exception on retry
  // otherwise we never know that we have failed during the retry cycle until all attempts
  // are exhausted.
  @SuppressWarnings("java:S2139")
  @Timed("rhsm-subscriptions.metering.openshift")
  @Transactional
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

            Map<EventKey, Event> events = new HashMap<>();
            Map<EventKey, Event> existing =
                eventController.mapEventsInTimeRange(
                    orgId,
                    MeteringEventFactory.EVENT_SOURCE,
                    MeteringEventFactory.getEventType(tagMetric.get().getId(), tag),
                    // We need to shift the start and end dates by the step, to account for the
                    // shift in the event start date when it is created. See note about eventDate
                    // below.
                    startDate.minusSeconds(metricProperties.getStep()),
                    end);

            log.debug(
                "Looking for events in range [{}, {})",
                startDate.minusSeconds(metricProperties.getStep()),
                end);
            log.debug("Found {} existing events.", existing.size());

            QuerySummaryResult metricData =
                prometheusService.runRangeQuery(
                    buildPromQLForMetering(orgId, tagMetric.get()),
                    startDate,
                    end,
                    metricProperties.getStep(),
                    metricProperties.getQueryTimeout(),
                    item -> {
                      Map<String, String> labels = item.getMetric();
                      String clusterId = labels.get(instanceKey);
                      String sla = labels.get("support");
                      String usage = labels.get("usage");

                      // These were added as an edge case with RHODS as it doesn't have product as a
                      // label in prometheus
                      String product = labels.get("product");
                      String resourceName = labels.get("resource_name");

                      // NOTE: Role comes from the product label despite its name. The values set
                      // here are NOT engineering or swatch product IDs. They map to the roles in
                      // the swatch-product-configuration library. For openshift, the values will
                      // be 'ocp' or 'osd'.
                      String role = product == null ? resourceName : product;
                      String billingProvider = labels.get("billing_marketplace");
                      String billingAccountId = labels.get("billing_marketplace_account");
                      String account = labels.get("ebs_account");

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
                        OffsetDateTime eventDate =
                            eventTermDate.minusSeconds(metricProperties.getStep());

                        Event event =
                            createOrUpdateEvent(
                                existing,
                                account,
                                orgId,
                                tagMetric.get().getId(),
                                clusterId,
                                sla,
                                usage,
                                role,
                                eventDate,
                                eventTermDate,
                                subDefOptional.get().getServiceType(),
                                billingProvider,
                                billingAccountId,
                                MetricId.fromString(tagMetric.get().getId()),
                                value,
                                tag);
                        events.putIfAbsent(EventKey.fromEvent(event), event);
                      }
                    });

            if (StatusType.ERROR.equals(metricData.getStatus())) {
              throw new MeteringException(
                  String.format(
                      "Unable to fetch %s %s %s metrics: %s",
                      tag, instanceKey, metric, metricData.getError()));
            }

            eventController.saveAll(events.values());
            log.info("Persisted {} events for {} {} metrics.", events.size(), tag, metric);

            // Delete any stale events found during the period.
            deleteStaleEvents(existing.values());
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
      Map<EventKey, Event> existing,
      String account,
      String orgId,
      String metricId,
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
      String productTag) {
    EventKey lookupKey =
        new EventKey(
            orgId,
            MeteringEventFactory.EVENT_SOURCE,
            MeteringEventFactory.getEventType(metricId, productTag), // NOSONAR
            instanceId,
            measuredDate);
    Event event = existing.remove(lookupKey);
    if (event == null) {
      event = new Event();
    }
    MeteringEventFactory.updateMetricEvent(
        event,
        account,
        orgId,
        instanceId,
        sla,
        usage,
        role,
        measuredDate,
        expired,
        serviceType,
        billingProvider,
        billingAccountId,
        metric,
        value.doubleValue(),
        productTag);
    return event;
  }

  private void deleteStaleEvents(Collection<Event> toDelete) {
    if (!toDelete.isEmpty()) {
      log.info("Deleting {} stale metric events.", toDelete.size());
      eventController.deleteEvents(toDelete);
    }
  }

  private String buildPromQLForMetering(String orgId, Metric tagMetric) {

    // Default the query template if the swatch-product-configuration library didn't specify one.
    if (Objects.nonNull(tagMetric.getPrometheus())
        && !StringUtils.hasText(tagMetric.getPrometheus().getQueryKey())) {
      tagMetric.getPrometheus().setQueryKey(QueryBuilder.DEFAULT_METRIC_QUERY_KEY);
    }

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
