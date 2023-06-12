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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contracts.api.resources.DefaultApi;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
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
import org.candlepin.subscriptions.tally.TallySummaryMapper;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class RemittanceControllerTest {

  @Mock private BillableUsageRemittanceRepository remittanceRepo;
  @Mock private TallySnapshotRepository snapshotRepo;
  @Mock private KafkaTemplate<String, BillableUsage> billableTemplate;
  @Mock private DefaultApi contractApi;
  @Mock private ContractsClientProperties contractsClientProperties;
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
    controller =
        new RemittanceController(
            clock,
            tagProfile,
            remittanceRepo,
            snapshotRepo,
            new TallySummaryMapper(),
            new BillableUsageMapper(tagProfile),
            usageController);
  }

  @Test
  void syncRemittance() {
    List<TallySnapshot> snaps = new ArrayList<>();
    TallySnapshot expectedSnapshot1 =
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
    snaps.add(expectedSnapshot1);

    TallySnapshot expectedSnapshot2 =
        buildSnapshot(
            "org345",
            "rhosak",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            Uom.STORAGE_GIBIBYTE_MONTHS,
            15.5,
            clock.startOfCurrentHour());
    snaps.add(expectedSnapshot2);
    when(snapshotRepo.findLatestBillablesForMonth(clock.now().getMonthValue()))
        .thenReturn(snaps.stream());

    BillableUsageRemittanceEntity expectedDailyRemittance =
        createRemittanceAndMockMeasurementValue(expectedSnapshot2, 46);
    expectedDailyRemittance.getKey().setGranularity(Granularity.DAILY);
    expectedDailyRemittance
        .getKey()
        .setRemittancePendingDate(
            clock.startOfDay(expectedDailyRemittance.getKey().getRemittancePendingDate()));

    when(remittanceRepo.findById(any())).thenReturn(Optional.of(expectedDailyRemittance));

    BillableUsageRemittanceEntity expectedRemittance1 =
        createRemittanceAndMockMeasurementValue(expectedSnapshot1, 46.0);
    BillableUsageRemittanceEntity expectedRemittance2 =
        createRemittanceAndMockMeasurementValue(expectedSnapshot2, 16.0);

    ArgumentCaptor<BillableUsageRemittanceEntity> savedRemittance =
        ArgumentCaptor.forClass(BillableUsageRemittanceEntity.class);
    controller.syncRemittance();
    verify(remittanceRepo, times(4)).save(savedRemittance.capture());
    var list = savedRemittance.getAllValues();
    assertThat(
        savedRemittance.getAllValues(),
        containsInAnyOrder(
            expectedRemittance1,
            expectedRemittance2,
            expectedDailyRemittance,
            expectedDailyRemittance));
  }

  @Test
  void syncRemittanceSkipsSnapshotWhenBillingWindowIsNotMonthly() {
    List<TallySnapshot> snaps = new ArrayList<>();
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
    snaps.add(snapshot);

    when(snapshotRepo.findLatestBillablesForMonth(clock.now().getMonthValue()))
        .thenReturn(snaps.stream());

    controller.syncRemittance();
    verifyNoInteractions(remittanceRepo);
  }

  @Test
  void syncRemittanceSkipsSnapshotWhenRemittanceExists() {
    List<TallySnapshot> snaps = new ArrayList<>();
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
    snaps.add(snapshot);

    when(snapshotRepo.findLatestBillablesForMonth(clock.now().getMonthValue()))
        .thenReturn(snaps.stream());

    BillableUsageRemittanceEntity remittance = createRemittance(snapshot, 46.0);
    remittance.getKey().setRemittancePendingDate(snapshot.getSnapshotDate());
    when(remittanceRepo.existsById(remittance.getKey())).thenReturn(true);

    controller.syncRemittance();
    // Should not save remittance.
    verifyNoMoreInteractions(remittanceRepo);
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
                .build())
        // NOTE: We are mocking the repository's sum call, so this value doesn't have to match the
        // snapshot.
        .remittedPendingValue(remittedValue)
        .build();
  }

  private BillableUsageRemittanceEntity createRemittanceAndMockMeasurementValue(
      TallySnapshot snapshot, double remittedValue) {
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
        .thenReturn(remittance.getRemittedPendingValue());

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
}
