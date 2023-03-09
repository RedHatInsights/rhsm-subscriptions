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
package org.candlepin.subscriptions.resource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.ParameterizedTest.DEFAULT_DISPLAY_NAME;
import static org.junit.jupiter.params.ParameterizedTest.DISPLAY_NAME_PLACEHOLDER;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.RoleProvider;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SuppressWarnings("linelength")
@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
@Import(FixedClockConfiguration.class)
class TallyResourceTest {

  public static final ProductId RHEL_PRODUCT_ID = ProductId.RHEL;
  public static final String INVALID_PRODUCT_ID_VALUE = "bad_product";
  private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);
  @MockBean TallySnapshotRepository repository;
  @MockBean PageLinkCreator pageLinkCreator;
  @MockBean AccountConfigRepository accountConfigRepository;
  @Autowired TallyResource resource;

  @BeforeEach
  public void setupTests() {
    when(accountConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  @Test
  void doesNotAllowReportsForUnsupportedGranularity() {
    assertThrows(
        BadRequestException.class,
        () ->
            resource.getTallyReport(
                RHEL_PRODUCT_ID,
                GranularityType.HOURLY,
                min,
                max,
                10,
                10,
                null,
                UsageType.PRODUCTION,
                false));
  }

  @Test
  void testNullSlaQueryParameter() {
    TallySnapshot snap = new TallySnapshot();

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel._ANY),
                Mockito.eq(Usage.PRODUCTION),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(Arrays.asList(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            null,
            UsageType.PRODUCTION,
            false);
    assertEquals(1, report.getData().size());

    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);

    assertMetadata(
        report.getMeta(),
        RHEL_PRODUCT_ID,
        null,
        UsageType.PRODUCTION,
        GranularityType.DAILY,
        1,
        0.0);
  }

  private void assertMetadata(
      TallyReportMeta meta,
      ProductId expectedProduct,
      ServiceLevelType expectedSla,
      UsageType expectedUsage,
      GranularityType expectedGranularity,
      Integer expectedCount,
      Double expectedTotalCoreHours) {

    assertEquals(expectedProduct, meta.getProduct());
    assertEquals(expectedSla, meta.getServiceLevel());
    assertEquals(expectedUsage, meta.getUsage());
    assertEquals(expectedCount, meta.getCount());
    assertEquals(expectedGranularity, meta.getGranularity());
    assertEquals(expectedTotalCoreHours, meta.getTotalCoreHours());
  }

  @Test
  void testNullUsageQueryParameter() {
    TallySnapshot snap = new TallySnapshot();

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage._ANY),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            ServiceLevelType.PREMIUM,
            null,
            false);
    assertEquals(1, report.getData().size());
    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel.PREMIUM,
            Usage._ANY,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);

    assertMetadata(
        report.getMeta(),
        RHEL_PRODUCT_ID,
        ServiceLevelType.PREMIUM,
        null,
        GranularityType.DAILY,
        1,
        0.0);
  }

  @Test
  @SuppressWarnings({"linelength", "indentation"})
  void testUnsetSlaQueryParameter() {
    TallySnapshot snap = new TallySnapshot();

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.EMPTY),
                Mockito.eq(Usage.PRODUCTION),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(Arrays.asList(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            ServiceLevelType.EMPTY,
            UsageType.PRODUCTION,
            false);
    assertEquals(1, report.getData().size());

    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel.EMPTY,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);

    assertMetadata(
        report.getMeta(),
        RHEL_PRODUCT_ID,
        ServiceLevelType.EMPTY,
        UsageType.PRODUCTION,
        GranularityType.DAILY,
        1,
        0.0);
  }

  @Test
  void testUnsetUsageQueryParameter() {
    TallySnapshot snap = new TallySnapshot();

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage.EMPTY),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(Arrays.asList(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            ServiceLevelType.PREMIUM,
            UsageType.EMPTY,
            false);
    assertEquals(1, report.getData().size());

    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel.PREMIUM,
            Usage.EMPTY,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);

    assertMetadata(
        report.getMeta(),
        RHEL_PRODUCT_ID,
        ServiceLevelType.PREMIUM,
        UsageType.EMPTY,
        GranularityType.DAILY,
        1,
        0.0);
  }

  @Test
  void testSetSlaAndUsageQueryParameters() {
    TallySnapshot snap = new TallySnapshot();

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage.PRODUCTION),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(Arrays.asList(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            false);
    assertEquals(1, report.getData().size());

    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);

    assertMetadata(
        report.getMeta(),
        RHEL_PRODUCT_ID,
        ServiceLevelType.PREMIUM,
        UsageType.PRODUCTION,
        GranularityType.DAILY,
        1,
        0.0);
  }

  @Test
  void testRunningTotalFormatUsedForPaygProducts() {
    List<TallySnapshot> snapshots =
        List.of(1, 2, 8).stream()
            .map(
                i -> {
                  var snapshot = new TallySnapshot();
                  snapshot.setSnapshotDate(
                      OffsetDateTime.of(2019, 5, i, 12, 35, 0, 0, ZoneOffset.UTC));
                  snapshot.setMeasurement(
                      HardwareMeasurementType.TOTAL, Measurement.Uom.CORES, i * 2.0);
                  return snapshot;
                })
            .collect(Collectors.toList());

    Mockito.when(
            repository.findSnapshot(
                "owner123456",
                ProductId.OPENSHIFT_DEDICATED_METRICS.toString(),
                Granularity.DAILY,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider._ANY,
                "_ANY",
                OffsetDateTime.parse("2019-05-01T00:00Z"),
                OffsetDateTime.parse("2019-05-31T11:59:59.999Z"),
                null))
        .thenReturn(new PageImpl<>(snapshots));

    TallyReport report =
        resource.getTallyReport(
            ProductId.OPENSHIFT_DEDICATED_METRICS,
            GranularityType.DAILY,
            OffsetDateTime.parse("2019-05-01T00:00Z"),
            OffsetDateTime.parse("2019-05-31T11:59:59.999Z"),
            null,
            null,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            true);
    assertEquals(31, report.getData().size());

    var firstSnapshot = report.getData().get(0);
    assertEquals(2.0, firstSnapshot.getCoreHours());

    var snapshots2Through7 = report.getData().subList(1, 7);
    snapshots2Through7.forEach(snapshot -> assertEquals(6.0, snapshot.getCoreHours()));

    var snapshots8Through24 = report.getData().subList(7, 24);
    snapshots8Through24.forEach(snapshot -> assertEquals(22.0, snapshot.getCoreHours()));

    var futureSnapshots = report.getData().subList(24, 31);
    futureSnapshots.forEach(snapshot -> assertNull(snapshot.getCoreHours()));

    assertEquals("22.0", report.getMeta().getTotalCoreHours().toString());
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() throws Exception {
    TallySnapshot snap = new TallySnapshot();

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage.PRODUCTION),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(Arrays.asList(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            false);
    assertEquals(1, report.getData().size());

    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);
  }

  @Test
  void testShouldPopulateTotalInstanceHours() throws Exception {
    TallySnapshot snap = new TallySnapshot();
    snap.setMeasurement(HardwareMeasurementType.TOTAL, Uom.INSTANCE_HOURS, 42.0);

    Mockito.when(
            repository.findSnapshot(
                Mockito.eq("owner123456"),
                Mockito.eq(RHEL_PRODUCT_ID.toString()),
                Mockito.eq(Granularity.DAILY),
                Mockito.eq(ServiceLevel.PREMIUM),
                Mockito.eq(Usage.PRODUCTION),
                Mockito.eq(BillingProvider._ANY),
                Mockito.eq("_ANY"),
                Mockito.eq(min),
                Mockito.eq(max),
                Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(Arrays.asList(snap)));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID,
            GranularityType.DAILY,
            min,
            max,
            10,
            10,
            ServiceLevelType.PREMIUM,
            UsageType.PRODUCTION,
            false);
    assertEquals(1, report.getData().size());
    assertEquals(42.0, report.getMeta().getTotalInstanceHours());

    Pageable expectedPageable = PageRequest.of(1, 10);
    Mockito.verify(repository)
        .findSnapshot(
            "owner123456",
            RHEL_PRODUCT_ID.toString(),
            Granularity.DAILY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            min,
            max,
            expectedPageable);
  }

  @Test
  void testShouldThrowExceptionOnBadOffset() throws IOException {
    SubscriptionsException e =
        assertThrows(
            SubscriptionsException.class,
            () ->
                resource.getTallyReport(
                    RHEL_PRODUCT_ID,
                    GranularityType.DAILY,
                    min,
                    max,
                    11,
                    10,
                    ServiceLevelType.PREMIUM,
                    UsageType.PRODUCTION,
                    false));
    assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void reportDataShouldGetFilledWhenPagingParametersAreNotPassed() {
    Mockito.when(
            repository.findSnapshot(
                "owner123456",
                RHEL_PRODUCT_ID.toString(),
                Granularity.DAILY,
                ServiceLevel._ANY,
                Usage._ANY,
                BillingProvider._ANY,
                "_ANY",
                min,
                max,
                null))
        .thenReturn(new PageImpl<>(Collections.emptyList()));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID, GranularityType.DAILY, min, max, null, null, null, null, false);

    // Since nothing was returned from the DB, there should be one generated snapshot for each day
    // in the range.
    assertEquals(9, report.getData().size());
    report.getData().forEach(snap -> assertFalse(snap.getHasData()));
  }

  @Test
  void testEmptySnapshotFilledWithAllZeroes() {
    org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot =
        new org.candlepin.subscriptions.utilization.api.model.TallySnapshot();

    assertEquals(0, snapshot.getInstanceCount().intValue());
    assertEquals(0, snapshot.getCores().intValue());
    assertEquals(0, snapshot.getSockets().intValue());
    assertEquals(0, snapshot.getHypervisorInstanceCount().intValue());
    assertEquals(0, snapshot.getHypervisorCores().intValue());
    assertEquals(0, snapshot.getHypervisorSockets().intValue());
    assertEquals(0, snapshot.getCloudInstanceCount().intValue());
    assertEquals(0, snapshot.getCloudCores().intValue());
    assertEquals(0, snapshot.getCloudSockets().intValue());
    assertEquals(0.0, snapshot.getCoreHours());
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {"ROLE_" + RoleProvider.SWATCH_ADMIN_ROLE})
  void canReportWithOnlyReportingRole() {
    Mockito.when(
            repository.findSnapshot(
                "owner123456",
                RHEL_PRODUCT_ID.toString(),
                Granularity.DAILY,
                ServiceLevel._ANY,
                Usage._ANY,
                BillingProvider._ANY,
                "_ANY",
                min,
                max,
                null))
        .thenReturn(new PageImpl<>(Collections.emptyList()));

    TallyReport report =
        resource.getTallyReport(
            RHEL_PRODUCT_ID, GranularityType.DAILY, min, max, null, null, null, null, false);
    assertNotNull(report);
  }

  @Test
  @WithMockRedHatPrincipal("1111")
  void testAccessDeniedWhenAccountIsNotInAllowlist() {
    assertThrows(
        AccessDeniedException.class,
        () -> {
          resource.getTallyReport(
              RHEL_PRODUCT_ID, GranularityType.DAILY, min, max, null, null, null, null, false);
        });
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testAccessDeniedWhenUserIsNotAnAdmin() {
    assertThrows(
        AccessDeniedException.class,
        () -> {
          resource.getTallyReport(
              RHEL_PRODUCT_ID, GranularityType.DAILY, min, max, null, null, null, null, false);
        });
  }

  @Test
  void testTallyReportDataTotalUsingHardwareMeasurements() {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setOrgId("org123");
    ;
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    snapshot.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, 4.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @Test
  void testTallyReportDataTotalUsingTallyMeasurements() {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    snapshot.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, 4.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @EnumSource
  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  void testTallyReportDataCategoriesUsingHardwareMeasurements(ReportCategory category) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    for (HardwareMeasurementType hardwareMeasurementType : HardwareMeasurementType.values()) {
      snapshot.setMeasurement(hardwareMeasurementType, Uom.CORES, 4.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(
        4.0, response.getData().stream().mapToDouble(TallyReportDataPoint::getValue).sum());
  }

  @EnumSource
  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  void testTallyReportDataCategoriesUsingTallyMeasurements(ReportCategory category) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    for (HardwareMeasurementType hardwareMeasurementType : HardwareMeasurementType.values()) {
      snapshot.setMeasurement(hardwareMeasurementType, Uom.CORES, 4.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
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
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(30, response.getMeta().getCount());
    assertEquals(30, response.getData().size());
  }

  @ValueSource(booleans = {true, false})
  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  void testTallyReportDataHasCloudigradeDataUsingHardwareMeasurements(boolean hasCloudigradeData) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    if (hasCloudigradeData) {
      snapshot.setMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.CORES, 4.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(hasCloudigradeData, response.getMeta().getHasCloudigradeData());
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @ValueSource(booleans = {true, false})
  void testTallyReportDataHasCloudigradeDataUsingTallyMeasurements(
      boolean hasCloudigradeMeasurement) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    if (hasCloudigradeMeasurement) {
      snapshot.setMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.CORES, 4.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(hasCloudigradeMeasurement, response.getMeta().getHasCloudigradeData());
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @ValueSource(booleans = {true, false})
  void testTallyReportDataHasCloudigradeMismatchUsingHardwareMeasurements(
      boolean hasCloudigradeMismatch) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    snapshot.setMeasurement(HardwareMeasurementType.AWS, Uom.CORES, 4.0);
    snapshot.setMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.CORES, 4.0);
    if (hasCloudigradeMismatch) {
      snapshot.setMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.CORES, 8.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(hasCloudigradeMismatch, response.getMeta().getHasCloudigradeMismatch());
  }

  @ParameterizedTest(name = DISPLAY_NAME_PLACEHOLDER + " " + DEFAULT_DISPLAY_NAME)
  @ValueSource(booleans = {true, false})
  void testTallyReportDataHasCloudigradeMismatchUsingTallyMeasurements(
      boolean hasCloudigradeMismatch) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setAccountNumber("account123");
    snapshot.setSnapshotDate(OffsetDateTime.parse("2021-10-05T00:00Z"));
    snapshot.setMeasurement(HardwareMeasurementType.AWS, Uom.CORES, 4.0);
    snapshot.setMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.CORES, 4.0);
    if (hasCloudigradeMismatch) {
      snapshot.setMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.CORES, 8.0);
    }
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertEquals(hasCloudigradeMismatch, response.getMeta().getHasCloudigradeMismatch());
  }

  @Test
  void testTallyReportTotalMonthlyNotPopulatedWhenQueryIsNotBeginningOfMonth() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertNull(response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyNotPopulatedWhenQueryIsNotEndOfMonth() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertNull(response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyNotPopulatedWhenQueryIsPaged() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    assertNull(response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyPopulatedWithNoUnderlyingData() {
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    TallyReportDataPoint expectedTotalMonthly =
        new TallyReportDataPoint().date(null).value(0.0).hasData(false);
    assertEquals(expectedTotalMonthly, response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyPopulatedWithExistingUnderlyingData() {
    TallySnapshot snapshot1 = new TallySnapshot();
    snapshot1.setSnapshotDate(OffsetDateTime.parse("2021-11-02T00:00Z"));
    snapshot1.setGranularity(Granularity.DAILY);
    snapshot1.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, 4.0);
    TallySnapshot snapshot2 = new TallySnapshot();
    snapshot2.setSnapshotDate(OffsetDateTime.parse("2021-11-03T00:00Z"));
    snapshot2.setGranularity(Granularity.DAILY);
    snapshot2.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, 3.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot1, snapshot2)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    TallyReportDataPoint expectedTotalMonthly =
        new TallyReportDataPoint()
            .date(OffsetDateTime.parse("2021-11-03T00:00Z"))
            .value(7.0)
            .hasData(true);
    assertEquals(expectedTotalMonthly, response.getMeta().getTotalMonthly());
  }

  @Test
  void testTallyReportTotalMonthlyPopulatedWithBillingProvider() {
    TallySnapshot snapshot1 = new TallySnapshot();
    snapshot1.setSnapshotDate(OffsetDateTime.parse("2021-11-02T00:00Z"));
    snapshot1.setGranularity(Granularity.DAILY);
    snapshot1.setBillingProvider(BillingProvider.RED_HAT);
    snapshot1.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, 4.0);
    TallySnapshot snapshot2 = new TallySnapshot();
    snapshot2.setSnapshotDate(OffsetDateTime.parse("2021-11-03T00:00Z"));
    snapshot2.setGranularity(Granularity.DAILY);
    snapshot2.setBillingProvider(BillingProvider.RED_HAT);
    snapshot2.setMeasurement(HardwareMeasurementType.TOTAL, Uom.CORES, 3.0);
    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(snapshot1, snapshot2)));
    TallyReportData response =
        resource.getTallyReportData(
            ProductId.RHEL,
            MetricId.CORES,
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
            false);
    TallyReportDataPoint expectedTotalMonthly =
        new TallyReportDataPoint()
            .date(OffsetDateTime.parse("2021-11-03T00:00Z"))
            .value(7.0)
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
                      HardwareMeasurementType.TOTAL, Measurement.Uom.CORES, i * 2.0);
                  return snapshot;
                })
            .collect(Collectors.toList());

    TallyReportDataPoint expectedTotalMonthly =
        new TallyReportDataPoint()
            .date(OffsetDateTime.parse("2023-03-08T12:35Z"))
            .value(30.0)
            .hasData(true);

    when(repository.findSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(snapshots));

    TallyReportData report =
        resource.getTallyReportData(
            ProductId.OPENSHIFT_DEDICATED_METRICS,
            MetricId.CORES,
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
            true);
    assertEquals(31, report.getData().size());

    var firstSnapshot = report.getData().get(0);
    assertEquals(2.0, firstSnapshot.getValue());

    var secondSnapshot = report.getData().get(1);
    assertEquals(6.0, secondSnapshot.getValue());

    var thirdSnapshot = report.getData().get(7);
    assertEquals(22.0, thirdSnapshot.getValue());

    assertEquals(expectedTotalMonthly, report.getMeta().getTotalMonthly());
  }
}
