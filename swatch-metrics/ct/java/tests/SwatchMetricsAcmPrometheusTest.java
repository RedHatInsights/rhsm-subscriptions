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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;

class SwatchMetricsAcmPrometheusTest extends BaseMetricsComponentTest {

  private static final String ACM_PRODUCT_TAG = "rhacm";
  private static final String VCPUS_METRIC_ID = VCPUS.getValue();
  private static final String VCPUS_SELF_MANAGED_METRIC_ID = "vCPUs-self-managed";
  private static final int METERING_API_RANGE_MINUTES = 120;

  @TestPlanName("swatch-metrics-prometheus-TC002")
  @Test
  void shouldCreateMeteringEventsForAcmManagedCoresFromPrometheusAws() {
    // Given: ACM managed cores metrics from mock Prometheus with AWS billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "aws-" + RandomUtils.generateRandom();
    double expectedCores = 8.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("moa")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(expectedCores)
        .stub();

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

  @TestPlanName("swatch-metrics-prometheus-TC003")
  @Test
  void shouldCreateMeteringEventsForAcmManagedCoresFromPrometheusAzure() {
    // Given: ACM managed cores metrics from mock Prometheus with Azure billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "azure";
    String billingAccountId = "azure-" + RandomUtils.generateRandom();
    double expectedCores = 16.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("moa")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(expectedCores)
        .stub();

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

  @TestPlanName("swatch-metrics-prometheus-TC004")
  @Test
  void shouldCreateMeteringEventsForAcmSelfManagedCoresFromPrometheusAws() {
    // Given: ACM self-managed cores metrics from mock Prometheus with AWS billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "aws-" + RandomUtils.generateRandom();
    double expectedCores = 12.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:self_managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("moa")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(expectedCores)
        .stub();

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

  @TestPlanName("swatch-metrics-prometheus-TC005")
  @Test
  void shouldCreateMeteringEventsForAcmSelfManagedCoresFromPrometheusAzure() {
    // Given: ACM self-managed cores metrics from mock Prometheus with Azure billing
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "azure";
    String billingAccountId = "azure-" + RandomUtils.generateRandom();
    double expectedCores = 20.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:self_managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("moa")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(expectedCores)
        .stub();

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

  @TestPlanName("swatch-metrics-prometheus-TC006")
  @Test
  void shouldCreateSeparateEventsForBothManagedAndSelfManagedCoresFromSameCluster() {
    // Given: Single cluster reporting both managed and self-managed cores
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "aws-" + RandomUtils.generateRandom();
    double expectedManagedCores = 10.0;
    double expectedSelfManagedCores = 15.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    // Stub both dimension metrics for the same cluster
    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("moa")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(expectedManagedCores)
        .stub();

    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:self_managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("moa")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(expectedSelfManagedCores)
        .stub();

    // When: Internal metering is triggered once
    service.triggerInternalMetering(
        ACM_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Two distinct events are produced - one for each dimension
    var managedEvents = thenEventsAreProduced(clusterId, VCPUS_METRIC_ID);
    assertFalse(managedEvents.isEmpty(), "Managed events should be produced");

    var selfManagedEvents = thenEventsAreProduced(clusterId, VCPUS_SELF_MANAGED_METRIC_ID);
    assertFalse(selfManagedEvents.isEmpty(), "Self-managed events should be produced");

    // Verify managed event has correct value (verifies no cross-contamination)
    Event managedEvent = managedEvents.get(0);
    thenEventHasCorrectMetadata(managedEvent, clusterId);
    thenEventHasCorrectBillingInfo(managedEvent, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(managedEvent);
    thenEventHasCorrectMeasurements(managedEvent, VCPUS_METRIC_ID, expectedManagedCores);

    // Verify self-managed event has correct value (verifies no cross-contamination)
    Event selfManagedEvent = selfManagedEvents.get(0);
    thenEventHasCorrectMetadata(selfManagedEvent, clusterId);
    thenEventHasCorrectBillingInfo(selfManagedEvent, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(selfManagedEvent);
    thenEventHasCorrectMeasurements(
        selfManagedEvent, VCPUS_SELF_MANAGED_METRIC_ID, expectedSelfManagedCores);
  }

  @TestPlanName("swatch-metrics-prometheus-TC007")
  @Test
  void shouldFilterManagedEventsForOcpAssistedInstallProduct() {
    // Given: Cluster with product=ocp-assistedinstall (self-managed regex only)
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "aws-" + RandomUtils.generateRandom();
    double managedCores = 8.0;
    double selfManagedCores = 12.0;
    OffsetDateTime meteringEnd = currentUtcHourStart();

    // Stub both metrics with ocp-assistedinstall product
    // Only self-managed dimension includes this product in its regex
    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("ocp-assistedinstall")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(managedCores)
        .stub();

    wiremock
        .forPrometheus()
        .metric("acm:managed_cluster_worker_cores:self_managed:sum")
        .label("_id", clusterId)
        .displayName(clusterId)
        .orgId(orgId)
        .product("ocp-assistedinstall")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .value(selfManagedCores)
        .stub();

    // When: Internal metering is triggered
    service.triggerInternalMetering(
        ACM_PRODUCT_TAG, orgId, meteringEnd, METERING_API_RANGE_MINUTES);

    // Then: Only self-managed event is produced (managed is filtered by regex)
    var selfManagedEvents = thenEventsAreProduced(clusterId, VCPUS_SELF_MANAGED_METRIC_ID);
    assertFalse(selfManagedEvents.isEmpty(), "Self-managed events should be produced");

    Event selfManagedEvent = selfManagedEvents.get(0);
    thenEventHasCorrectMetadata(selfManagedEvent, clusterId);
    thenEventHasCorrectBillingInfo(selfManagedEvent, billingProvider, billingAccountId);
    thenEventHasCorrectProductInfo(selfManagedEvent);
    thenEventHasCorrectMeasurements(
        selfManagedEvent, VCPUS_SELF_MANAGED_METRIC_ID, selfManagedCores);

    // Verify no managed events are produced (productLabelRegex filters them out). The self-managed
    // events above are our sync point: the service processes the managed metric first, so any
    // managed event would already be cached by now. A non-blocking read confirms there are none.
    var managedEvents = thenEventsAlreadyProduced(clusterId, VCPUS_METRIC_ID);
    assertTrue(
        managedEvents.isEmpty(),
        "No managed events should be produced for ocp-assistedinstall product");
  }

  private static OffsetDateTime currentUtcHourStart() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
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
