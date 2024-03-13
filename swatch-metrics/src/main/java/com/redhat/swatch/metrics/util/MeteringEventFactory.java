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
package com.redhat.swatch.metrics.util;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.subscriptions.json.CleanUpEvent;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.BillingProvider;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides a means to create instances of various types of Event objects for various metrics. */
public final class MeteringEventFactory {

  private static final Logger log = LoggerFactory.getLogger(MeteringEventFactory.class);
  private static final String EVENT_TYPE = "snapshot";

  private MeteringEventFactory() {}

  /**
   * Creates an Event object that represents a cores snapshot for a given instance.
   *
   * @param instanceId the ID of the cluster that was measured.
   * @param serviceLevel the service level of the cluster.
   * @param usage the usage of the cluster.
   * @param role the role of the cluster.
   * @param measuredTime the time the measurement was taken.
   * @param expired the time the measurement had ended.
   * @param measuredValue the value that was measured.
   * @param productTag the product tag.
   * @param displayName the display name.
   * @return a populated Event instance.
   */
  @SuppressWarnings("java:S107")
  public static Event createMetricEvent(
      String orgId,
      String instanceId,
      String serviceLevel,
      String usage,
      String role,
      String eventSource,
      OffsetDateTime measuredTime,
      OffsetDateTime expired,
      String serviceType,
      String billingProvider,
      String billingAccountId,
      MetricId measuredMetric,
      Double measuredValue,
      String productTag,
      UUID meteringBatchId,
      List<String> productIds,
      String displayName) {
    Event event = new Event();
    updateMetricEvent(
        event,
        orgId,
        instanceId,
        serviceLevel,
        usage,
        role,
        eventSource,
        measuredTime,
        expired,
        serviceType,
        billingProvider,
        billingAccountId,
        measuredMetric,
        measuredValue,
        productTag,
        meteringBatchId,
        productIds,
        displayName);
    return event;
  }

  /**
   * Creates an event with type "cleanup" which is a special type that triggers the deletion of
   * stale events.
   *
   * @param orgId the organization id.
   * @param eventType the event type.
   * @param eventSource the event source.
   * @param start the start time window.
   * @param end the end time window.
   * @param meteringBatchId Metering batch ID that identifies which process generated the event.
   * @return a populated Event instance.
   */
  public static CleanUpEvent createCleanUpEvent(
      String orgId,
      String eventType,
      String eventSource,
      OffsetDateTime start,
      OffsetDateTime end,
      UUID meteringBatchId) {
    CleanUpEvent event = new CleanUpEvent();
    event.setOrgId(orgId);
    event.setEventSource(eventSource);
    event.setEventType(eventType);
    event.setMeteringBatchId(meteringBatchId);
    event.setStart(start);
    event.setEnd(end);
    return event;
  }

  @SuppressWarnings("java:S107")
  public static void updateMetricEvent(
      Event toUpdate,
      String orgId,
      String instanceId,
      String serviceLevel,
      String usage,
      String role,
      String eventSource,
      OffsetDateTime measuredTime,
      OffsetDateTime expired,
      String serviceType,
      String billingProvider,
      String billingAccountId,
      MetricId measuredMetric,
      Double measuredValue,
      String productTag,
      UUID meteringBatchId,
      List<String> productIds,
      String displayName) {
    toUpdate
        .withServiceType(serviceType)
        .withTimestamp(measuredTime)
        .withExpiration(Optional.of(expired))
        .withDisplayName(Optional.of(displayName))
        .withSla(getSla(serviceLevel, orgId, instanceId))
        .withUsage(getUsage(usage, orgId, instanceId))
        .withBillingProvider(getBillingProvider(billingProvider, orgId, instanceId))
        .withBillingAccountId(Optional.ofNullable(billingAccountId))
        .withMeasurements(
            List.of(
                new Measurement()
                    .withUom(measuredMetric.getValue())
                    .withMetricId(measuredMetric.getValue())
                    .withValue(measuredValue)))
        .withRole(getRole(role, orgId, instanceId))
        .withEventSource(eventSource)
        .withEventType(MeteringEventFactory.getEventType(measuredMetric.getValue(), productTag))
        .withOrgId(orgId)
        .withInstanceId(instanceId)
        .withMeteringBatchId(meteringBatchId)
        .withProductTag(Set.of(productTag))
        .withProductIds(productIds);
  }

  public static String getEventType(String metricId, String productTag) {
    return StringUtils.isNotEmpty(metricId) && StringUtils.isNotEmpty(productTag)
        ? String.format("%s_%s_%s", EVENT_TYPE, productTag.toLowerCase(), metricId.toLowerCase())
        : EVENT_TYPE;
  }

  private static Sla getSla(String serviceLevel, String orgId, String clusterId) {
    /**
     * SLA values set by OCM: - Eval (ignored for now) - Standard - Premium - Self-Support - None
     * (converted to be __EMPTY__)
     */
    try {
      String sla = "None".equalsIgnoreCase(serviceLevel) ? "" : serviceLevel;
      return Sla.fromValue(StringUtils.strip(sla));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported SLA '{}' specified for event. orgId/cluster: {}/{}",
          serviceLevel,
          orgId,
          clusterId);
    }
    return null;
  }

  private static Usage getUsage(String usage, String orgId, String clusterId) {
    if (usage == null) {
      return null;
    }

    try {
      return Usage.fromValue(StringUtils.strip(usage));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported Usage '{}' specified for event. orgId/cluster: {}/{}",
          usage,
          orgId,
          clusterId);
    }
    return null;
  }

  private static Role getRole(String role, String orgId, String clusterId) {
    if (role == null) {
      return null;
    }

    try {
      return Role.fromValue(StringUtils.strip(role));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported Role '{}' specified for event. orgId/cluster: {}/{}",
          role,
          orgId,
          clusterId);
    }
    return null;
  }

  private static BillingProvider getBillingProvider(
      String billingProvider, String orgId, String clusterId) {
    if (billingProvider == null
        || billingProvider.equals(BillingProvider.__EMPTY__.value())
        || "rhm".equalsIgnoreCase(billingProvider)) {
      return BillingProvider.RED_HAT;
    }

    try {
      return BillingProvider.fromValue(StringUtils.strip(billingProvider));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported BillingProvider '{}' specified for event. orgId/cluster: {}/{}",
          billingProvider,
          orgId,
          clusterId);
    }
    return null;
  }
}
