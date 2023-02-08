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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntity;
import org.candlepin.subscriptions.db.model.BillableUsageRemittanceEntityPK;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.InstanceMonthlyTotalKey;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillableUsageControllerTest {

  private ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  @Mock BillingProducer producer;
  @Mock BillableUsageRemittanceRepository remittanceRepo;
  @Mock TallySnapshotRepository snapshotRepo;
  @Mock TagProfile tagProfile;

  BillableUsageController controller;

  @BeforeEach
  void setup() {
    controller =
        new BillableUsageController(clock, producer, remittanceRepo, snapshotRepo, tagProfile);
  }

  @Test
  void usageIsSentAsIsWhenBillingWindowIsHourly() {
    BillableUsage usage = new BillableUsage();
    controller.submitBillableUsage(BillingWindow.HOURLY, usage);
    verify(producer).produce(usage);
    verifyNoInteractions(snapshotRepo, remittanceRepo);
  }

  @Test
  void monthlyWindowNoCurrentRemittance() {
    BillableUsage usage = billable(clock.startOfCurrentMonth(), 0.0003);
    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.empty());
    mockCurrentSnapshotMeasurementTotal(usage, 0.0003); // from single snapshot
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, clock.now(), 1.0);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 1.0);
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowWithRemittanceUpdate() {
    BillableUsage usage = billable(clock.startOfCurrentMonth(), 2.3);
    BillableUsageRemittanceEntity currentRemittance =
        remittance(usage, clock.now().minusHours(1), 3.0);
    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.of(currentRemittance));
    mockCurrentSnapshotMeasurementTotal(usage, 4.4); // from multiple snapshots (2.1, 2.3)
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, clock.now(), 5.0);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 2.0);
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowWithNoRemittanceUpdate() {
    BillableUsage usage = billable(clock.startOfCurrentMonth(), 0.03);
    BillableUsageRemittanceEntity currentRemittance = remittance(usage, clock.now(), 1.0);
    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.of(currentRemittance));
    mockCurrentSnapshotMeasurementTotal(usage, 0.05); // from multiple snapshots (0.02, 0.03)
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 0.0); // Nothing billed
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(producer).produce(expectedUsage);
    verifyNoMoreInteractions(remittanceRepo);
  }

  @Test
  void billingFactorAppliedInRecalculationEvenNumber() {
    BillableUsage usage = billable(clock.startOfCurrentMonth(), 8.0);
    usage.setProductId("osd");
    usage.setUom(Uom.CORES);
    BillableUsageRemittanceEntity currentRemittance =
        remittance(usage, clock.now().minusHours(1), 1.5);
    currentRemittance.setBillingFactor(0.5);

    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.of(currentRemittance));
    TagMetric tag =
        TagMetric.builder()
            .tag("OpenShift-dedicated-metrics")
            .uom(Measurement.Uom.CORES)
            .billingFactor(0.25)
            .accountQueryKey("osd")
            .build();

    when(tagProfile.getTagMetric("osd", Measurement.Uom.CORES)).thenReturn(Optional.of(tag));
    mockCurrentSnapshotMeasurementTotal(usage, 16.0);
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, clock.now(), 4.75);
    expectedRemittance.setBillingFactor(0.25);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), usage.getValue());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    expectedUsage.setProductId("osd");
    expectedUsage.setUom(usage.getUom());
    expectedUsage.setBillingFactor(0.25);
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void billingFactorAppliedInRecalculation() {
    BillableUsage usage = billable(clock.startOfCurrentMonth(), 8.0);
    usage.setProductId("osd");
    usage.setUom(Uom.CORES);
    BillableUsageRemittanceEntity currentRemittance =
        remittance(usage, clock.now().minusHours(1), 1.5);
    currentRemittance.setBillingFactor(0.5);

    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.of(currentRemittance));
    TagMetric tag =
        TagMetric.builder()
            .tag("OpenShift-dedicated-metrics")
            .uom(Measurement.Uom.CORES)
            .billingFactor(0.25)
            .accountQueryKey("osd")
            .build();

    when(tagProfile.getTagMetric("osd", Measurement.Uom.CORES)).thenReturn(Optional.of(tag));
    mockCurrentSnapshotMeasurementTotal(usage, 32.3);
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, clock.now(), 8.75);
    expectedRemittance.setBillingFactor(0.25);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), usage.getValue());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    expectedUsage.setProductId("osd");
    expectedUsage.setUom(usage.getUom());
    expectedUsage.setBillingFactor(0.25);
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  private BillableUsage billable(OffsetDateTime date, Double value) {
    return new BillableUsage()
        .withAccountNumber("account123")
        .withUsage(Usage.PRODUCTION)
        .withId(UUID.randomUUID())
        .withBillingAccountId("aws-account1")
        .withBillingFactor(1.0)
        .withBillingProvider(BillingProvider.AWS)
        .withOrgId("org123")
        .withProductId("rhosak")
        .withSla(Sla.STANDARD)
        .withUom(Uom.STORAGE_GIBIBYTES)
        .withSnapshotDate(date)
        .withValue(value);
  }

  private BillableUsageRemittanceEntityPK keyFrom(BillableUsage billableUsage) {
    return BillableUsageRemittanceEntityPK.builder()
        .usage(billableUsage.getUsage().value())
        .orgId(billableUsage.getOrgId())
        .billingProvider(billableUsage.getBillingProvider().value())
        .billingAccountId(billableUsage.getBillingAccountId())
        .productId(billableUsage.getProductId())
        .sla(billableUsage.getSla().value())
        .metricId(billableUsage.getUom().value())
        .accumulationPeriod(InstanceMonthlyTotalKey.formatMonthId(billableUsage.getSnapshotDate()))
        .build();
  }

  private void mockCurrentSnapshotMeasurementTotal(BillableUsage usage, Double sum) {
    TallyMeasurementKey measurementKey =
        new TallyMeasurementKey(
            HardwareMeasurementType.PHYSICAL, Measurement.Uom.fromValue(usage.getUom().value()));
    when(snapshotRepo.sumMeasurementValueForPeriod(
            usage.getOrgId(),
            usage.getProductId(),
            Granularity.HOURLY,
            ServiceLevel.fromString(usage.getSla().value()),
            org.candlepin.subscriptions.db.model.Usage.fromString(usage.getUsage().value()),
            org.candlepin.subscriptions.db.model.BillingProvider.fromString(
                usage.getBillingProvider().value()),
            usage.getBillingAccountId(),
            clock.startOfMonth(usage.getSnapshotDate()),
            usage.getSnapshotDate(),
            measurementKey))
        .thenReturn(sum);
  }

  private BillableUsageRemittanceEntity remittance(
      BillableUsage usage, OffsetDateTime remittedDate, Double value) {
    BillableUsageRemittanceEntityPK remKey = keyFrom(usage);
    return BillableUsageRemittanceEntity.builder()
        .key(remKey)
        .billingFactor(1.0)
        .remittanceDate(remittedDate)
        .remittedValue(value)
        .accountNumber(usage.getAccountNumber())
        .build();
  }
}
