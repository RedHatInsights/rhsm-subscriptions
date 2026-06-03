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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;

class SwatchMetricsPrometheusTest extends BaseMetricsComponentTest {

  /** Must match product definition used by metering and event assertions in this class. */
  private static final String RHEL_PAYG_ADDON_PRODUCT_TAG = "rhel-for-x86-els-payg-addon";

  /**
   * POST {@code rangeInMinutes} for internal metering: large enough that after the controller
   * shifts the Prometheus range start by one hour, at least one full hourly step still overlaps
   * imported samples.
   */
  private static final int METERING_API_RANGE_MINUTES = 120;

  /**
   * Exercises rhelemeter PromQL against mocked Prometheus responses, internal metering, and
   * Kafka-bound events. Flow: stub query_range results → POST metering → assert event payload.
   */
  @Test
  void shouldCreateMeteringEventsForRhelPaygAddonFromPrometheus() {
    // Given: RHEL PAYG addon metrics will be returned from mock Prometheus
    String metricId = VCPUS.getValue();
    String instanceId = "i-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "test-" + RandomUtils.generateRandom();
    double expectedVcpu = 4.0;

    OffsetDateTime meteringEnd = currentUtcHourStart();

    // Stub the query_range endpoint to return metric data
    // The metering service will query Prometheus for VCPU metrics
    Map<String, String> labels = new java.util.HashMap<>();
    labels.put("billing_marketplace_instance_id", instanceId);
    labels.put("external_organization", orgId);
    labels.put("billing_marketplace", billingProvider);
    labels.put("billing_marketplace_account", billingAccountId);
    labels.put("product", "204");
    labels.put("billing_model", "marketplace");
    labels.put("support", "Premium");
    labels.put("usage", "Production");
    wiremock
        .forPrometheus()
        .stubQueryRangeWithMetricData("system_cpu_logical_count", labels, expectedVcpu);

    // When: Internal metering is triggered
    service.triggerInternalMetering(
        RHEL_PAYG_ADDON_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Metering events are produced with correct data
    var events = thenEventsAreProduced(instanceId, metricId);
    assertFalse(events.isEmpty(), "Events should be produced");

    Event event = events.get(0);
    thenEventHasCorrectMetadata(event, orgId, instanceId);
    thenEventHasCorrectBillingInfo(event, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(event);
    thenEventHasCorrectMeasurements(event, metricId, expectedVcpu);
  }

  /**
   * Same boundary as {@code InternalMeteringResource}: end of the metering window on an hour in
   * UTC. Using the next hour would put {@code endDate} in the future so imported samples sit past
   * {@code now} and range queries often get an empty matrix.
   */
  private static OffsetDateTime currentUtcHourStart() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
  }

  private void thenEventHasCorrectMetadata(Event event, String orgId, String instanceId) {
    assertNotNull(event.getEventSource(), "event_source should not be null");
    assertEquals(
        "snapshot_" + RHEL_PAYG_ADDON_PRODUCT_TAG + "_vcpus",
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
        Event.BillingProvider.valueOf(billingProvider.toUpperCase()),
        event.getBillingProvider(),
        "billing_provider should match");
    assertEquals(
        billingAccountId,
        event.getBillingAccountId().orElse(null),
        "billing_account_id should match");
  }

  private void thenEventHasCorrectProductInfo(Event event) {
    assertTrue(
        event.getProductTag().contains(RHEL_PAYG_ADDON_PRODUCT_TAG),
        "product_tag should contain " + RHEL_PAYG_ADDON_PRODUCT_TAG);
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
