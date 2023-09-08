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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.BillingProvider;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.util.MetricIdUtils;
import org.junit.jupiter.api.Test;

class MeteringEventFactoryTest {

  private final String productTag = "OpenShift-dedicated-metrics";

  @Test
  void testOpenShiftClusterCoresEventCreation() throws Exception {
    String account = "my-account";
    String orgId = "my-org";
    String clusterId = "my-cluster";
    String sla = "Premium";
    String usage = "Production";
    String role = "ocp";
    String serviceType = "cluster-service-type";
    String billingProvider = "red hat";
    String spanId = "123";
    OffsetDateTime expiry = OffsetDateTime.now();
    OffsetDateTime measuredTime = expiry.minusHours(1);
    Double measuredValue = 23.0;
    MetricId uom = MetricIdUtils.getCores();

    Event event =
        MeteringEventFactory.createMetricEvent(
            account,
            orgId,
            clusterId,
            sla,
            usage,
            role,
            measuredTime,
            expiry,
            serviceType,
            billingProvider,
            null,
            uom,
            measuredValue,
            productTag,
            spanId);

    assertEquals(account, event.getAccountNumber());
    assertEquals(orgId, event.getOrgId());
    assertEquals(measuredTime, event.getTimestamp());
    assertEquals(expiry, event.getExpiration().get());
    assertEquals(clusterId, event.getInstanceId());
    assertEquals(Optional.of(clusterId), event.getDisplayName());
    assertEquals(Sla.PREMIUM, event.getSla());
    assertEquals(Usage.PRODUCTION, event.getUsage());
    assertEquals(MeteringEventFactory.EVENT_SOURCE, event.getEventSource());
    assertEquals(
        MeteringEventFactory.getEventType(uom.getValue(), productTag), event.getEventType());
    assertEquals(serviceType, event.getServiceType());
    assertEquals(spanId, event.getSpanId());
    assertEquals(1, event.getMeasurements().size());
    Measurement measurement = event.getMeasurements().get(0);
    assertEquals(uom.toString(), measurement.getUom());
    assertEquals(measuredValue, measurement.getValue());
  }

  @Test
  void testOpenShiftClusterCoresHandlesNullServiceLevel() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            null,
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getSla());
  }

  @Test
  void testOpenShiftClusterCoresSlaSetToEmptyForSlaValueNone() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "None",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            "null",
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertEquals(Sla.__EMPTY__, event.getSla());
  }

  @Test
  void testOpenShiftClusterCoresInvalidSlaWillNotBeSetOnEvent() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "UNKNOWN_SLA",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getSla());
  }

  @Test
  void testOpenShiftClusterCoresInvalidUsageSetsNullValue() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "UNKNOWN_USAGE",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getUsage());
  }

  @Test
  void testOpenShiftClusterCoresHandlesNullUsage() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            null,
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getUsage());
  }

  @Test
  void testOpenShiftClusterCoresInvalidRoleSetsNullValue() {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            "UNKNOWN_ROLE",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getRole());
  }

  @Test
  void testOpenShiftClusterCoresHandlesNullRole() {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getRole());
  }

  @Test
  void testHandlesValidBillingData() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "aws",
            "aws_account_123",
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertEquals(BillingProvider.AWS, event.getBillingProvider());
    assertTrue(event.getBillingAccountId().isPresent());
    assertEquals("aws_account_123", event.getBillingAccountId().get());
  }

  @Test
  void testHandlesNullBillingData() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            null,
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertEquals(BillingProvider.RED_HAT, event.getBillingProvider());
    assertTrue(event.getBillingAccountId().isEmpty());
  }

  @Test
  void testBillingProviderEmptyForBillingProviderValueNoneToDefaultRedHat() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertEquals(BillingProvider.RED_HAT, event.getBillingProvider());
  }

  @Test
  void testInvalidBillingProviderWillNotBeSetOnEvent() throws Exception {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "invalid provider",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertNull(event.getBillingProvider());
  }

  @Test
  void testEventTypeGeneratedOnEventCreation() {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            null,
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "red hat",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertEquals("snapshot_openshift-dedicated-metrics_cores", event.getEventType());
  }

  @Test
  void testEventTypeGeneration() {
    assertEquals(
        "snapshot_openshift-dedicated-metrics_my-metric",
        MeteringEventFactory.getEventType("my-metric", productTag));
    assertEquals("snapshot", MeteringEventFactory.getEventType("", ""));
  }

  @Test
  void testRhmNormalizedToRedHat() {
    Event event =
        MeteringEventFactory.createMetricEvent(
            "my-account",
            "my-org",
            "cluster-id",
            "Premium",
            "Production",
            "ocp",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            "service_type",
            "rhm",
            null,
            MetricIdUtils.getCores(),
            12.5,
            productTag,
            "span");
    assertEquals(BillingProvider.RED_HAT, event.getBillingProvider());
  }
}
