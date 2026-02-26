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
package com.redhat.swatch.utilization.service;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.utilization.configuration.FeatureFlags;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service for detecting and reporting customer over-usage of subscriptions.
 *
 * <p>Monitors utilization summaries and increments a Prometheus counter when current usage exceeds
 * capacity by more than a configured threshold percentage. Only processes DAILY and HOURLY
 * granularity summaries.
 *
 * <p>Threshold configuration follows this priority: 1) Product-specific threshold from {@link
 * SubscriptionDefinition#getOverUsageThreshold} (configured in YAML files) 2) System default from
 * CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT property (defaults to 5%)
 *
 * <p>Setting a product's threshold to a negative value disables over-usage detection for that
 * product.
 *
 * <p>The service emits a counter metric {@code swatch_utilization_over_usage_total} tagged with:
 *
 * <ul>
 *   <li>product - Product ID
 *   <li>metric_id - Metric ID (e.g., Cores, Instance-hours)
 *   <li>billing - Billing provider (conditional, only if present)
 * </ul>
 */
@Slf4j
@ApplicationScoped
public class CustomerOverUsageService {

  public static final String OVER_USAGE_METRIC = "swatch_utilization_over_usage";
  private static final double FULL_CAPACITY_PERCENT = 100.0;
  private static final String PERCENT_FORMAT = "%.2f";
  private static final String BUNDLE = "subscription-services";
  private static final String APPLICATION = "subscriptions";
  private static final String EVENT_TYPE = "exceeded-utilization-threshold";
  private static final boolean NOTIFY_ONLY_ADMINS = false;
  private static final boolean IGNORE_USER_PREFERENCES = false;

  @Inject MeterRegistry meterRegistry;
  @Inject NotificationsProducer notificationsProducer;
  @Inject FeatureFlags featureFlags;

  @ConfigProperty(name = "CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT")
  Double defaultThresholdPercent;

  /**
   * Checks a utilization summary for over-usage and increments metrics if detected.
   *
   * <p>This method processes utilization events to detect when usage exceeds capacity by more than
   * the configured threshold. It only processes DAILY and HOURLY granularity events.
   *
   * @param payload the utilization summary to check
   */
  public void check(UtilizationSummary payload, Measurement measurement) {
    // Get threshold for this product (from product configuration or default)
    Double threshold = getThresholdForProduct(payload.getProductId());

    // Negative threshold disables over-usage detection for the product
    if (threshold < 0.0) {
      log.debug(
          "Skipping over-usage check for orgId={} productId={} due to negative threshold={}",
          payload.getOrgId(),
          payload.getProductId(),
          threshold);
      return;
    }

    // Check measurement for over-usage
    checkMeasurement(payload, measurement, threshold);
  }

  /**
   * Checks a single measurement for over-usage. Skips unlimited or invalid measurements.
   *
   * @param payload the utilization summary containing this measurement
   * @param measurement the measurement to check
   * @param threshold the threshold percentage for over-usage detection
   */
  private void checkMeasurement(
      UtilizationSummary payload, Measurement measurement, Double threshold) {
    // Skip unlimited capacity measurements
    if (Boolean.TRUE.equals(measurement.getUnlimited())) {
      log.debug(
          "Skipping over-usage check for unlimited capacity: orgId={} productId={} metricId={}",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId());
      return;
    }

    double currentTotal = measurement.getCurrentTotal();
    double capacity = measurement.getCapacity();

    // Skip invalid or zero capacity measurements
    if (capacity <= 0.0) {
      log.debug(
          "Skipping over-usage check for invalid capacity: orgId={} productId={} metricId={} capacity={}",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId(),
          capacity);
      return;
    }

    double utilizationPercent = calculateUtilizationPercent(currentTotal, capacity);
    double overagePercent = utilizationPercent - FULL_CAPACITY_PERCENT;

    // Over-usage occurs when usage exceeds capacity by more than the threshold
    if (overagePercent > threshold) {
      logOverUsageDetected(
          payload,
          measurement,
          currentTotal,
          capacity,
          utilizationPercent,
          overagePercent,
          threshold);
      MetricId metricId = MetricId.fromString(measurement.getMetricId());
      sendNotification(payload, metricId, utilizationPercent);
    } else {
      log.debug(
          "Usage within threshold: orgId={} productId={} metricId={} currentTotal={} capacity={} utilizationPercent={}% overagePercent={}% threshold={}%",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId(),
          currentTotal,
          capacity,
          String.format(PERCENT_FORMAT, utilizationPercent),
          String.format(PERCENT_FORMAT, overagePercent),
          String.format(PERCENT_FORMAT, threshold));
    }
  }

  private double calculateUtilizationPercent(double currentTotal, double capacity) {
    return (currentTotal / capacity) * FULL_CAPACITY_PERCENT;
  }

  private void logOverUsageDetected(
      UtilizationSummary payload,
      Measurement measurement,
      double currentTotal,
      double capacity,
      double utilizationPercent,
      double overagePercent,
      Double threshold) {
    log.info(
        "Over-usage detected: orgId={} productId={} metricId={} currentTotal={} capacity={} utilizationPercent={}% overagePercent={}% threshold={}%",
        payload.getOrgId(),
        payload.getProductId(),
        measurement.getMetricId(),
        currentTotal,
        capacity,
        String.format(PERCENT_FORMAT, utilizationPercent),
        String.format(PERCENT_FORMAT, overagePercent),
        String.format(PERCENT_FORMAT, threshold));
  }

  private void incrementOverUsageCounter(UtilizationSummary payload, MetricId metricId) {
    List<String> tags =
        new ArrayList<>(
            List.of("product", payload.getProductId(), "metric_id", metricId.getValue()));

    if (Objects.nonNull(payload.getBillingProvider())) {
      tags.addAll(List.of("billing", payload.getBillingProvider().value()));
    }

    meterRegistry.counter(OVER_USAGE_METRIC, tags.toArray(new String[0])).increment();
  }

  private void sendNotification(
      UtilizationSummary payload, MetricId metricId, double utilizationPercent) {
    if (!canSendNotification(payload.getOrgId())) {
      log.info(
          "Notification not sent for orgId={} productId={} metricId={} - feature flag '{}' is disabled and org is not whitelisted",
          payload.getOrgId(),
          payload.getProductId(),
          metricId,
          FeatureFlags.SEND_NOTIFICATIONS);
      return;
    }

    Action action = buildNotificationAction(payload, metricId, utilizationPercent);
    incrementOverUsageCounter(payload, metricId);
    notificationsProducer.produce(action);
  }

  private boolean canSendNotification(String orgId) {
    return featureFlags.sendNotifications() || featureFlags.isOrgWhitelistedForNotifications(orgId);
  }

  private Action buildNotificationAction(
      UtilizationSummary payload, MetricId metricId, double utilizationPercent) {
    var action = new Action();
    action.setBundle(BUNDLE);
    action.setApplication(APPLICATION);
    action.setEventType(EVENT_TYPE);
    action.setOrgId(payload.getOrgId());
    action.setTimestamp(LocalDateTime.now());
    action.setId(UUID.randomUUID());

    action.setEvents(List.of(buildEvent(utilizationPercent)));
    action.setContext(buildContext(payload, metricId));
    action.setRecipients(List.of(buildRecipient()));

    return action;
  }

  private Event buildEvent(double utilizationPercent) {
    var event = new Event();
    event.setMetadata(new Metadata());

    var eventPayload =
        new Payload.PayloadBuilder()
            .withAdditionalProperty(
                "utilization_percentage", String.format(PERCENT_FORMAT, utilizationPercent))
            .build();
    event.setPayload(eventPayload);

    return event;
  }

  private Context buildContext(UtilizationSummary payload, MetricId metricId) {
    return new Context.ContextBuilder()
        .withAdditionalProperty("product_id", payload.getProductId())
        .withAdditionalProperty("metric_id", metricId.getValue())
        .build();
  }

  private Recipient buildRecipient() {
    var recipient = new Recipient();
    recipient.setOnlyAdmins(NOTIFY_ONLY_ADMINS);
    recipient.setIgnoreUserPreferences(IGNORE_USER_PREFERENCES);
    recipient.setUsers(List.of());
    return recipient;
  }

  /**
   * Get the over-usage threshold for a product. Checks product configuration first, then falls back
   * to default threshold.
   *
   * @param productId the product ID
   * @return threshold percentage
   */
  private Double getThresholdForProduct(String productId) {
    // First check if product configuration defines a threshold
    Double productThreshold = SubscriptionDefinition.getOverUsageThreshold(productId);
    if (productThreshold != null) {
      return productThreshold;
    }
    // Fall back to default threshold
    return defaultThresholdPercent;
  }
}
