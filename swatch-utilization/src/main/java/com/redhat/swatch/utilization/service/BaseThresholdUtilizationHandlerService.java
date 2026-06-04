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

import static java.util.Optional.ofNullable;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.MetricType;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.utilization.model.Measurement;
import com.redhat.swatch.utilization.model.Severity;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import com.redhat.swatch.utilization.model.UtilizationSummary.BillingProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
public abstract class BaseThresholdUtilizationHandlerService implements UtilizationHandlerService {

  public static final String DIMENSION_ANY = "_ANY";

  static final double FULL_CAPACITY_PERCENT = 100.0;
  static final String PERCENT_FORMAT = "%.2f";

  private static final String BUNDLE = "subscription-services";
  private static final String APPLICATION = "subscriptions";
  private static final boolean NOTIFY_ONLY_ADMINS = false;
  private static final boolean IGNORE_USER_PREFERENCES = false;

  @Inject NotificationsProducer notificationsProducer;
  @Inject MeterRegistry meterRegistry;

  @ConfigProperty(name = "CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT")
  Double defaultOverUsageThresholdPercent;

  public boolean handle(UtilizationSummary payload, Measurement measurement) {
    var utilizationOpt = computeUtilizationPercent(payload, measurement);
    if (utilizationOpt.isEmpty()) {
      return false;
    }
    double utilizationPercent = utilizationOpt.getAsDouble();
    var eventOpt = evaluateThreshold(utilizationPercent, payload, measurement);
    if (eventOpt.isPresent()) {
      MetricId metricId = MetricId.fromString(measurement.getMetricId());
      sendNotification(payload, metricId, eventType(), severity(), eventOpt.get(), metricName());
      return true;
    }
    return false;
  }

  protected abstract Optional<HandlerEvent> evaluateThreshold(
      double utilizationPercent, UtilizationSummary payload, Measurement measurement);

  protected abstract String eventType();

  protected abstract Severity severity();

  protected abstract String metricName();

  protected HandlerEvent buildEvent(double utilizationPercent) {
    var event = new HandlerEvent();
    event.addPayloadProperty(
        "utilization_percentage", String.format(PERCENT_FORMAT, utilizationPercent));
    return event;
  }

  protected OptionalDouble computeUtilizationPercent(
      UtilizationSummary payload, Measurement measurement) {
    if (Boolean.TRUE.equals(measurement.getUnlimited())) {
      log.debug(
          "Skipping threshold check for unlimited capacity: orgId={} productId={} metricId={} sla={} usage={}",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId(),
          payload.getSla(),
          payload.getUsage());
      return OptionalDouble.empty();
    }

    double effectiveUsage = resolveEffectiveUsage(payload.getProductId(), measurement);
    double capacity = measurement.getCapacity();

    if (capacity <= 0.0) {
      log.debug(
          "Skipping threshold check for invalid capacity: orgId={} productId={} metricId={} capacity={}",
          payload.getOrgId(),
          payload.getProductId(),
          measurement.getMetricId(),
          capacity);
      return OptionalDouble.empty();
    }

    return OptionalDouble.of((effectiveUsage / capacity) * FULL_CAPACITY_PERCENT);
  }

  private double resolveEffectiveUsage(String productId, Measurement measurement) {
    MetricType metricType =
        SubscriptionDefinition.lookupSubscriptionByTag(productId)
            .flatMap(sub -> sub.getMetric(measurement.getMetricId()))
            .map(Metric::getType)
            .orElse(MetricType.GAUGE);

    if (metricType == MetricType.COUNTER) {
      return measurement.getCurrentTotal();
    }
    return measurement.getValue();
  }

  protected double getOverUsageThresholdForProduct(String productId) {
    Double productThreshold = SubscriptionDefinition.getOverUsageThreshold(productId);
    if (productThreshold != null) {
      return productThreshold;
    }
    return defaultOverUsageThresholdPercent;
  }

