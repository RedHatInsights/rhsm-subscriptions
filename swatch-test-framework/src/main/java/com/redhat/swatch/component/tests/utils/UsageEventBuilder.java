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
package com.redhat.swatch.component.tests.utils;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.TargetHost;
import org.candlepin.subscriptions.json.UsageEvent;

public class UsageEventBuilder {
  private String orgId;
  private String serviceType;
  private String instanceId;
  private String displayName;
  private OffsetDateTime timestamp;
  private String productTag;
  private Measurement measurement;

  private UsageEventBuilder() {}

  public static UsageEventBuilder createUsageEvent() {
    UsageEventBuilder builder = new UsageEventBuilder();
    builder.orgId = RandomUtils.generateRandom();
    builder.serviceType = "RHEL System";
    builder.instanceId = UUID.randomUUID().toString();
    builder.timestamp = OffsetDateTime.now();
    return builder;
  }

  public UsageEventBuilder withOrgId(String orgId) {
    this.orgId = orgId;
    return this;
  }

  public UsageEventBuilder withServiceType(String serviceType) {
    this.serviceType = serviceType;
    return this;
  }

  public UsageEventBuilder withInstanceId(String instanceId) {
    this.instanceId = instanceId;
    return this;
  }

  public UsageEventBuilder withDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  public UsageEventBuilder withTimestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public UsageEventBuilder withProductTag(String productTag) {
    this.productTag = productTag;
    return this;
  }

  public UsageEventBuilder withMeasurement(String metricId, double value) {
    Measurement measurement = new Measurement();
    measurement.setMetricId(metricId);
    measurement.setValue(value);
    this.measurement = measurement;
    return this;
  }

  public UsageEventBuilder withMeasurement(Measurement measurement) {
    this.measurement = measurement;
    return this;
  }

  public UsageEvent build() {
    UsageEvent event = new UsageEvent();
    event.setOrgId(orgId);
    event.setServiceType(serviceType);

    TargetHost targetHost = new TargetHost();
    targetHost.setInstanceId(instanceId);
    if (displayName != null) {
      targetHost.setDisplayName(displayName);
    }
    event.setTargetHost(targetHost);

    event.setTimestamp(timestamp);
    if (productTag != null) {
      event.setProductTag(productTag);
    }
    if (measurement != null) {
      event.setMeasurement(measurement);
    }
    return event;
  }
}
