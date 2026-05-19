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
import com.redhat.swatch.component.tests.utils.RandomUtils;
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
  private static OffsetDateTime positiveEventHourStart;
  private static OffsetDateTime positiveEventHourEnd;
  private static OffsetDateTime positiveRangeStart;
  private static OffsetDateTime gapHour;
  private static OffsetDateTime zeroEventHourStart;
  private static OffsetDateTime zeroEventHourEnd;

  private static String dataGapOrgId;
  private static OffsetDateTime dataGapBaseTime;
  private static OffsetDateTime dataGapRangeBeginning;
  private static OffsetDateTime dataGapRangeEnding;

  @BeforeAll
  static void givenCategoryHasDataPaygScenariosReady() {
    OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
    positiveEventHourStart = base.minusHours(2);
    positiveEventHourEnd = positiveEventHourStart.plusHours(1).minusNanos(1);
    positiveRangeStart = positiveEventHourStart.minusHours(4);
    gapHour = positiveEventHourStart.minusHours(2);

    zeroEventHourStart = base.minusHours(6);
    zeroEventHourEnd = zeroEventHourStart.plusHours(1).minusNanos(1);

    firstOrgId = RandomUtils.generateRandom();
    service.createOptInConfig(firstOrgId);
    givenCloudPaygEventPublished(firstOrgId, positiveEventHourStart, 8.0f);
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(firstOrgId);
          if (!cloudUsagePositiveForHour(
              fetchCategoryHourlyReport(
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

    dataGapBaseTime = base.minusHours(10);
    dataGapRangeBeginning = dataGapBaseTime;
    dataGapRangeEnding = dataGapBaseTime.plusHours(4).minusNanos(1);
    dataGapOrgId = RandomUtils.generateRandom();
    service.createOptInConfig(dataGapOrgId);
    givenCloudPaygEventPublished(dataGapOrgId, dataGapBaseTime, 10.0f);
    givenCloudPaygEventPublished(dataGapOrgId, dataGapBaseTime.plusHours(2), 20.0f);
    service.performHourlyTallyForOrg(dataGapOrgId);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-TC001")
  void shouldIndicateHasDataMatchesCategoryContribution(boolean primaryRowSearches) {
    // Given: Positive cloud PAYG is tallied and the primary-row flag is configured
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Hourly category reports are fetched for the event window
    TallyReportData cloudReport =
        fetchCategoryHourlyReport(firstOrgId, "cloud", positiveRangeStart, positiveEventHourEnd);

    // Then: No category reports value=0 with has_data=true without contributing
    for (String category : List.of("physical", "virtual", "hypervisor", "cloud")) {
      TallyReportData report =
          fetchCategoryHourlyReport(firstOrgId, category, positiveRangeStart, positiveEventHourEnd);
      assertFalse(
          hasMisleadingZeroWithHasData(report),
          "Category="
              + category
              + " must not return value=0 with has_data=true when that category "
              + "contributed nothing.");
    }

    thenNoSnapshotGapBucket(pointForHour(cloudReport, gapHour), gapHour);
    thenCategoryHasMeasurementBucket(
        pointForHour(cloudReport, positiveEventHourStart),
        8,
        positiveEventHourStart,
        "cloud");

    for (String mutedCategory : List.of("physical", "virtual", "hypervisor")) {
      TallyReportData mutedReport =
          fetchCategoryHourlyReport(
              firstOrgId, mutedCategory, positiveRangeStart, positiveEventHourEnd);
      thenMutedCategoryAtSnapshotHour(
          pointForHour(mutedReport, positiveEventHourStart),
          mutedCategory,
          positiveEventHourStart);
    }
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-TC002")
  void shouldIndicateExistingZeroValueMeasurementsStillReportHasData(
      boolean primaryRowSearches) {
    // Given: Zero cloud PAYG is published and tallied
    givenFeatureFlagIsConfigured(primaryRowSearches);
    String orgId = RandomUtils.generateRandom();
    service.createOptInConfig(orgId);
    givenCloudPaygEventPublished(orgId, zeroEventHourStart, 0.0f);
    givenZeroCloudMeasurementReadyForOrg(orgId);

    // When: Hourly category reports are fetched for the zero event hour
    TallyReportData cloudReport =
        fetchCategoryHourlyReport(orgId, "cloud", zeroEventHourStart, zeroEventHourEnd);

    // Then: Cloud reports zero with has_data=true; muted categories report has_data=false
    thenCategoryHasMeasurementBucket(
        pointForHour(cloudReport, zeroEventHourStart), 0, zeroEventHourStart, "cloud");

    for (String mutedCategory : List.of("physical", "virtual", "hypervisor")) {
      TallyReportData mutedReport =
          fetchCategoryHourlyReport(orgId, mutedCategory, zeroEventHourStart, zeroEventHourEnd);
      thenMutedCategoryAtSnapshotHour(
          pointForHour(mutedReport, zeroEventHourStart), mutedCategory, zeroEventHourStart);
    }
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-TC003")
  void shouldIndicateDataGapsWithHasDataField(boolean primaryRowSearches) {
    // Given: Events at hours 0 and 2 are tallied; hours 1 and 3 are gaps
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Unfiltered hourly report is fetched for the four-hour range
    TallyReportData response =
        AwaitilityUtils.until(
            () ->
                fetchUnfilteredHourlyReport(
                    dataGapOrgId, dataGapRangeBeginning, dataGapRangeEnding),
            data -> data.getData() != null && !data.getData().isEmpty());

    OffsetDateTime hour0 = dataGapBaseTime;
    OffsetDateTime hour1 = hour0.plusHours(1);
    OffsetDateTime hour2 = hour0.plusHours(2);
    OffsetDateTime hour3 = hour0.plusHours(3);

    // Then: Event hours report usage; gap hours report value=0 and has_data=false
    thenCategoryHasMeasurementBucket(pointForHour(response, hour0), 10, hour0, "report");
    thenCategoryHasMeasurementBucket(pointForHour(response, hour2), 20, hour2, "report");
    thenNoSnapshotGapBucket(pointForHour(response, hour1), hour1);
    thenNoSnapshotGapBucket(pointForHour(response, hour3), hour3);

    assertFalse(
        hasMisleadingZeroWithHasData(response),
        "Unfiltered report must not pair value=0 with has_data=true on gap-filled hours");
  }

  private static void givenCloudPaygEventPublished(
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

  private static void givenZeroCloudMeasurementReadyForOrg(String orgId) {
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);
          if (!cloudZeroMeasurementForHour(
              fetchCategoryHourlyReport(orgId, "cloud", zeroEventHourStart, zeroEventHourEnd),
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

  private static void thenNoSnapshotGapBucket(TallyReportDataPoint point, OffsetDateTime hour) {
    assertEquals(
        0,
        intValueOrZero(point.getValue()),
        "Gap hour " + hour + " must have value 0 when no events exist for that hour");
    assertFalse(
        Boolean.TRUE.equals(point.getHasData()),
        "Gap hour " + hour + " must have has_data=false when no snapshot exists: " + point);
  }

  private static void thenCategoryHasMeasurementBucket(
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

  private static void thenMutedCategoryAtSnapshotHour(
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

  private static TallyReportData fetchCategoryHourlyReport(
      String orgId, String category, OffsetDateTime beginning, OffsetDateTime ending) {
    Map<String, Object> params = new HashMap<>();
    params.put("granularity", "Hourly");
    params.put("beginning", beginning.toString());
    params.put("ending", ending.toString());
    params.put("sla", ServiceLevelType.PREMIUM.toString());
    params.put("category", category);
    return service.getTallyReportData(orgId, PRODUCT_TAG, METRIC_ID, params);
  }

  private static TallyReportData fetchUnfilteredHourlyReport(
      String orgId, OffsetDateTime beginning, OffsetDateTime ending) {
    Map<String, Object> params = new HashMap<>();
    params.put("granularity", "Hourly");
    params.put("beginning", beginning.toString());
    params.put("ending", ending.toString());
    return service.getTallyReportData(orgId, PRODUCT_TAG, METRIC_ID, params);
  }

  private static OffsetDateTime startOfUtcHour(OffsetDateTime timestamp) {
    return timestamp.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
  }

  private static int intValueOrZero(Integer value) {
    return value == null ? 0 : value;
  }

  private static TallyReportDataPoint pointForHour(
      TallyReportData report, OffsetDateTime hourStart) {
    assertNotNull(report.getData(), "report data");
    return report.getData().stream()
        .filter(
            point ->
                point.getDate() != null && startOfUtcHour(point.getDate()).isEqual(hourStart))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No data point for hour " + hourStart));
  }

  private static boolean cloudUsagePositiveForHour(
      TallyReportData report, OffsetDateTime hourStart) {
    if (report.getData() == null || report.getData().isEmpty()) {
      return false;
    }
    return report.getData().stream()
        .filter(
            point ->
                point.getDate() != null
                    && startOfUtcHour(point.getDate()).isEqual(hourStart))
        .anyMatch(
            point ->
                point.getValue() != null
                    && point.getValue() > 0
                    && Boolean.TRUE.equals(point.getHasData()));
  }

  private static boolean cloudZeroMeasurementForHour(
      TallyReportData report, OffsetDateTime hourStart) {
    if (report.getData() == null || report.getData().isEmpty()) {
      return false;
    }
    return report.getData().stream()
        .filter(
            point ->
                point.getDate() != null && startOfUtcHour(point.getDate()).isEqual(hourStart))
        .anyMatch(
            point ->
                intValueOrZero(point.getValue()) == 0
                    && Boolean.TRUE.equals(point.getHasData()));
  }

  private static boolean hasMisleadingZeroWithHasData(TallyReportData report) {
    if (report.getData() == null) {
      return false;
    }
    return report.getData().stream()
        .anyMatch(
            point ->
                point.getValue() != null
                    && point.getValue() == 0
                    && Boolean.TRUE.equals(point.getHasData()));
  }
}