  private Action buildNotificationAction(
      UtilizationSummary payload,
      MetricId metricId,
      String eventType,
      Severity severity,
      HandlerEvent event) {
    var action = new Action();
    action.setBundle(BUNDLE);
    action.setApplication(APPLICATION);
    action.setEventType(eventType);
    action.setOrgId(payload.getOrgId());
    action.setTimestamp(LocalDateTime.now());
    action.setId(UUID.randomUUID());
    action.setEvents(List.of(buildNotificationEvent(event)));
    action.setContext(buildContext(payload, metricId, event));
    action.setRecipients(List.of(buildRecipient()));
    action.setSeverity(severity.name());
    return action;
  }

  private Event buildNotificationEvent(HandlerEvent handlerEvent) {
    Event event = new Event();
    event.setMetadata(handlerEvent.metadata);

    Payload.PayloadBuilder builder = new Payload.PayloadBuilder();
    for (Map.Entry<String, String> entry : handlerEvent.payload.entrySet()) {
      builder.withAdditionalProperty(entry.getKey(), entry.getValue());
    }

    event.setPayload(builder.build());
    return event;
  }

  private Context buildContext(UtilizationSummary payload, MetricId metricId, HandlerEvent event) {
    Context.ContextBuilder builder =
        (Context.ContextBuilder)
            new Context.ContextBuilder()
                .withAdditionalProperty("product_id", payload.getProductId())
                .withAdditionalProperty("metric_id", metricId.getValue());

    if (isServiceLevelSet(payload.getSla())) {
      builder.withAdditionalProperty("service_level", payload.getSla().value());
    }
    if (isUsageSet(payload.getUsage())) {
      builder.withAdditionalProperty("usage", payload.getUsage().value());
    }
    if (payload.getBillingAccountId() != null && !payload.getBillingAccountId().isEmpty()) {
      builder.withAdditionalProperty("billing_account_id", payload.getBillingAccountId());
    }

    for (Map.Entry<String, String> entry : event.context.entrySet()) {
      builder.withAdditionalProperty(entry.getKey(), entry.getValue());
    }

    return builder.build();
  }

  protected void sendNotification(
      UtilizationSummary payload,
      MetricId metricId,
      String eventType,
      Severity severity,
      HandlerEvent event,
      String metricName) {
    var action = buildNotificationAction(payload, metricId, eventType, severity, event);
    incrementCounter(payload, metricId, metricName);
    notificationsProducer.produce(action);
  }

  private void incrementCounter(UtilizationSummary payload, MetricId metricId, String metricName) {
    Counter.builder(metricName)
        .withRegistry(meterRegistry)
        .withTags(
            "product",
            payload.getProductId(),
            "metric_id",
            metricId.getValue(),
            "billing",
            ofNullable(payload.getBillingProvider()).map(BillingProvider::value).orElse(""),
            "sla",
            metricSlaLabelValue(payload.getSla()),
            "usage",
            metricUsageLabelValue(payload.getUsage()))
        .increment();
  }

  static String metricSlaLabelValue(UtilizationSummary.Sla sla) {
    return isServiceLevelSet(sla) ? sla.value() : DIMENSION_ANY;
  }

  static String metricUsageLabelValue(UtilizationSummary.Usage usage) {
    return isUsageSet(usage) ? usage.value() : DIMENSION_ANY;
  }

  static boolean isServiceLevelSet(UtilizationSummary.Sla sla) {
    return sla != null
        && sla != UtilizationSummary.Sla.__EMPTY__
        && sla != UtilizationSummary.Sla.ANY;
  }

  static boolean isUsageSet(UtilizationSummary.Usage usage) {
    return usage != null
        && usage != UtilizationSummary.Usage.__EMPTY__
        && usage != UtilizationSummary.Usage.ANY;
  }

  private Recipient buildRecipient() {
    var recipient = new Recipient();
    recipient.setOnlyAdmins(NOTIFY_ONLY_ADMINS);
    recipient.setIgnoreUserPreferences(IGNORE_USER_PREFERENCES);
    recipient.setUsers(List.of());
    return recipient;
  }

  protected static class HandlerEvent {
    private final Metadata metadata = new Metadata();
    private final Map<String, String> payload = new HashMap<>();
    private final Map<String, String> context = new HashMap<>();

    public void addPayloadProperty(String key, String value) {
      payload.put(key, value);
    }

    public void addContextProperty(String key, String value) {
      context.put(key, value);
    }
  }
}
