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

import api.PrometheusService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.PrometheusMetricData;
import domain.PrometheusMetricData.TimeValuePair;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwatchMetricsPrometheusTest extends BaseMetricsComponentTest {

  /** Must match product definition used by metering and event assertions in this class. */
  private static final String RHEL_PAYG_ADDON_PRODUCT_TAG = "rhel-for-x86-els-payg-addon";

  /**
   * POST {@code rangeInMinutes} for internal metering: large enough that after the controller
   * shifts the Prometheus range start by one hour, at least one full hourly step still overlaps
   * imported samples.
   */
  private static final int METERING_API_RANGE_MINUTES = 120;

  /** Importer returns before the TSDB always answers on {@code /api/v1/query}; avoid flaky CT. */
  private static final Duration PROM_PROBE_POLL = Duration.ofMillis(500);

  private static final Duration PROM_PROBE_TIMEOUT = Duration.ofSeconds(90);

  /**
   * Exercises rhelemeter PromQL against imported OpenMetrics, internal metering, and Kafka-bound
   * events. Flow: build aligned fixture → import → wait until query API sees the series → POST
   * metering → assert event payload.
   */
  @Test
  void shouldCreateMeteringEventsForRhelPaygAddonFromPrometheus() {
    // Given: RHEL PAYG addon metrics imported to Prometheus
    String metricId = VCPUS.getValue();
    String clusterId = "cluster-" + RandomUtils.generateRandom();
    String instanceId = "i-" + RandomUtils.generateRandom();
    String billingProvider = "aws";
    String billingAccountId = "test-" + RandomUtils.generateRandom();
    double expectedVcpu = 4.0;

    OffsetDateTime meteringEnd = currentUtcHourStart();
    PrometheusMetricData metricData =
        rhelPaygMetricsForMeteringWindow(
            orgId, instanceId, billingProvider, billingAccountId, expectedVcpu, meteringEnd);

    PrometheusService prometheus = new PrometheusService();
    String probeQuery = vcpuProbeQuery(instanceId, orgId);
    long lastSampleEpoch = lastSampleEpochSeconds(metricData);

    prometheus.importMetricsExpectSuccess(
        metricData.toOpenMetrics(clusterId, "subscription_labels_vcpus"));
    awaitProbeSeriesVisible(prometheus, probeQuery, lastSampleEpoch);

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

  private static String vcpuProbeQuery(String instanceId, String orgId) {
    return String.format(
        "system_cpu_logical_count{billing_marketplace_instance_id=\"%s\",external_organization=\"%s\"}",
        instanceId, orgId);
  }

  private static long lastSampleEpochSeconds(PrometheusMetricData metricData) {
    List<TimeValuePair> values = metricData.getValues();
    return (long) values.get(values.size() - 1).getTimestamp();
  }

  /**
   * Instant query at {@code now} misses stale-looking points; evaluating at {@code lastSampleEpoch}
   * covers that case. This is the series count used both for waiting and for logging.
   */
  private static int probeSeriesCount(
      PrometheusService prometheus, String probeQuery, long lastSampleEpochSeconds) {
    return Math.max(
        prometheus.getInstantQueryResultCount(probeQuery, null),
        prometheus.getInstantQueryResultCount(probeQuery, lastSampleEpochSeconds));
  }

  private static void awaitProbeSeriesVisible(
      PrometheusService prometheus, String probeQuery, long lastSampleEpochSeconds) {
    AwaitilityUtils.untilIsTrue(
        () -> probeSeriesCount(prometheus, probeQuery, lastSampleEpochSeconds) > 0,
        AwaitilitySettings.using(PROM_PROBE_POLL, PROM_PROBE_TIMEOUT)
            .timeoutMessage(
                "Prometheus did not return probe series for %s within timeout "
                    + "(instant at now or at last sample epoch %d)",
                probeQuery, lastSampleEpochSeconds));
  }

  /**
   * Samples every minute over the last four hours through {@code max(meteringEndUtc, now)} so that
   * (1) metering windows ending at {@code meteringEndUtc} still have history, and (2) the latest
   * point is recent enough for Prometheus instant queries at default evaluation time (lookback
   * ~5m).
   */
  private static PrometheusMetricData rhelPaygMetricsForMeteringWindow(
      String orgId,
      String instanceId,
      String billingProvider,
      String billingAccountId,
      double metricValue,
      OffsetDateTime meteringEndUtc) {
    long hourEnd = meteringEndUtc.toEpochSecond();
    long nowSec = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();
    long end = Math.max(hourEnd, nowSec);
    long start = end - 4 * 3600L;
    List<TimeValuePair> dataPoints = new ArrayList<>();
    for (long t = start; t <= end; t += 60) {
      dataPoints.add(new TimeValuePair(t, metricValue));
    }
    return PrometheusMetricData.builder()
        .orgId(orgId)
        .instanceId(instanceId)
        .sla("Premium")
        .usage("PRODUCTION")
        .billingProvider(billingProvider)
        .billingAccountId(billingAccountId)
        .accountNumber(null)
        .displayName(instanceId + "-display")
        .product("204")
        .conversionsSuccess(false)
        .values(dataPoints)
        .build();
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
