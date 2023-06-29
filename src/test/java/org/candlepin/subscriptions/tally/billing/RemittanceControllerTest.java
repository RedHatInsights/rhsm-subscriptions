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
package org.candlepin.subscriptions.tally.billing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contracts.api.resources.DefaultApi;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.BillableUsageRemittanceFilter;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.RemittanceSummaryProjection;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMapping;
import org.candlepin.subscriptions.registry.TagMetaData;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.retention.TallyRetentionPolicy;
import org.candlepin.subscriptions.retention.TallyRetentionPolicyProperties;
import org.candlepin.subscriptions.tally.TallySummaryMapper;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class RemittanceControllerTest {

  @Mock private BillableUsageRemittanceRepository remittanceRepo;
  @Mock private TallySnapshotRepository snapshotRepo;
  @Mock private KafkaTemplate<String, BillableUsage> billableTemplate;
  @Mock private DefaultApi contractApi;
  @Mock private ContractsClientProperties contractsClientProperties;

  @Mock(lenient = true)
  private TallyRetentionPolicyProperties retentionProperties;

  private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  private TagProfile tagProfile;

  private RemittanceController controller;

  @BeforeEach
  public void setup() {
    tagProfile = initTagProfile();
    BillingProducer billingProducer =
        new BillingProducer(new TaskQueueProperties(), billableTemplate);
    ContractsController contractsController =
        new ContractsController(tagProfile, contractApi, contractsClientProperties);
    BillableUsageController usageController =
        new BillableUsageController(
            clock, billingProducer, remittanceRepo, snapshotRepo, tagProfile, contractsController);

    when(retentionProperties.getHourly()).thenReturn(1680); // 70 days
    TallyRetentionPolicy retentionPolicy = new TallyRetentionPolicy(clock, retentionProperties);
    controller =
        new RemittanceController(
            clock,
            tagProfile,
            remittanceRepo,
            snapshotRepo,
            new TallySummaryMapper(),
            new BillableUsageMapper(tagProfile),
            usageController,
            retentionPolicy);
  }

  @Test
  void syncRemittance() {
    double snap1Measurement = 15.25;
    double snap2Measurement = 15.5;

    // Applicable snapshots
    TallySnapshot expectedSnapshot1 =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            snap1Measurement,
            clock.startOfCurrentHour());

    TallySnapshot expectedSnapshot2 =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            snap2Measurement,
            clock.startOfCurrentHour().plusHours(1));

    BillableUsageRemittanceEntity monthlyRemittanceAfterDbMigration =
        createRemittance(expectedSnapshot1, 31.0);
    monthlyRemittanceAfterDbMigration.getKey().setGranularity(Granularity.MONTHLY);

    when(snapshotRepo.hasLatestBillables(
            expectedSnapshot1.getOrgId(),
            expectedSnapshot1.getProductId(),
            expectedSnapshot1.getServiceLevel(),
            expectedSnapshot1.getUsage(),
            expectedSnapshot1.getBillingProvider(),
            expectedSnapshot1.getBillingAccountId(),
            clock.startOfMonth(expectedSnapshot1.getSnapshotDate()),
            clock.endOfMonth(expectedSnapshot1.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(true);

    when(snapshotRepo.getBillableSnapshots(
            expectedSnapshot1.getOrgId(),
            expectedSnapshot1.getProductId(),
            expectedSnapshot1.getServiceLevel(),
            expectedSnapshot1.getUsage(),
            expectedSnapshot1.getBillingProvider(),
            expectedSnapshot1.getBillingAccountId(),
            clock.startOfMonth(expectedSnapshot1.getSnapshotDate()),
            clock.endOfMonth(expectedSnapshot1.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(Stream.of(expectedSnapshot1, expectedSnapshot2));

    BillableUsageRemittanceEntity expectedDailyRemittance =
        createRemittanceAndMockMeasurementValue(
            expectedSnapshot2, snap1Measurement + snap2Measurement, 46);
    expectedDailyRemittance.getKey().setGranularity(Granularity.DAILY);
    expectedDailyRemittance
        .getKey()
        .setRemittancePendingDate(
            clock.startOfDay(expectedDailyRemittance.getKey().getRemittancePendingDate()));

    when(remittanceRepo.findById(any())).thenReturn(Optional.of(expectedDailyRemittance));

    BillableUsageRemittanceEntity expectedRemittance1 =
        createRemittanceAndMockMeasurementValue(expectedSnapshot1, snap1Measurement, 16.0);

    BillableUsageRemittanceEntity expectedRemittance2 =
        createRemittanceAndMockMeasurementValue(
            expectedSnapshot2, snap1Measurement + snap2Measurement, 15.0);

    mockRemittanceSummaryTotal(
        monthlyRemittanceAfterDbMigration.getKey(),
        // Nothing remitted initially, when first snapshot is processed.
        0.0,
        expectedRemittance1.getRemittedPendingValue(),
        // The final summary call will be when the remittance sync validation occurs
        // so we expect the value of the remittance we are syncing.
        monthlyRemittanceAfterDbMigration.getRemittedPendingValue());

    ArgumentCaptor<BillableUsageRemittanceEntity> savedRemittance =
        ArgumentCaptor.forClass(BillableUsageRemittanceEntity.class);
    controller.syncRemittance(monthlyRemittanceAfterDbMigration);
    verify(remittanceRepo, times(4)).save(savedRemittance.capture());
    assertThat(
        savedRemittance.getAllValues(),
        containsInAnyOrder(
            expectedRemittance1,
            expectedRemittance2,
            expectedDailyRemittance,
            expectedDailyRemittance));
    verify(remittanceRepo).delete(monthlyRemittanceAfterDbMigration);
  }

  @Test
  void syncRemittanceSkipsSnapshotWhenBillingWindowIsNotMonthly() {

    TallySnapshot snapshot =
        buildSnapshot(
            "org123",
            "my-product",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            15.25,
            clock.startOfCurrentHour());

    // Technically, a remittance record should never exist for a product that
    // doesn't have a monthly billing window, however, there is a safeguard put in
    // place just in case it ever happened because of future code changes.
    BillableUsageRemittanceEntity monthlyRemittanceAfterDbMigration =
        createRemittance(snapshot, 16.0);
    monthlyRemittanceAfterDbMigration.getKey().setGranularity(Granularity.MONTHLY);

    when(snapshotRepo.hasLatestBillables(
            snapshot.getOrgId(),
            snapshot.getProductId(),
            snapshot.getServiceLevel(),
            snapshot.getUsage(),
            snapshot.getBillingProvider(),
            snapshot.getBillingAccountId(),
            clock.startOfMonth(snapshot.getSnapshotDate()),
            clock.endOfMonth(snapshot.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(true);

    when(snapshotRepo.getBillableSnapshots(
            snapshot.getOrgId(),
            snapshot.getProductId(),
            snapshot.getServiceLevel(),
            snapshot.getUsage(),
            snapshot.getBillingProvider(),
            snapshot.getBillingAccountId(),
            clock.startOfMonth(snapshot.getSnapshotDate()),
            clock.endOfMonth(snapshot.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(Stream.of(snapshot));

    mockRemittanceSummaryTotal(
        monthlyRemittanceAfterDbMigration.getKey(),
        monthlyRemittanceAfterDbMigration.getRemittedPendingValue());

    controller.syncRemittance(monthlyRemittanceAfterDbMigration);

    verify(remittanceRepo).delete(monthlyRemittanceAfterDbMigration);

    // Code path shouldn't get into the BillableUsageController, so no more interactions are
    // expected with the RemittanceRepository (saves).
    verifyNoMoreInteractions(remittanceRepo);
  }

  @Test
  void syncRemittanceSkipsSnapshotWhenRemittanceExists() {
    TallySnapshot snapshot =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            15.25,
            clock.startOfCurrentHour());

    BillableUsageRemittanceEntity monthlyRemittanceAfterDbMigration =
        createRemittance(snapshot, 46.0);
    monthlyRemittanceAfterDbMigration.getKey().setGranularity(Granularity.MONTHLY);

    BillableUsageRemittanceEntity remittance = createRemittance(snapshot, 46.0);
    remittance.getKey().setRemittancePendingDate(snapshot.getSnapshotDate());
    when(remittanceRepo.existsById(remittance.getKey())).thenReturn(true);

    when(snapshotRepo.hasLatestBillables(
            snapshot.getOrgId(),
            snapshot.getProductId(),
            snapshot.getServiceLevel(),
            snapshot.getUsage(),
            snapshot.getBillingProvider(),
            snapshot.getBillingAccountId(),
            clock.startOfMonth(snapshot.getSnapshotDate()),
            clock.endOfMonth(snapshot.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(true);

    when(snapshotRepo.getBillableSnapshots(
            snapshot.getOrgId(),
            snapshot.getProductId(),
            snapshot.getServiceLevel(),
            snapshot.getUsage(),
            snapshot.getBillingProvider(),
            snapshot.getBillingAccountId(),
            clock.startOfMonth(snapshot.getSnapshotDate()),
            clock.endOfMonth(snapshot.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(Stream.of(snapshot));

    mockRemittanceSummaryTotal(
        monthlyRemittanceAfterDbMigration.getKey(),
        monthlyRemittanceAfterDbMigration.getRemittedPendingValue());

    controller.syncRemittance(monthlyRemittanceAfterDbMigration);

    // Should not save new remittance but should delete the main remittance since the final
    // sync totals matched.
    verify(remittanceRepo).delete(monthlyRemittanceAfterDbMigration);
    verifyNoMoreInteractions(remittanceRepo);
  }

  // If there are no billable snapshots present for the remittance being synced (
  // potentially pruned snapshots for older remittance) then the remittance is
  // considered synced and is deleted.
  @Test
  void testRemittanceBeingSyncedIsDeletedIfThereAreNoBillablesPresent() {
    var remittanceToSync =
        BillableUsageRemittanceEntity.builder()
            .key(
                BillableUsageRemittanceEntityPK.builder()
                    .orgId("org123")
                    .accumulationPeriod(
                        BillableUsageRemittanceEntityPK.getAccumulationPeriod(clock.now()))
                    .billingAccountId("baid")
                    .billingProvider("red hat")
                    .metricId(Uom.STORAGE_GIBIBYTE_MONTHS.value())
                    .productId("rhosak")
                    .sla("Premium")
                    .usage("Production")
                    .remittancePendingDate(clock.now())
                    .granularity(Granularity.MONTHLY)
                    .build())
            .remittedPendingValue(22.2)
            .build();

    when(snapshotRepo.hasLatestBillables(
            remittanceToSync.getKey().getOrgId(),
            remittanceToSync.getKey().getProductId(),
            ServiceLevel.fromString(remittanceToSync.getKey().getSla()),
            Usage.fromString(remittanceToSync.getKey().getUsage()),
            BillingProvider.fromString(remittanceToSync.getKey().getBillingProvider()),
            remittanceToSync.getKey().getBillingAccountId(),
            clock.startOfMonth(remittanceToSync.getKey().getRemittancePendingDate()),
            clock.endOfMonth(remittanceToSync.getKey().getRemittancePendingDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(false);

    controller.syncRemittance(remittanceToSync);

    // Should not save new remittance but should delete the main remittance since the final
    // sync totals matched.
    verify(remittanceRepo).delete(remittanceToSync);
    verifyNoMoreInteractions(remittanceRepo);
    verifyNoMoreInteractions(snapshotRepo);
  }

  @Test
  void testIllegalStateExceptionWhenRemittanceDoesNotAlignAndSyncedRemittanceIsNotDeleted() {
    double snapMeasurementValue1 = 15.25;
    double snapMeasurementValue2 = 1.20;

    // Applicable snapshots
    TallySnapshot snapshot1 =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            snapMeasurementValue1,
            clock.startOfCurrentHour());

    // Applicable snapshots
    TallySnapshot snapshot2 =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            snapMeasurementValue2,
            clock.startOfCurrentHour().plusHours(1));

    BillableUsageRemittanceEntity monthlyRemittanceAfterDbMigration =
        createRemittance(snapshot1, 18.0);
    monthlyRemittanceAfterDbMigration.getKey().setGranularity(Granularity.MONTHLY);

    when(snapshotRepo.hasLatestBillables(
            snapshot1.getOrgId(),
            snapshot1.getProductId(),
            snapshot1.getServiceLevel(),
            snapshot1.getUsage(),
            snapshot1.getBillingProvider(),
            snapshot1.getBillingAccountId(),
            clock.startOfMonth(snapshot1.getSnapshotDate()),
            clock.endOfMonth(snapshot1.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(true);

    when(snapshotRepo.getBillableSnapshots(
            snapshot1.getOrgId(),
            snapshot1.getProductId(),
            snapshot1.getServiceLevel(),
            snapshot1.getUsage(),
            snapshot1.getBillingProvider(),
            snapshot1.getBillingAccountId(),
            clock.startOfMonth(snapshot1.getSnapshotDate()),
            clock.endOfMonth(snapshot1.getSnapshotDate()),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(Stream.of(snapshot1, snapshot2));

    createRemittanceAndMockMeasurementValue(snapshot1, snapMeasurementValue1, 16.0);

    mockRemittanceSummaryTotal(
        monthlyRemittanceAfterDbMigration.getKey(),
        // Nothing remitted initially, when first snapshot is processed.
        0.0,
        // Mock an incorrect final remittance on validation value to trigger exception.
        10.0);

    RemittanceSyncAlignmentException ex =
        assertThrows(
            RemittanceSyncAlignmentException.class,
            () -> controller.syncRemittance(monthlyRemittanceAfterDbMigration));
    assertEquals(monthlyRemittanceAfterDbMigration, ex.getRemittanceToSync());
    assertEquals(10.0, ex.getResult());
    assertEquals(snapshot2.getSnapshotDate(), ex.getDateOfLatestSnapshot());

    verify(remittanceRepo, never()).delete(monthlyRemittanceAfterDbMigration);
  }

  @Test
  void testRemittanceNotProcessedAndIsDeletedWhenOutsideHourlyTallyRetentionPeriod() {
    TallySnapshot snapshot =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            12.0,
            clock.startOfCurrentMonth().minusMonths(5));

    BillableUsageRemittanceEntity monthlyRemittanceAfterDbMigration =
        createRemittance(snapshot, 12.0);
    monthlyRemittanceAfterDbMigration.getKey().setGranularity(Granularity.MONTHLY);

    controller.syncRemittance(monthlyRemittanceAfterDbMigration);

    verifyNoInteractions(snapshotRepo);
    verify(remittanceRepo).delete(monthlyRemittanceAfterDbMigration);
    verifyNoMoreInteractions(remittanceRepo);
  }

  @Test
  void testReplaceMonthlyRemittance() {
    TallySnapshot snapshot =
        buildSnapshot(
            "org123",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            12.0,
            clock.startOfCurrentMonth());

    BillableUsageRemittanceEntity monthlyRemittance = createRemittance(snapshot, 12.0);
    monthlyRemittance.getKey().setGranularity(Granularity.MONTHLY);

    BillableUsageRemittanceEntity hourlyRem =
        createRemittance(snapshot, monthlyRemittance.getRemittedPendingValue());

    BillableUsageRemittanceEntity dailyRem =
        createRemittance(snapshot, monthlyRemittance.getRemittedPendingValue());
    dailyRem.getKey().setGranularity(Granularity.DAILY);

    controller.replaceMonthlyRemittance(monthlyRemittance, snapshot.getSnapshotDate());
    verify(remittanceRepo).save(dailyRem);
    verify(remittanceRepo).save(hourlyRem);
    verify(remittanceRepo).deleteById(monthlyRemittance.getKey());
  }

  private BillableUsageRemittanceEntity createRemittance(
      TallySnapshot snapshot, double remittedValue) {
    return BillableUsageRemittanceEntity.builder()
        .key(
            BillableUsageRemittanceEntityPK.builder()
                .orgId(snapshot.getOrgId())
                .accumulationPeriod(
                    BillableUsageRemittanceEntityPK.getAccumulationPeriod(
                        snapshot.getSnapshotDate()))
                .billingAccountId(snapshot.getBillingAccountId())
                .billingProvider(snapshot.getBillingProvider().getValue())
                .metricId(Uom.STORAGE_GIBIBYTE_MONTHS.value())
                .productId(snapshot.getProductId())
                .sla(snapshot.getServiceLevel().getValue())
                .usage(snapshot.getUsage().getValue())
                .remittancePendingDate(snapshot.getSnapshotDate())
                .granularity(Granularity.HOURLY)
                .snapshotId(snapshot.getId().toString())
                .build())
        // NOTE: We are mocking the repository's sum call, so this value doesn't have to match the
        // snapshot.
        .remittedPendingValue(remittedValue)
        .build();
  }

  private BillableUsageRemittanceEntity createRemittanceAndMockMeasurementValue(
      TallySnapshot snapshot, double sumOfSnapMeasurement, double remittedValue) {
    BillableUsageRemittanceEntity remittance = createRemittance(snapshot, remittedValue);

    when(snapshotRepo.sumMeasurementValueForPeriod(
            snapshot.getOrgId(),
            snapshot.getProductId(),
            snapshot.getGranularity(),
            snapshot.getServiceLevel(),
            snapshot.getUsage(),
            snapshot.getBillingProvider(),
            snapshot.getBillingAccountId(),
            clock.startOfMonth(snapshot.getSnapshotDate()),
            snapshot.getSnapshotDate(),
            new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, Uom.STORAGE_GIBIBYTE_MONTHS)))
        .thenReturn(sumOfSnapMeasurement);

    return remittance;
  }

  private TallySnapshot buildSnapshot(
      String orgId,
      String productId,
      Granularity granularity,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      Uom uom,
      double val,
      OffsetDateTime snapshotDate) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, uom), val);
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.TOTAL, uom), val);
    return TallySnapshot.builder()
        .orgId(orgId)
        .productId(productId)
        .snapshotDate(snapshotDate)
        .tallyMeasurements(measurements)
        .granularity(granularity)
        .serviceLevel(sla)
        .usage(usage)
        .billingProvider(billingProvider)
        .billingAccountId("billing-account")
        .id(UUID.randomUUID())
        .build();
  }

  private TagProfile initTagProfile() {
    List<TagMetric> tagMetrics = new LinkedList<>();
    tagMetrics.add(
        TagMetric.builder()
            .tag("rhosak")
            .uom(Uom.STORAGE_GIBIBYTE_MONTHS)
            .metricId("redhat.com:rhosak:storage_gib_months")
            .billingWindow(BillingWindow.MONTHLY)
            .build());
    tagMetrics.add(
        TagMetric.builder()
            .tag("my-product")
            .uom(Uom.STORAGE_GIBIBYTE_MONTHS)
            .metricId("redhat.com:rhosak:storage_gib_months")
            .billingWindow(BillingWindow.HOURLY)
            .build());

    List<TagMetaData> metaData =
        List.of(
            TagMetaData.builder().tags(Set.of("rhosak")).billingModel("PAYG").build(),
            TagMetaData.builder().tags(Set.of("my-product")).billingModel("PAYG").build());

    TagProfile tagProfile =
        TagProfile.builder()
            .tagMappings(
                List.of(
                    TagMapping.builder()
                        .tags(Set.of("rhosak"))
                        .valueType("role")
                        .value("rhosak")
                        .build()))
            .tagMetrics(tagMetrics)
            .tagMetaData(metaData)
            .build();

    // Manually invoke @PostConstruct so that the class is properly initialized.
    tagProfile.initLookups();
    return tagProfile;
  }

  void mockRemittanceSummaryTotal(
      BillableUsageRemittanceEntityPK key, Double... expectedRemittanceTotal) {

    var filter =
        BillableUsageRemittanceFilter.builder()
            .orgId(key.getOrgId())
            .billingAccountId(key.getBillingAccountId())
            .billingProvider(key.getBillingProvider())
            .accumulationPeriod(key.getAccumulationPeriod())
            .metricId(key.getMetricId())
            .productId(key.getProductId())
            .sla(key.getSla())
            .usage(key.getUsage())
            .granularity(Granularity.HOURLY)
            .build();

    List<List<RemittanceSummaryProjection>> returnValues =
        Arrays.stream(expectedRemittanceTotal)
            .map(
                value ->
                    List.of(
                        RemittanceSummaryProjection.builder()
                            .orgId(key.getOrgId())
                            .billingAccountId(key.getBillingAccountId())
                            .billingProvider(key.getBillingProvider())
                            .accumulationPeriod(key.getAccumulationPeriod())
                            .metricId(key.getMetricId())
                            .productId(key.getProductId())
                            .sla(key.getSla())
                            .usage(key.getUsage())
                            .totalRemittedPendingValue(value)
                            .build()))
            .collect(Collectors.toList());

    OngoingStubbing<List<RemittanceSummaryProjection>> mockCall =
        when(remittanceRepo.getRemittanceSummaries(filter));

    for (var summaryReturn : returnValues) {
      mockCall = mockCall.thenReturn(summaryReturn);
    }
  }
}
