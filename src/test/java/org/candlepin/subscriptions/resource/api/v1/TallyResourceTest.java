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
package org.candlepin.subscriptions.resource.api.v1;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.ParameterizedTest.DEFAULT_DISPLAY_NAME;
import static org.junit.jupiter.params.ParameterizedTest.DISPLAY_NAME_PLACEHOLDER;
import static org.mockito.Mockito.*;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contracts.api.resources.CapacityApi;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.test.TestClock;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.SnapshotTimeAdjuster;
import org.candlepin.subscriptions.utilization.api.v1.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;

@SuppressWarnings("linelength")
@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
@Import(TestClockConfiguration.class)
class TallyResourceTest {
  public static final Instant MID_MONTH_INSTANT =
      LocalDateTime.of(2023, 7, 15, 13, 0, 0).toInstant(ZoneOffset.UTC);
  public static final OffsetDateTime TEST_DATE =
      OffsetDateTime.ofInstant(MID_MONTH_INSTANT, ZoneOffset.UTC);

  private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

  public static final ProductId RHEL_PRODUCT_ID = ProductId.fromString("RHEL for x86");
  public static final ProductId OPENSHIFT_DEDICATED_METRICS =
      ProductId.fromString("OpenShift-dedicated-metrics");
  public static final ProductId RHEL_FOR_X86 = RHEL_PRODUCT_ID;
  private static final MetricId METRIC_ID_CORES = MetricId.fromString("Cores");
  private static final MetricId METRIC_ID_SOCKETS = MetricId.fromString("Sockets");

  @MockBean TallySnapshotRepository repository;
  @MockBean PageLinkCreator pageLinkCreator;
  @MockBean OrgConfigRepository orgConfigRepository;
  @MockBean CapacityApi capacityApi;
  @Autowired TallyResource resource;
  @Autowired ApplicationClock applicationClock;

  @BeforeEach
  public void setupTests() {
    when(orgConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
    var testClock = (TestClock) applicationClock.getClock();
    testClock.setInstant(MID_MONTH_INSTANT);
  }

  @Test
  void testTallyReportDataFillerMarksHasDataFalseAfterNowWithRunningTotal() {
    var begin = applicationClock.startOfMonth(TEST_DATE);
    var end = applicationClock.endOfMonth(TEST_DATE);
    List<TallySnapshot> snapshots =
        List.of(1, 2, 8).stream()
            .map(
                i -> {
                  var snapshot = new TallySnapshot();
                  snapshot.setSnapshotDate(
                      OffsetDateTime.of(
                          TEST_DATE.getYear(),
                          TEST_DATE.getMonthValue(),
                          i,
                          12,
                          35,
                          0,
                          0,
                          ZoneOffset.UTC));
                  snapshot.setMeasurement(
                      HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), i * 2.0);
                  return snapshot;
                })
            .collect(Collectors.toList());

    Mockito.when(
            repository.findSnapshot(
                "owner123456",
                RHEL_FOR_X86.toString(),
                Granularity.DAILY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider._ANY,
                "_ANY",
                begin,
                end,
                null))
        .thenReturn(new PageImpl<>(snapshots));

    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            begin,
            end,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            null,
            null,
            null,
            null,
            true,
            null);
    int expected = TEST_DATE.getMonth().length(false);
    assertEquals(expected, response.getMeta().getCount());
    assertEquals(expected, response.getData().size());

    var firstSnapshot = response.getData().get(0);
    assertEquals(2, firstSnapshot.getValue());
    assertTrue(firstSnapshot.getHasData());

    var snapshots2Through7 = response.getData().subList(1, 7);
    snapshots2Through7.forEach(
        snapshot -> {
          assertTrue(snapshot.getHasData());
          assertEquals(6, snapshot.getValue());
        });

    var snapshots8ThroughPresent = response.getData().subList(7, TEST_DATE.getDayOfMonth());
    snapshots8ThroughPresent.forEach(
        snapshot -> {
          assertTrue(snapshot.getHasData());
          assertEquals(22, snapshot.getValue());
        });

