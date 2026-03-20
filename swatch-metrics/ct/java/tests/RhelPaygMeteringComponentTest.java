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
package tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.PrometheusMetricData;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;

class RhelPaygMeteringComponentTest extends BaseMetricsComponentTest {

  @Test
  void shouldCreateMeteringEventsForRhelPaygAddonFromPrometheus() throws Exception {
    // Given: RHEL PAYG addon metrics exist in Prometheus
    String productTag = "rhel-for-x86-els-payg-addon";
    String metricId = VCPUS.getValue();
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String instanceId = "i-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "test-" + RandomUtils.generateRandom();
    double expectedValue = 4.0;

    PrometheusMetricData metricData =
        PrometheusMetricData.buildRhelPaygAddonMetric(
            orgId, instanceId, billingProvider, billingAccountId, expectedValue);

    // Push metrics to Prometheus using importer API
    api.PrometheusService realPrometheus = new api.PrometheusService();
    String openMetricsData = metricData.toOpenMetrics(clusterId, "subscription_labels_vcpus");
    System.out.println("=== Pushing metrics via importer ===");
    realPrometheus.importMetrics(openMetricsData);
    System.out.println("Metrics pushed successfully");

    // When: Trigger metering after remote write
    System.out.println("=== Triggering metering operation ===");
    whenMeteringIsPerformed(productTag, metricId);

    // Then: Events are created with correct fields
    var events = thenEventsAreProduced(instanceId, metricId);
    assertFalse(events.isEmpty(), "Events should be produced");

    Event event = events.get(0);
    thenEventHasCorrectMetadata(event, productTag, orgId, instanceId);
    thenEventHasCorrectBillingInfo(event, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(event);
    thenEventHasCorrectMeasurements(event, metricId, expectedValue);
  }

  private void thenEventHasCorrectMetadata(
      Event event, String productTag, String orgId, String instanceId) {
    assertNotNull(event.getEventSource(), "event_source should not be null");
    assertEquals(
        "snapshot_rhel-for-x86-els-payg-addon_vcpus",
        event.getEventType(),
        "event_type should match");
    assertEquals(orgId, event.getOrgId(), "org_id should match");
    assertEquals(instanceId, event.getInstanceId(), "instance_id should match");
    assertNotNull(event.getMeteringBatchId(), "metering_batch_id should not be null");
    assertEquals("RHEL System", event.getServiceType(), "service_type should match");
    assertNotNull(event.getTimestamp(), "timestamp should not be null");
    assertNotNull(event.getExpiration(), "expiration should not be null");
    assertNotNull(event.getDisplayName(), "display_name should not be null");
  }

  private void thenEventHasCorrectBillingInfo(
      Event event, String billingProvider, String billingAccountId) {
    assertEquals(
        Event.BillingProvider.AWS, event.getBillingProvider(), "billing_provider should be AWS");
    assertEquals(
        billingAccountId,
        event.getBillingAccountId().orElse(null),
        "billing_account_id should match");
  }

  private void thenEventHasCorrectProductInfo(Event event) {
    assertTrue(
        event.getProductTag().contains("rhel-for-x86-els-payg-addon"),
        "product_tag should contain rhel-for-x86-els-payg-addon");
    assertTrue(
        event.getProductIds().contains("204"), "product_ids should contain engineering ID 204");
    assertFalse(event.getConversion(), "conversion should be false");
    assertEquals(Event.Sla.PREMIUM, event.getSla(), "sla should be Premium");
  }

  private void thenEventHasCorrectMeasurements(Event event, String metricId, double expectedValue) {
    assertFalse(event.getMeasurements().isEmpty(), "measurements should not be empty");

    Measurement measurement = event.getMeasurements().get(0);
    assertEquals(metricId, measurement.getMetricId(), "metric_id should match");
    assertTrue(measurement.getValue() > 0.0, "metric value should be greater than 0");
    assertEquals(expectedValue, measurement.getValue(), 0.01, "metric value should match");
  }
}
