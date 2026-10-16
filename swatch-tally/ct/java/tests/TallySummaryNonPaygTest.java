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

import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TallySummaryNonPaygTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86.metricIds().get(1);

  private HostStateManager hostManager;

  @BeforeEach
  void setupHostManager() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
  }

  @AfterEach
  void cleanupHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  @Test
  @TestPlanName("tally-summary-nonpayg-TC001")
  void testTallyNightlySummaryEmitsGranularityDaily() {
    // Given: Create multiple recent hosts with sockets for nightly tally
    service.createOptInConfig(orgId);
    givenPhysicalRhelHost(2).insert();
    givenPhysicalRhelHost(4).insert();

    // When: Polling for tally summaries with DAILY granularity
    List<TallySummary> tallySummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, 1, service, kafkaBridge);

    // Then: The shared bucket emits one daily summary without TOTAL measurements
    var measurements =
        tallySummaries.stream()
            .flatMap(summary -> summary.getTallySnapshots().stream())
            .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
            .toList();

    Assertions.assertAll(
        () -> Assertions.assertEquals(1, tallySummaries.size(), "Expected one summary per bucket"),
        () ->
            Assertions.assertTrue(
                measurements.stream()
                    .anyMatch(m -> TEST_METRIC_ID.equalsIgnoreCase(m.getMetricId())),
                "Expected the Sockets measurement"),
        () ->
            Assertions.assertTrue(
                measurements.stream()
                    .allMatch(m -> "PHYSICAL".equalsIgnoreCase(m.getHardwareMeasurementType())),
                "Expected only PHYSICAL measurements and no TOTAL measurements"));
  }

  @Test
  @TestPlanName("tally-summary-nonpayg-TC002")
  void testTallyNightlySummaryCurrentTotalEqualsGaugeValue() {
    // Given: Create one physical RHEL host with 2 sockets
    service.createOptInConfig(orgId);
    givenPhysicalRhelHost(2).insert();

    // When: Nightly tally emits a DAILY summary
    List<TallySummary> tallySummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, 1, service, kafkaBridge);

    // Then: Socket gauge value and currentTotal are present and equal
    GaugeMeasurement sockets = getSocketGauge(tallySummaries);
    Assertions.assertEquals(sockets.value().doubleValue(), sockets.currentTotal().doubleValue());
  }

  @Test
  @TestPlanName("tally-summary-nonpayg-TC003")
  void testTallyNightlySummaryGaugeReflectsCurrentStateNotAccumulation() {
    // Given: A baseline daily Sockets total, then a host created
    service.createOptInConfig(orgId);
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);
    double baseline = getDailyPhysicalSocketsTotal(beginning, ending);
    givenPhysicalRhelHost(2).insert();

    // When: Host A is tallied, then Host B is added and tallied
    List<TallySummary> firstSummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, 1, service, kafkaBridge);
    GaugeMeasurement firstSockets = getSocketGauge(firstSummaries);

    givenPhysicalRhelHost(4).insert();
    // KafkaBridge retains messages from the first tally, so wait for both runs' summaries.
    List<TallySummary> secondSummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, 2, service, kafkaBridge);
    GaugeMeasurement secondSockets = getSocketGauge(secondSummaries);

    // Then: The second value includes Host B's 4 current sockets, not an accumulated prior total
    Assertions.assertAll(
        () -> Assertions.assertEquals(baseline + 2.0, firstSockets.value().doubleValue()),
        () ->
            Assertions.assertEquals(
                firstSockets.value().doubleValue(), firstSockets.currentTotal().doubleValue()),
        () ->
            Assertions.assertEquals(
                4.0, secondSockets.value().doubleValue() - firstSockets.value().doubleValue()),
        () ->
            Assertions.assertEquals(
                secondSockets.value().doubleValue(), secondSockets.currentTotal().doubleValue()));
  }

  @Test
  @TestPlanName("tally-summary-nonpayg-TC004")
  void testTallyNightlySummaryGaugeTotalsDropWhenHostBucketRemoved() {
    // Given: Three physical RHEL hosts contribute 12 sockets to the daily total
    service.createOptInConfig(orgId);
    OffsetDateTime beginning = OffsetDateTime.now().minusDays(1);
    OffsetDateTime ending = OffsetDateTime.now().plusDays(1);
    givenPhysicalRhelHost(2).insert();
    givenPhysicalRhelHost(4).insert();
    HostBuilder hostWithoutRhel = givenPhysicalRhelHost(6);
    hostWithoutRhel.insert();

    List<TallySummary> beforeSummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, 1, service, kafkaBridge);
    double reportTotalBefore = getDailyPhysicalSocketsTotal(beginning, ending);
    GaugeMeasurement summaryBefore = getSocketGauge(beforeSummaries);
    var instancesBefore = service.getInstancesByProduct(orgId, TEST_PRODUCT_TAG, beginning, ending);

    // When: Host B stops reporting RHEL and nightly reconciliation removes its product buckets
    hostWithoutRhel
        .rhsmFacts(hostWithoutRhel.rhsmFacts().toBuilder().clearProducts().build())
        .update();
    // KafkaBridge retains the pre-removal summary, so wait for both runs' summaries.
    List<TallySummary> afterSummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, 2, service, kafkaBridge);
    double reportTotalAfter = getDailyPhysicalSocketsTotal(beginning, ending);
    GaugeMeasurement summaryAfter = getSocketGauge(afterSummaries);
    var instancesAfter = service.getInstancesByProduct(orgId, TEST_PRODUCT_TAG, beginning, ending);

    // Then: The daily report, summary, and instance count reflect the removed host
    Assertions.assertAll(
        () -> Assertions.assertEquals(12.0, reportTotalBefore),
        () -> Assertions.assertEquals(12.0, summaryBefore.value().doubleValue()),
        () -> Assertions.assertEquals(12.0, summaryBefore.currentTotal().doubleValue()),
        () -> Assertions.assertEquals(3, instancesBefore.getMeta().getCount().intValue()),
        () -> Assertions.assertEquals(6.0, reportTotalAfter),
        () -> Assertions.assertEquals(6.0, summaryAfter.value().doubleValue()),
        () -> Assertions.assertEquals(6.0, summaryAfter.currentTotal().doubleValue()),
        () -> Assertions.assertEquals(2, instancesAfter.getMeta().getCount().intValue()));
  }

  private HostBuilder givenPhysicalRhelHost(int sockets) {
    return hostManager
        .createHost(orgId)
        .subscriptionManagerId(UUID.randomUUID().toString())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("physical")
                .arch("x86_64")
                .numberOfSockets(sockets)
                .build());
  }

  private GaugeMeasurement getSocketGauge(List<TallySummary> summaries) {
    // KafkaBridge returns cached messages in arrival order; the latest measurement reflects the
    // most recent tally run.
    var socketMeasurement =
        summaries.stream()
            .flatMap(summary -> summary.getTallySnapshots().stream())
            .flatMap(snapshot -> snapshot.getTallyMeasurements().stream())
            .filter(measurement -> TEST_METRIC_ID.equalsIgnoreCase(measurement.getMetricId()))
            .filter(m -> "PHYSICAL".equalsIgnoreCase(m.getHardwareMeasurementType()))
            .reduce((first, latest) -> latest)
            .orElseThrow(() -> new AssertionError("Expected a PHYSICAL Sockets measurement"));
    return new GaugeMeasurement(socketMeasurement.getValue(), socketMeasurement.getCurrentTotal());
  }

  private double getDailyPhysicalSocketsTotal(OffsetDateTime beginning, OffsetDateTime ending) {
    var report =
        service.getTallyReportData(
            orgId,
            TEST_PRODUCT_TAG,
            TEST_METRIC_ID,
            Map.of(
                "granularity",
                "Daily",
                "beginning",
                beginning.toString(),
                "ending",
                ending.toString(),
                "category",
                "physical"));
    if (report.getData() == null) {
      return 0.0;
    }
    return report.getData().stream().mapToDouble(point -> point.getValue()).sum();
  }

  private record GaugeMeasurement(Number value, Number currentTotal) {}
}
