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

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TallyReportCategoryHasDataPaygTest extends BaseTallyComponentTest {

  private static final String PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

  private static final Duration PIPELINE_WAIT_TIMEOUT = Duration.ofSeconds(120);
  private static final Duration PIPELINE_POLL_INTERVAL = Duration.ofSeconds(2);

  private static String firstOrgId;

  /** Cloud usage hour and surrounding range for gap assertions. */
  private static OffsetDateTime positiveEventHourStart;

  private static OffsetDateTime positiveEventHourEnd;
  private static OffsetDateTime positiveRangeStart;
  private static OffsetDateTime gapHour;

  /** Separate hour for zero-quantity cloud usage. */
  private static OffsetDateTime zeroEventHourStart;

  private static OffsetDateTime zeroEventHourEnd;

  @BeforeAll
  static void setupCategoryHasDataPaygEvents() {
    OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
    positiveEventHourStart = base.minusHours(2);
    positiveEventHourEnd = positiveEventHourStart.plusHours(1).minusNanos(1);
    positiveRangeStart = positiveEventHourStart.minusHours(4);
    gapHour = positiveEventHourStart.minusHours(2);

    zeroEventHourStart = base.minusHours(6);
    zeroEventHourEnd = zeroEventHourStart.plusHours(1).minusNanos(1);

    firstOrgId = String.valueOf(10000 + (int) (Math.random() * 90000));
    service.createOptInConfig(firstOrgId);
    publishCloudPaygEvent(firstOrgId, positiveEventHourStart, 8.0f);
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(firstOrgId);
          if (!usagePositiveForHour(
              getCategoryHourlyReport(
                  firstOrgId, "cloud", positiveEventHourStart, positiveEventHourEnd),
              positiveEventHourStart)) {
            throw new AssertionError(
                "PAYG fixtures not ready for org "
                    + firstOrgId
                    + " at positive hour "
                    + positiveEventHourStart);
          }
        },
        AwaitilitySettings.using(PIPELINE_POLL_INTERVAL, PIPELINE_WAIT_TIMEOUT)
            .timeoutMessage(
                "Category=cloud metric=%s must materialize positive usage at %s.",
                METRIC_ID, positiveEventHourStart));
  }

  private void awaitCloudZeroMeasurementReady(String orgId) {
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);
          if (!zeroMeasurementForHour(
              getCategoryHourlyReport(orgId, "cloud", zeroEventHourStart, zeroEventHourEnd),
              zeroEventHourStart)) {
            throw new AssertionError(
                "PAYG zero-quantity cloud measurement not ready for org "
                    + orgId
                    + " at "
                    + zeroEventHourStart
                    + " (expected value=0, has_data=true on category=cloud)");
          }
        },
        AwaitilitySettings.using(PIPELINE_POLL_INTERVAL, PIPELINE_WAIT_TIMEOUT)
            .timeoutMessage(
                "Category=cloud metric=%s must materialize value=0 with has_data=true at %s.",
                METRIC_ID, zeroEventHourStart));
  }

  private static void publishCloudPaygEvent(
      String orgId, OffsetDateTime hourStart, float metricValue) {
    kafkaBridge.produceKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS,
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            hourStart.toString(),
            UUID.randomUUID().toString(),
            METRIC_ID,
            metricValue,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            PRODUCT_ID,
            PRODUCT_TAG));
  }

  private static Map<String, Object> hourlyQueryParams(
      OffsetDateTime beginning, OffsetDateTime ending, String category) {
    Map<String, Object> params = new HashMap<>();
    params.put("granularity", "Hourly");
    params.put("beginning", beginning.toString());
    params.put("ending", ending.toString());
    params.put("sla", ServiceLevelType.PREMIUM.toString());
    params.put("category", category);
    return params;
  }

  private static TallyReportData getCategoryHourlyReport(
      String orgId, String category, OffsetDateTime beginning, OffsetDateTime ending) {
    return service.getTallyReportData(
        orgId, PRODUCT_TAG, METRIC_ID, hourlyQueryParams(beginning, ending, category));
  }

  private static OffsetDateTime startOfUtcHour(OffsetDateTime t) {
    return t.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
  }

  private static TallyReportDataPoint pointForHour(
      TallyReportData report, OffsetDateTime hourStart) {
    assertNotNull(report.getData(), "report data");
    return report.getData().stream()
        .filter(p -> p.getDate() != null && startOfUtcHour(p.getDate()).isEqual(hourStart))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No data point for hour " + hourStart));
  }

  private static boolean hasMisleadingZeroWithHasData(TallyReportData report) {
    return report.getData().stream()
        .anyMatch(
            p -> p.getValue() != null && p.getValue() == 0 && Boolean.TRUE.equals(p.getHasData()));
  }

  private static boolean usagePositiveForHour(TallyReportData report, OffsetDateTime hourStart) {
    if (report.getData() == null || report.getData().isEmpty()) {
      return false;
    }
    return report.getData().stream()
        .filter(p -> p.getDate() != null && startOfUtcHour(p.getDate()).isEqual(hourStart))
        .anyMatch(
            p -> p.getValue() != null && p.getValue() > 0 && Boolean.TRUE.equals(p.getHasData()));
  }

  private static boolean zeroMeasurementForHour(TallyReportData report, OffsetDateTime hourStart) {
    if (report.getData() == null || report.getData().isEmpty()) {
      return false;
    }
    return report.getData().stream()
        .filter(p -> p.getDate() != null && startOfUtcHour(p.getDate()).isEqual(hourStart))
        .anyMatch(p -> intValueOrZero(p.getValue()) == 0 && Boolean.TRUE.equals(p.getHasData()));
  }

  private static int intValueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  /** No snapshot/measurements for the period — gap-filled bucket. */
  private static void assertNoSnapshotGapBucket(TallyReportDataPoint point, OffsetDateTime hour) {
    assertEquals(
        0,
        intValueOrZero(point.getValue()),
        "Gap hour " + hour + " must have value 0 when no events exist for that hour");
    assertFalse(
        Boolean.TRUE.equals(point.getHasData()),
        "Gap hour " + hour + " must have has_data=false when no snapshot exists: " + point);
  }

  /** Category contributed measurements for the period. */
  private static void assertCategoryHasMeasurementBucket(
      TallyReportDataPoint point, int expectedValue, OffsetDateTime hour, String category) {
    assertEquals(
        expectedValue,
        intValueOrZero(point.getValue()),
        "category=" + category + " hour " + hour + " expected value");
    assertTrue(
        Boolean.TRUE.equals(point.getHasData()),
        "category="
            + category
            + " hour "
            + hour
            + " must have has_data=true when measurements exist: "
            + point);
  }

  /** Snapshot exists from other categories but this category did not contribute. */
  private static void assertMutedCategoryAtSnapshotHour(
      TallyReportDataPoint point, String category, OffsetDateTime hour) {
    assertEquals(
        0,
        intValueOrZero(point.getValue()),
        "category="
            + category
            + " hour "
            + hour
            + " must have value 0 with no category contribution");
    assertFalse(
        Boolean.TRUE.equals(point.getHasData()),
        "category="
            + category
            + " hour "
            + hour
            + " must have has_data=false when snapshot exists "
            + "but this category has no measurements: "
            + point);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-TC001")
  void shouldIndicateHasDataMatchesCategoryContribution(boolean primaryRowSearches) {
    givenFeatureFlagIsConfigured(primaryRowSearches);

    for (String category : List.of("physical", "virtual", "hypervisor", "cloud")) {
      TallyReportData report =
          getCategoryHourlyReport(firstOrgId, category, positiveRangeStart, positiveEventHourEnd);

      assertFalse(
          hasMisleadingZeroWithHasData(report),
          "Category="
              + category
              + " must not return value=0 with has_data=true when that category contributed nothing.");
    }

    TallyReportData cloudReport =
        getCategoryHourlyReport(firstOrgId, "cloud", positiveRangeStart, positiveEventHourEnd);

    assertNoSnapshotGapBucket(pointForHour(cloudReport, gapHour), gapHour);

    assertCategoryHasMeasurementBucket(
        pointForHour(cloudReport, positiveEventHourStart), 8, positiveEventHourStart, "cloud");

    for (String mutedCategory : List.of("physical", "virtual", "hypervisor")) {
      TallyReportData mutedReport =
          getCategoryHourlyReport(
              firstOrgId, mutedCategory, positiveRangeStart, positiveEventHourEnd);
      assertMutedCategoryAtSnapshotHour(
          pointForHour(mutedReport, positiveEventHourStart), mutedCategory, positiveEventHourStart);
    }
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-TC002")
  void shouldIndicateExistingZeroValueMeasurementsStillReportHasData(boolean primaryRowSearches) {
    givenFeatureFlagIsConfigured(primaryRowSearches);

    String orgId = String.valueOf(10000 + (int) (Math.random() * 90000));
    service.createOptInConfig(orgId);
    publishCloudPaygEvent(orgId, zeroEventHourStart, 0.0f);
    awaitCloudZeroMeasurementReady(orgId);

    TallyReportData cloudReport =
        getCategoryHourlyReport(orgId, "cloud", zeroEventHourStart, zeroEventHourEnd);

    assertCategoryHasMeasurementBucket(
        pointForHour(cloudReport, zeroEventHourStart), 0, zeroEventHourStart, "cloud");

    for (String mutedCategory : List.of("physical", "virtual", "hypervisor")) {
      TallyReportData mutedReport =
          getCategoryHourlyReport(orgId, mutedCategory, zeroEventHourStart, zeroEventHourEnd);
      assertMutedCategoryAtSnapshotHour(
          pointForHour(mutedReport, zeroEventHourStart), mutedCategory, zeroEventHourStart);
    }
  }
}
