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

class SwatchMetricsAcmPrometheusTest extends BaseMetricsComponentTest {

  private static final String ACM_PRODUCT_TAG = "rhacm";
  private static final String VCPUS_METRIC_ID = VCPUS.getValue();
  private static final String VCPUS_SELF_MANAGED_METRIC_ID = "vCPUs-self-managed";
  private static final int METERING_API_RANGE_MINUTES = 120;

  @Test
  void shouldCreateMeteringEventsForAcmManagedCoresFromPrometheusAws() {
    // Given: ACM managed cores metrics from mock Prometheus with AWS billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "aws-" + RandomUtils.generateRandom();
    double expectedCores = 8.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    givenPrometheusReturnsAcmManagedMetrics(
        clusterId, billingProvider, billingAccountId, expectedCores);

    // When: Internal metering is triggered
    service.triggerInternalMetering(
        ACM_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Metering events are produced with managed cores metric
    var events = thenEventsAreProduced(clusterId, VCPUS_METRIC_ID);
    assertFalse(events.isEmpty(), "Events should be produced");

    Event event = events.get(0);
    thenEventHasCorrectMetadata(event, clusterId);
    thenEventHasCorrectBillingInfo(event, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(event);
    thenEventHasCorrectMeasurements(event, VCPUS_METRIC_ID, expectedCores);
  }

  @Test
  void shouldCreateMeteringEventsForAcmManagedCoresFromPrometheusAzure() {
    // Given: ACM managed cores metrics from mock Prometheus with Azure billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "azure";
    String billingAccountId = "azure-" + RandomUtils.generateRandom();
    double expectedCores = 16.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    givenPrometheusReturnsAcmManagedMetrics(
        clusterId, billingProvider, billingAccountId, expectedCores);

    // When: Internal metering is triggered
    service.triggerInternalMetering(
        ACM_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Metering events are produced with managed cores metric
    var events = thenEventsAreProduced(clusterId, VCPUS_METRIC_ID);
    assertFalse(events.isEmpty(), "Events should be produced");

    Event event = events.get(0);
    thenEventHasCorrectMetadata(event, clusterId);
    thenEventHasCorrectBillingInfo(event, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(event);
    thenEventHasCorrectMeasurements(event, VCPUS_METRIC_ID, expectedCores);
  }

  @Test
  void shouldCreateMeteringEventsForAcmSelfManagedCoresFromPrometheusAws() {
    // Given: ACM self-managed cores metrics from mock Prometheus with AWS billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "aws-" + RandomUtils.generateRandom();
    double expectedCores = 12.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    givenPrometheusReturnsAcmSelfManagedMetrics(
        clusterId, billingProvider, billingAccountId, expectedCores);

    // When: Internal metering is triggered
    service.triggerInternalMetering(
        ACM_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Metering events are produced with self-managed cores metric
    var events = thenEventsAreProduced(clusterId, VCPUS_SELF_MANAGED_METRIC_ID);
    assertFalse(events.isEmpty(), "Events should be produced");

    Event event = events.get(0);
    thenEventHasCorrectMetadata(event, clusterId);
    thenEventHasCorrectBillingInfo(event, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(event);
    thenEventHasCorrectMeasurements(event, VCPUS_SELF_MANAGED_METRIC_ID, expectedCores);
  }

  @Test
  void shouldCreateMeteringEventsForAcmSelfManagedCoresFromPrometheusAzure() {
    // Given: ACM self-managed cores metrics from mock Prometheus with Azure billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "azure";
    String billingAccountId = "azure-" + RandomUtils.generateRandom();
    double expectedCores = 20.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    givenPrometheusReturnsAcmSelfManagedMetrics(
        clusterId, billingProvider, billingAccountId, expectedCores);

    // When: Internal metering is triggered
    service.triggerInternalMetering(
        ACM_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Metering events are produced with self-managed cores metric
    var events = thenEventsAreProduced(clusterId, VCPUS_SELF_MANAGED_METRIC_ID);
    assertFalse(events.isEmpty(), "Events should be produced");

    Event event = events.get(0);
    thenEventHasCorrectMetadata(event, clusterId);
    thenEventHasCorrectBillingInfo(event, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(event);
    thenEventHasCorrectMeasurements(event, VCPUS_SELF_MANAGED_METRIC_ID, expectedCores);
  }

  private static OffsetDateTime currentUtcHourStart() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
  }

  private void givenPrometheusReturnsAcmManagedMetrics(
      String clusterId, String billingProvider, String billingAccountId, double coresValue) {
    Map<String, String> labels = buildAcmLabels(clusterId, billingProvider, billingAccountId);
    wiremock
        .forPrometheus()
        .stubQueryRangeWithMetricData(
            "acm:managed_cluster_worker_cores:managed:sum", labels, coresValue);
  }

  private void givenPrometheusReturnsAcmSelfManagedMetrics(
      String clusterId, String billingProvider, String billingAccountId, double coresValue) {
    Map<String, String> labels = buildAcmLabels(clusterId, billingProvider, billingAccountId);
    wiremock
        .forPrometheus()
        .stubQueryRangeWithMetricData(
            "acm:managed_cluster_worker_cores:self_managed:sum", labels, coresValue);
  }

  private Map<String, String> buildAcmLabels(
      String clusterId, String billingProvider, String billingAccountId) {
    Map<String, String> labels = new java.util.HashMap<>();
    labels.put("_id", clusterId);
    labels.put("billing_marketplace", billingProvider);
    labels.put("billing_marketplace_account", billingAccountId);
    labels.put("billing_model", "marketplace");
    labels.put("display_name", clusterId);
    labels.put("external_organization", orgId);
    labels.put("product", "moa");
    return labels;
  }

  private void thenEventHasCorrectMetadata(Event event, String clusterId) {
    assertNotNull(event.getEventSource(), "event_source should not be null");
    assertEquals(orgId, event.getOrgId(), "org_id should match");
    assertEquals(clusterId, event.getInstanceId(), "instance_id should match");
    assertEquals(
        clusterId, event.getDisplayName().orElse(null), "display_name should match cluster_id");
    assertNotNull(event.getMeteringBatchId(), "metering_batch_id should not be null");
    assertNotNull(event.getTimestamp(), "timestamp should not be null");
    assertNotNull(event.getExpiration(), "expiration should not be null");
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
    assertNotNull(event.getProductTag(), "product_tag should not be null");
    assertFalse(event.getProductTag().isEmpty(), "product_tag should not be empty");
    assertTrue(
        event.getProductTag().contains(ACM_PRODUCT_TAG), "product_tag should contain expected tag");
  }

  private void thenEventHasCorrectMeasurements(Event event, String metricId, double expectedValue) {
    assertFalse(event.getMeasurements().isEmpty(), "measurements should not be empty");

    Measurement measurement = event.getMeasurements().get(0);
    assertEquals(metricId, measurement.getMetricId(), "metric_id should match");
    assertEquals(expectedValue, measurement.getValue(), 0.01, "metric value should match");
  }
}