    var snapshotsPresentToEndOfMonth =
        response.getData().subList(TEST_DATE.getDayOfMonth(), TEST_DATE.getMonth().length(false));
    snapshotsPresentToEndOfMonth.forEach(
        snapshot -> {
          assertFalse(snapshot.getHasData());
          assertEquals(0, snapshot.getValue());
        });

    assertEquals(22, response.getMeta().getTotalMonthly().getValue());
  }

  @Test
  void testTallyReportDataTotalUsingHardwareMeasurements() {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setOrgId("org123");
    ;
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    snapshot.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 4.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-10-01T00:00Z"),
            OffsetDateTime.parse("2021-10-30T00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @Test
  void testTallyReportDataTotalUsingTallyMeasurements() {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    snapshot.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 4.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-10-01T00:00Z"),
            OffsetDateTime.parse("2021-10-30T00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @EnumSource
  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  void testTallyReportDataCategoriesUsingHardwareMeasurements(ReportCategory category) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    for (HardwareMeasurementType hardwareMeasurementType : HardwareMeasurementType.values()) {
      snapshot.setMeasurement(hardwareMeasurementType, MetricIdUtils.getCores(), 4.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-10-01T00:00Z"),
            OffsetDateTime.parse("2021-10-30T00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @EnumSource
  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  void testTallyReportDataCategoriesUsingTallyMeasurements(ReportCategory category) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    for (HardwareMeasurementType hardwareMeasurementType : HardwareMeasurementType.values()) {
      snapshot.setMeasurement(hardwareMeasurementType, MetricIdUtils.getCores(), 4.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-10-01T00:00Z"),
            OffsetDateTime.parse("2021-10-30T00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @Test
  void testTallyReportDataReportFiller() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-10-01T00:00Z"),
            OffsetDateTime.parse("2021-10-30T00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertEquals(30, response.getMeta().getCount());
    assertEquals(30, response.getData().size());
  }

  @Test
  void testTallyReportTotalMonthlyNotPopulatedWhenQueryIsNotBeginningOfMonth() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-11-02T00:00Z"),
            OffsetDateTime.parse("2021-11-30T23:59:59.999Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertNull(response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyNotPopulatedWhenQueryIsNotEndOfMonth() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-11-01T00:00Z"),
            OffsetDateTime.parse("2021-11-24T23:59:59.999Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    assertNull(response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyNotPopulatedWhenQueryIsPaged() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-11-01T00:00Z"),
            OffsetDateTime.parse("2021-11-30T23:59:59.999Z"),
            null,
            null,
            null,
            null,
            null,
            0,
            10,
            false,
            null);
    assertNull(response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyPopulatedWithNoUnderlyingData() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-11-01T00:00Z"),
            OffsetDateTime.parse("2021-11-30T23:59:59.999Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    TallyReportTotalMonthly expectedTotalMonthly =
        new TallyReportTotalMonthly().date(null).value(0).hasData(false);
    assertEquals(expectedTotalMonthly, response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyPopulatedWithExistingUnderlyingData() {
    TallySnapshot snapshot1 = new TallySnapshot();
    snapshot1.setSnapshotDate(OffsetDateTime.parse("2021-11-02T00:00Z"));
    snapshot1.setGranularity(Granularity.DAILY);
    snapshot1.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 4.0);
    TallySnapshot snapshot2 = new TallySnapshot();
    snapshot2.setSnapshotDate(OffsetDateTime.parse("2021-11-03T00:00Z"));
    snapshot2.setGranularity(Granularity.DAILY);
    snapshot2.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 3.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot1, snapshot2)));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-11-01T00:00Z"),
            OffsetDateTime.parse("2021-11-30T23:59:59.999Z"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);
    TallyReportTotalMonthly expectedTotalMonthly =
        new TallyReportTotalMonthly()
            .date(OffsetDateTime.parse("2021-11-03T00:00Z"))
            .value(7)
            .hasData(true);
    assertEquals(expectedTotalMonthly, response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyPopulatedWithBillingProvider() {
    TallySnapshot snapshot1 = new TallySnapshot();
    snapshot1.setSnapshotDate(OffsetDateTime.parse("2021-11-02T00:00Z"));
    snapshot1.setGranularity(Granularity.DAILY);
    snapshot1.setBillingProvider(BillingProvider.RED_HAT);
    snapshot1.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 4.0);
    TallySnapshot snapshot2 = new TallySnapshot();
    snapshot2.setSnapshotDate(OffsetDateTime.parse("2021-11-03T00:00Z"));
    snapshot2.setGranularity(Granularity.DAILY);
    snapshot2.setBillingProvider(BillingProvider.RED_HAT);
    snapshot2.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 3.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot1, snapshot2)));
    TallyReportData response =
        resource.getTallyReportData(
            RHEL_FOR_X86,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2021-11-01T00:00Z"),
            OffsetDateTime.parse("2021-11-30T23:59:59.999Z"),
            null,
            null,
            null,
            BillingProviderType.RED_HAT,
            null,
            null,
            null,
            false,
            null);
    TallyReportTotalMonthly expectedTotalMonthly =
        new TallyReportTotalMonthly()
            .date(OffsetDateTime.parse("2021-11-03T00:00Z"))
            .value(7)
            .hasData(true);
    assertEquals(expectedTotalMonthly, response.getMeta().getTotalMonthly());
    assertEquals(BillingProviderType.RED_HAT, response.getMeta().getBillingProvider());
  }

  @Test
  void testRunningTotalFormatUsedForNewerTallyAPI() {
    List<TallySnapshot> snapshots =
        List.of(1, 2, 8).stream()
            .map(
                i -> {
                  var snapshot = new TallySnapshot();
                  snapshot.setSnapshotDate(
                      OffsetDateTime.of(2023, 3, i, 12, 35, 0, 0, ZoneOffset.UTC));
                  snapshot.setMeasurement(
                      HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), i * 2.0);
                  return snapshot;
                })
            .collect(Collectors.toList());

    TallyReportTotalMonthly expectedTotalMonthly =
        new TallyReportTotalMonthly()
            .date(OffsetDateTime.parse("2023-03-08T12:35Z"))
            .value(22)
            .hasData(true);

    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(snapshots));

    TallyReportData report =
        resource.getTallyReportData(
            OPENSHIFT_DEDICATED_METRICS,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2023-03-01T00:00Z"),
            OffsetDateTime.parse("2023-03-31T23:59:59.999Z"),
            null,
            null,
            null,
            BillingProviderType.RED_HAT,
            null,
            null,
            null,
            true,
            null);
    assertEquals(31, report.getData().size());

    var firstSnapshot = report.getData().get(0);
    assertEquals(2, firstSnapshot.getValue());

    var secondSnapshot = report.getData().get(1);
    assertEquals(6, secondSnapshot.getValue());

    // Snapshot for day with no usage should still show running total
    var snapshotWithNoNewUsage = report.getData().get(2);
    assertEquals(6, snapshotWithNoNewUsage.getValue());

    var thirdSnapshot = report.getData().get(7);
    assertEquals(22, thirdSnapshot.getValue());

    assertEquals(expectedTotalMonthly, report.getMeta().getTotalMonthly());
  }

  @Test
  void testMonthlyTotalsRoundedUpToNearestInteger() {
    List<TallySnapshot> snapshots =
        List.of(1, 2).stream()
            .map(
                i -> {
                  var snapshot = new TallySnapshot();
                  snapshot.setSnapshotDate(
                      OffsetDateTime.of(2023, 3, i, 12, 35, 0, 0, ZoneOffset.UTC));
                  snapshot.setMeasurement(
                      HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 1.3);
                  return snapshot;
                })
            .collect(Collectors.toList());

    TallyReportTotalMonthly expectedTotalMonthly =
        new TallyReportTotalMonthly()
            .date(OffsetDateTime.parse("2023-03-02T12:35Z"))
            .value(3)
            .hasData(true);

    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(snapshots));

    TallyReportData report =
        resource.getTallyReportData(
            OPENSHIFT_DEDICATED_METRICS,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2023-03-01T00:00Z"),
            OffsetDateTime.parse("2023-03-31T23:59:59.999Z"),
            null,
            null,
            null,
            BillingProviderType.RED_HAT,
            null,
            null,
            null,
            true,
            null);
    assertEquals(31, report.getData().size());

    // Each running total entry should be the Math.ceil of all the total previous snapshot values
    // Rounding should only occur after the total has been calculated
    var firstSnapshot = report.getData().get(0);
    assertEquals((int) Math.ceil(1.3), firstSnapshot.getValue());

    var secondSnapshot = report.getData().get(1);
    assertEquals((int) Math.ceil(1.3 + 1.3), secondSnapshot.getValue());

    assertEquals(expectedTotalMonthly, report.getMeta().getTotalMonthly());
  }

  @Test
  void testBadRequestWhenBillingCategorySpecifiedWhenRunningTotalsParamIsNull() {
    OffsetDateTime beginning = OffsetDateTime.parse("2021-10-01T00:00Z");
    OffsetDateTime ending = OffsetDateTime.parse("2021-10-30T00:00Z");
    assertThrows(
        BadRequestException.class,
        () -> {
          resource.getTallyReportData(
              RHEL_FOR_X86,
              METRIC_ID_CORES,
              GranularityType.DAILY,
              beginning,
              ending,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              BillingCategory.ON_DEMAND);
        });
  }

  @Test
  void testBadRequestWhenBillingCategorySpecifiedWhenRunningTotalsParamIsFalse() {
    OffsetDateTime beginning = OffsetDateTime.parse("2021-10-01T00:00Z");
    OffsetDateTime ending = OffsetDateTime.parse("2021-10-30T00:00Z");
    assertThrows(
        BadRequestException.class,
        () -> {
          resource.getTallyReportData(
              RHEL_FOR_X86,
              METRIC_ID_CORES,
              GranularityType.DAILY,
              beginning,
              ending,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              false,
              BillingCategory.ON_DEMAND);
        });
  }

  @Test
  void testRunningTotalForOnDemand() throws Exception {
    OffsetDateTime snap1Date = OffsetDateTime.of(2023, 3, 4, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap2Date = OffsetDateTime.of(2023, 3, 5, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap3Date = OffsetDateTime.of(2023, 3, 6, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap4Date = OffsetDateTime.of(2023, 3, 7, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap5Date = OffsetDateTime.of(2023, 3, 8, 0, 0, 0, 0, ZoneOffset.UTC);

    List<OffsetDateTime> snapDates = List.of(snap1Date, snap2Date, snap3Date, snap4Date, snap5Date);
    List<TallySnapshot> snapshots = new LinkedList<>();
    for (OffsetDateTime nextDate : snapDates) {
      TallySnapshot snap =
          TallySnapshot.builder()
              .productId(OPENSHIFT_DEDICATED_METRICS.toString())
              .snapshotDate(nextDate)
              .build();
      snap.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 100.0);
      snapshots.add(snap);
    }

    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(snapshots));

    mockCapacity(
        OPENSHIFT_DEDICATED_METRICS,
        METRIC_ID_CORES,
        GranularityType.DAILY,
        OffsetDateTime.parse("2023-03-01T00:00Z"),
        OffsetDateTime.parse("2023-03-31T23:59:59.999Z"),
        null,
        null,
        null,
        Map.of(
            snap2Date, 100,
            snap3Date, 100,
            snap4Date, 100,
            snap5Date, 150));

    TallyReportTotalMonthly expectedTotalMonthly =
        new TallyReportTotalMonthly().date(snap5Date).value(500).hasData(true);

    TallyReportData report =
        resource.getTallyReportData(
            OPENSHIFT_DEDICATED_METRICS,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2023-03-01T00:00Z"),
            OffsetDateTime.parse("2023-03-31T23:59:59.999Z"),
            null,
            null,
            null,
            BillingProviderType.RED_HAT,
            null,
            null,
            null,
            true,
            BillingCategory.ON_DEMAND);
    assertEquals(31, report.getData().size());

    int snapshotIndex = 2; //
    var beforeUsage = report.getData().get(snapshotIndex);
    assertEquals(0, beforeUsage.getValue());

    var noAppliedCapacity = report.getData().get(snapshotIndex + 1);
    assertEquals(100, noAppliedCapacity.getValue());

    var firstSnapshot = report.getData().get(snapshotIndex + 2);
    assertEquals(100, firstSnapshot.getValue());

    var secondSnapshot = report.getData().get(snapshotIndex + 3);
    assertEquals(200, secondSnapshot.getValue());

    var thirdSnapshot = report.getData().get(snapshotIndex + 4);
    assertEquals(300, thirdSnapshot.getValue());

    var fourthSnapshot = report.getData().get(snapshotIndex + 5);
    assertEquals(350, fourthSnapshot.getValue());

    assertEquals(expectedTotalMonthly, report.getMeta().getTotalMonthly());
  }

  @Test
  void testRunningTotalForPrePaid() throws Exception {
    OffsetDateTime snap1Date = OffsetDateTime.of(2023, 3, 4, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap2Date = OffsetDateTime.of(2023, 3, 5, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap3Date = OffsetDateTime.of(2023, 3, 6, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap4Date = OffsetDateTime.of(2023, 3, 7, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime snap5Date = OffsetDateTime.of(2023, 3, 8, 0, 0, 0, 0, ZoneOffset.UTC);

    List<OffsetDateTime> snapDates = List.of(snap1Date, snap2Date, snap3Date, snap4Date, snap5Date);
    List<TallySnapshot> snapshots = new LinkedList<>();
    for (OffsetDateTime nextDate : snapDates) {
      TallySnapshot snap =
          TallySnapshot.builder()
              .productId(OPENSHIFT_DEDICATED_METRICS.toString())
              .snapshotDate(nextDate)
              .build();
      snap.setMeasurement(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores(), 100.0);
      snapshots.add(snap);
    }

    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(snapshots));

    mockCapacity(
        OPENSHIFT_DEDICATED_METRICS,
        METRIC_ID_CORES,
        GranularityType.DAILY,
        OffsetDateTime.parse("2023-03-01T00:00Z"),
        OffsetDateTime.parse("2023-03-31T23:59:59.999Z"),
        null,
        null,
        null,
        Map.of(
            snap2Date, 200,
            snap3Date, 200,
            snap4Date, 500,
            snap5Date, 500));

    TallyReportDataPoint expectedTotalMonthly =
        new TallyReportDataPoint().date(snap5Date).value(500).hasData(true);

    TallyReportData report =
        resource.getTallyReportData(
            OPENSHIFT_DEDICATED_METRICS,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            OffsetDateTime.parse("2023-03-01T00:00Z"),
            OffsetDateTime.parse("2023-03-31T23:59:59.999Z"),
            null,
            null,
            null,
            BillingProviderType.RED_HAT,
            null,
            null,
            null,
            true,
            BillingCategory.PREPAID);
    assertEquals(31, report.getData().size());

    int snapshotIndex = 2; //
    var beforeUsage = report.getData().get(snapshotIndex);
    assertEquals(0, beforeUsage.getValue());

    var noAppliedCapacity = report.getData().get(snapshotIndex + 1);
    assertEquals(0, noAppliedCapacity.getValue());

    var firstSnapshot = report.getData().get(snapshotIndex + 2);
    assertEquals(200, firstSnapshot.getValue());

    var secondSnapshot = report.getData().get(snapshotIndex + 3);
    assertEquals(200, secondSnapshot.getValue());

    var thirdSnapshot = report.getData().get(snapshotIndex + 4);
    assertEquals(400, thirdSnapshot.getValue());

    var fourthSnapshot = report.getData().get(snapshotIndex + 5);
    assertEquals(500, fourthSnapshot.getValue());

    var noUsage2 = report.getData().get(snapshotIndex + 5);
    assertEquals(500, noUsage2.getValue());
  }

  private void mockCapacity(
      ProductId productId,
      MetricId metricId,
      GranularityType granularityType,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      ReportCategory category,
      ServiceLevelType sla,
      UsageType usageType,
      Map<OffsetDateTime, Integer> result)
      throws Exception {
    com.redhat.swatch.contracts.api.model.GranularityType cGranularity =
        com.redhat.swatch.contracts.api.model.GranularityType.valueOf(granularityType.name());
    com.redhat.swatch.contracts.api.model.ReportCategory cReportCategory =
        Optional.ofNullable(category)
            .map(c -> com.redhat.swatch.contracts.api.model.ReportCategory.valueOf(c.name()))
            .orElse(null);
    com.redhat.swatch.contracts.api.model.ServiceLevelType cSla =
        Optional.ofNullable(sla)
            .map(s -> com.redhat.swatch.contracts.api.model.ServiceLevelType.valueOf(s.name()))
            .orElse(null);
    com.redhat.swatch.contracts.api.model.UsageType cUsage =
        Optional.ofNullable(usageType)
            .map(ut -> com.redhat.swatch.contracts.api.model.UsageType.valueOf(ut.name()))
            .orElse(null);

    when(capacityApi.getCapacityReportByMetricId(
            eq(productId.getValue()),
            eq(metricId.getValue()),
            eq(cGranularity),
            eq(beginning),
            eq(ending),
            any(),
            any(),
            eq(cReportCategory),
            eq(cSla),
            eq(cUsage)))
        .thenReturn(
            capacityReport(
                beginning,
                ending,
                metricId.getValue(),
                cReportCategory,
                cGranularity,
                cSla,
                cUsage,
                result));
  }

  private com.redhat.swatch.contracts.api.model.CapacityReportByMetricId capacityReport(
      OffsetDateTime start,
      OffsetDateTime end,
      String metricId,
      com.redhat.swatch.contracts.api.model.ReportCategory category,
      com.redhat.swatch.contracts.api.model.GranularityType granularity,
      com.redhat.swatch.contracts.api.model.ServiceLevelType sla,
      com.redhat.swatch.contracts.api.model.UsageType usage,
      Map<OffsetDateTime, Integer> values) {
    var meta =
        new com.redhat.swatch.contracts.api.model.CapacityReportByMetricIdMeta()
            .metricId(metricId)
            .category(category)
            .granularity(granularity)
            .serviceLevel(sla)
            .usage(usage)
            .count(values.size());
    return new com.redhat.swatch.contracts.api.model.CapacityReportByMetricId()
        .meta(meta)
        .data(createCapacitySnapshots(start, end, granularity, values));
  }

  private List<com.redhat.swatch.contracts.api.model.CapacitySnapshotByMetricId>
      createCapacitySnapshots(
          OffsetDateTime reportStart,
          OffsetDateTime reportEnd,
          com.redhat.swatch.contracts.api.model.GranularityType granularityType,
          Map<OffsetDateTime, Integer> values) {
    SnapshotTimeAdjuster timeAdjuster =
        SnapshotTimeAdjuster.getTimeAdjuster(
            applicationClock, Granularity.fromString(granularityType.toString()));

    OffsetDateTime start = timeAdjuster.adjustToPeriodStart(reportStart);
    OffsetDateTime end = timeAdjuster.adjustToPeriodEnd(reportEnd);
    TemporalAmount offset = timeAdjuster.getSnapshotOffset();

    List<com.redhat.swatch.contracts.api.model.CapacitySnapshotByMetricId> result =
        new ArrayList<>();
    OffsetDateTime next = OffsetDateTime.from(start);

    while (next.isBefore(end) || next.isEqual(end)) {
      Integer value = values.getOrDefault(next, 0);
      result.add(
          new com.redhat.swatch.contracts.api.model.CapacitySnapshotByMetricId()
              .hasData(values.containsKey(next))
              .date(next)
              .hasInfiniteQuantity(false)
              .value(value));
      next = timeAdjuster.adjustToPeriodStart(next.plus(offset));
    }
    return result;
  }
}
