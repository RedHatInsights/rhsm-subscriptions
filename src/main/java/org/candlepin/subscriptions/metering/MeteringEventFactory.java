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
package org.candlepin.subscriptions.metering;

import com.redhat.swatch.configuration.registry.MetricId;
import static org.candlepin.subscriptions.util.EventConstants.CLEAN_UP_EVENT_TYPE_PREFIX;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.BillingProvider;
import org.candlepin.subscriptions.json.Event.Role;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** Provides a means to create instances of various types of Event objects for various metrics. */
public class MeteringEventFactory {

  private static final Logger log = LoggerFactory.getLogger(MeteringEventFactory.class);

  public static final String EVENT_SOURCE = "prometheus";
  private static final String EVENT_TYPE = "snapshot";

  private MeteringEventFactory() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Creates an Event object that represents a cores snapshot for a given instance.
   *
   * @param accountNumber the account number.
   * @param instanceId the ID of the cluster that was measured.
   * @param serviceLevel the service level of the cluster.
   * @param usage the usage of the cluster.
   * @param role the role of the cluster.
   * @param measuredTime the time the measurement was taken.
   * @param expired the time the measurement had ended.
   * @param measuredValue the value that was measured./
   * @param productTag
   * @return a populated Event instance.
   */
  @SuppressWarnings("java:S107")
  public static Event createMetricEvent(
      String accountNumber,
      String orgId,
      String instanceId,
      String serviceLevel,
      String usage,
      String role,
      OffsetDateTime measuredTime,
      OffsetDateTime expired,
      String serviceType,
      String billingProvider,
      String billingAccountId,
      MetricId measuredMetric,
      Double measuredValue,
      String productTag) {
    Event event = new Event();
    updateMetricEvent(
        event,
        accountNumber,
        orgId,
        instanceId,
        serviceLevel,
        usage,
        role,
        measuredTime,
        expired,
        serviceType,
        billingProvider,
        billingAccountId,
        measuredMetric,
        measuredValue,
        productTag);
    return event;
  }

  /**
   * Creates an event with type "cleanup" which is a special type that triggers the deletion of
   * stale events.
   *
   * @param orgId the organization id.
   * @param eventType the event type.
   * @param cutOff the time when events are considered stale events.
   * @return a populated Event instance.
   */
  public static Event createCleanUpEvent(String orgId, String eventType, OffsetDateTime cutOff) {
    Event event = new Event();
    event.setOrgId(orgId);
    event.setEventSource(MeteringEventFactory.EVENT_SOURCE);
    event.setEventType(CLEAN_UP_EVENT_TYPE_PREFIX + eventType);
    event.setTimestamp(cutOff);
    return event;
  }

  @SuppressWarnings("java:S107")
  public static void updateMetricEvent(
      Event toUpdate,
      String accountNumber,
      String orgId,
      String instanceId,
      String serviceLevel,
      String usage,
      String role,
      OffsetDateTime measuredTime,
      OffsetDateTime expired,
      String serviceType,
      String billingProvider,
      String billingAccountId,
      MetricId measuredMetric,
      Double measuredValue,
      String productTag) {
    toUpdate
        .withEventSource(EVENT_SOURCE)
        .withEventType(MeteringEventFactory.getEventType(measuredMetric.getValue(), productTag))
        .withServiceType(serviceType)
        .withAccountNumber(accountNumber)
        .withOrgId(orgId)
        .withInstanceId(instanceId)
        .withTimestamp(measuredTime)
        .withExpiration(Optional.of(expired))
        .withDisplayName(Optional.of(instanceId))
        .withSla(getSla(serviceLevel, accountNumber, instanceId))
        .withUsage(getUsage(usage, accountNumber, instanceId))
        .withBillingProvider(getBillingProvider(billingProvider, accountNumber, instanceId))
        .withBillingAccountId(Optional.ofNullable(billingAccountId))
        .withMeasurements(
            List.of(new Measurement().withUom(measuredMetric.getValue()).withValue(measuredValue)))
        .withRole(getRole(role, accountNumber, instanceId));
  }

  public static String getEventType(String metricId, String productTag) {
    return StringUtils.hasText(metricId) && StringUtils.hasText(productTag)
        ? String.format("%s_%s_%s", EVENT_TYPE, productTag.toLowerCase(), metricId.toLowerCase())
        : EVENT_TYPE;
  }

  private static Sla getSla(String serviceLevel, String account, String clusterId) {
    /**
     * SLA values set by OCM: - Eval (ignored for now) - Standard - Premium - Self-Support - None
     * (converted to be __EMPTY__)
     */
    try {
      String sla = "None".equalsIgnoreCase(serviceLevel) ? "" : serviceLevel;
      return Sla.fromValue(StringUtils.trimWhitespace(sla));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported SLA '{}' specified for event. account/cluster: {}/{}",
          serviceLevel,
          account,
          clusterId);
    }
    return null;
  }

  private static Usage getUsage(String usage, String account, String clusterId) {
    if (usage == null) {
      return null;
    }

    try {
      return Usage.fromValue(StringUtils.trimWhitespace(usage));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported Usage '{}' specified for event. account/cluster: {}/{}",
          usage,
          account,
          clusterId);
    }
    return null;
  }

  private static Role getRole(String role, String account, String clusterId) {
    if (role == null) {
      return null;
    }

    try {
      return Role.fromValue(StringUtils.trimWhitespace(role));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported Role '{}' specified for event. account/cluster: {}/{}",
          role,
          account,
          clusterId);
    }
    return null;
  }

  private static BillingProvider getBillingProvider(
      String billingProvider, String account, String clusterId) {
    if (billingProvider == null
        || billingProvider.equals(BillingProvider.__EMPTY__.value())
        || "rhm".equalsIgnoreCase(billingProvider)) {
      return BillingProvider.RED_HAT;
    }

    try {
      return BillingProvider.fromValue(StringUtils.trimWhitespace(billingProvider));
    } catch (IllegalArgumentException e) {
      log.warn(
          "Unsupported BillingProvider '{}' specified for event. account/cluster: {}/{}",
          billingProvider,
          account,
          clusterId);
    }
    return null;
  }
}
