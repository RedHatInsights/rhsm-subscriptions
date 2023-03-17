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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contracts.api.model.Contract;
import com.redhat.swatch.contracts.api.model.Metric;
import com.redhat.swatch.contracts.api.resources.DefaultApi;
import com.redhat.swatch.contracts.client.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BillableUsageControllerTest {

  private static ApplicationClock CLOCK = new FixedClockConfiguration().fixedClock();

  @Mock BillingProducer producer;
  @Mock BillableUsageRemittanceRepository remittanceRepo;
  @Mock TallySnapshotRepository snapshotRepo;
  @Mock TagProfile tagProfile;
  @Mock DefaultApi contractsApi;
  @Mock ContractsClientProperties contractsClientProperties;

  BillableUsageController controller;
  ContractsController contractsController;

  @BeforeEach
  void setup() {
    contractsController =
        new ContractsController(tagProfile, contractsApi, contractsClientProperties);
    controller =
        new BillableUsageController(
            CLOCK, producer, remittanceRepo, snapshotRepo, tagProfile, contractsController);
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
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 0.0003);
    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.empty());
    mockCurrentSnapshotMeasurementTotal(usage, 0.0003); // from single snapshot
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, CLOCK.now(), 1.0);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 1.0);
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowWithRemittanceUpdate() {
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 2.3);
    BillableUsageRemittanceEntity currentRemittance =
        remittance(usage, CLOCK.now().minusHours(1), 3.0);
    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.of(currentRemittance));
    mockCurrentSnapshotMeasurementTotal(usage, 4.4); // from multiple snapshots (2.1, 2.3)
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, CLOCK.now(), 5.0);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 2.0);
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowWithNoRemittanceUpdate() {
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 0.03);
    BillableUsageRemittanceEntity currentRemittance = remittance(usage, CLOCK.now(), 1.0);
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
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 8.0);
    usage.setProductId("osd");
    usage.setUom(Uom.CORES);
    BillableUsageRemittanceEntity currentRemittance =
        remittance(usage, CLOCK.now().minusHours(1), 1.5);
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

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, CLOCK.now(), 4.75);
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
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 8.0);
    usage.setProductId("osd");
    usage.setUom(Uom.CORES);
    BillableUsageRemittanceEntity currentRemittance =
        remittance(usage, CLOCK.now().minusHours(1), 1.5);
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

    BillableUsageRemittanceEntity expectedRemittance = remittance(usage, CLOCK.now(), 8.75);
    expectedRemittance.setBillingFactor(0.25);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), usage.getValue());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    expectedUsage.setProductId("osd");
    expectedUsage.setUom(usage.getUom());
    expectedUsage.setBillingFactor(0.25);
    verify(remittanceRepo).save(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  // Simulates progression through contract billing.
  static Stream<Arguments> contractRemittanceParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth().plusDays(4);
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedRemitted, expectedBilledValue)
        // NOTES:
        //    - currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        //    - Test setup mocks contracts as follows:
        //        100 coverage from 2019-05-01T00:00Z to 2019-05-15T23:59:59.999999999Z
        //        200 coverage from 2019-05-16T00:00Z onwards
        arguments(startOfUsage, 50.0, 0.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(5), 150.0, 0.0, 50.0, 50.0),
        arguments(startOfUsage.plusDays(13), 200.0, 50.0, 50.0, 0.0),
        arguments(startOfUsage.plusDays(20), 300.0, 50.0, 100.0, 50.0),
        arguments(CLOCK.endOfCurrentMonth(), 350.00, 100.0, 150.0, 50.0),
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 150.0, 0.0, 0.0, 0.0));
  }

  @ParameterizedTest()
  @MethodSource("contractRemittanceParameters")
  void testContractRemittance(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedRemitted,
      Double expectedBilledValue)
      throws Exception {
    performRemittanceTesting(
        usageDate,
        currentUsage,
        currentRemittance,
        expectedRemitted,
        expectedBilledValue,
        1.0,
        true);
  }

  static Stream<Arguments> contractedRemittanceWithBillingFactorParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth();
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedRemitted, expectedBilledValue)
        // NOTES:
        //    - currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        //    - Test setup mocks contracts as follows:
        //        100 coverage from 2019-05-01T00:00Z to 2019-05-15T23:59:59.999999999Z
        //        200 coverage from 2019-05-16T00:00Z onwards
        arguments(startOfUsage, 50.0, 0.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(5), 150.0, 0.0, 25.0, 25.0),
        arguments(startOfUsage.plusDays(10), 200.0, 25.0, 50.0, 25.0),
        arguments(startOfUsage.plusDays(20), 300.0, 50.0, 50.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 350.00, 50.0, 75.0, 25.0),
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 150.0, 0.0, 0.0, 0.0));
  }

  @ParameterizedTest()
  @MethodSource("contractedRemittanceWithBillingFactorParameters")
  void testContractRemittanceWithBillingFactor(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedRemitted,
      Double expectedBilledValue)
      throws Exception {
    performRemittanceTesting(
        usageDate,
        currentUsage,
        currentRemittance,
        expectedRemitted,
        expectedBilledValue,
        0.5,
        true);
  }

  // Simulates progression through non-contract based billing.
  static Stream<Arguments> remittanceParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth().plusDays(4);
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedRemitted, expectedBilledValue)
        // NOTE: currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        arguments(startOfUsage, 0.5, 0.0, 1.0, 1.0),
        arguments(startOfUsage.plusDays(5), 1.0, 1.0, 1.0, 0.0),
        arguments(startOfUsage.plusDays(13), 1.5, 1.0, 2.0, 1.0),
        arguments(startOfUsage.plusDays(20), 2.0, 2.0, 2.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 2.5, 2.0, 3.0, 1.0),
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 2.5, 0.0, 3.0, 3.0));
  }

  @ParameterizedTest()
  @MethodSource("remittanceParameters")
  void testRemittance(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedRemitted,
      Double expectedBilledValue)
      throws Exception {
    performRemittanceTesting(
        usageDate,
        currentUsage,
        currentRemittance,
        expectedRemitted,
        expectedBilledValue,
        1.0,
        false);
  }

  static Stream<Arguments> remittanceBillingFactorParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth().plusDays(4);
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedRemitted, expectedBilledValue)
        // NOTE: currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        arguments(startOfUsage, 0.5, 0.0, 1.0, 1.0),
        arguments(startOfUsage.plusDays(5), 1.0, 1.0, 1.0, 0.0),
        arguments(startOfUsage.plusDays(13), 1.5, 1.0, 1.0, 0.0),
        arguments(startOfUsage.plusDays(20), 2.0, 1.0, 1.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 2.5, 1.0, 2.0, 1.0),
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 3.0, 0.0, 2.0, 2.0));
  }

  @ParameterizedTest()
  @MethodSource("remittanceBillingFactorParameters")
  void testRemittanceWithBillingFactor(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedRemitted,
      Double expectedBilledValue)
      throws Exception {
    performRemittanceTesting(
        usageDate,
        currentUsage,
        currentRemittance,
        expectedRemitted,
        expectedBilledValue,
        0.5,
        false);
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
            CLOCK.startOfMonth(usage.getSnapshotDate()),
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

  private void performRemittanceTesting(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedRemitted,
      Double expectedBilledValue,
      Double billingFactor,
      boolean isContractEnabledTest)
      throws Exception {
    BillableUsage usage = billable(usageDate, currentUsage);

    // Enable contracts for the current product.
    TagMetric tagMetric = TagMetric.builder().billingFactor(billingFactor).build();
    when(tagProfile.getTagMetric(
            usage.getProductId(), Measurement.Uom.fromValue(usage.getUom().value())))
        .thenReturn(Optional.of(tagMetric));
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(isContractEnabledTest);

    BillableUsageRemittanceEntity existingRemittance =
        remittance(usage, CLOCK.now().minusHours(1), currentRemittance);
    existingRemittance.setBillingFactor(tagMetric.getBillingFactor());

    when(remittanceRepo.findById(keyFrom(usage))).thenReturn(Optional.of(existingRemittance));

    mockCurrentSnapshotMeasurementTotal(usage, currentUsage);

    // Configure contract data, if defined
    if (isContractEnabledTest) {
      mockContactCoverage(
          usage.getOrgId(),
          usage.getProductId(),
          usage.getUom().value(),
          usage.getBillingProvider().value(),
          usage.getBillingAccountId());
    }

    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);

    // Remittance should only be saved when it changes.
    // NOTE: Using argument captors make errors caught by mocks a little easier to debug.
    if (!currentRemittance.equals(expectedRemitted)) {
      BillableUsageRemittanceEntity expectedRemittance =
          remittance(usage, CLOCK.now(), expectedRemitted);
      expectedRemittance.setBillingFactor(billingFactor);
      ArgumentCaptor<BillableUsageRemittanceEntity> remitted =
          ArgumentCaptor.forClass(BillableUsageRemittanceEntity.class);
      verify(remittanceRepo).save(remitted.capture());
      BillableUsageRemittanceEntity saved = remitted.getValue();
      assertEquals(expectedRemittance, saved);
    } else {
      verify(remittanceRepo, never()).save(any());
    }

    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), expectedBilledValue);
    expectedUsage.setId(usage.getId());
    expectedUsage.setBillingFactor(billingFactor);
    ArgumentCaptor<BillableUsage> usageCaptor = ArgumentCaptor.forClass(BillableUsage.class);
    verify(producer).produce(usageCaptor.capture());
    assertEquals(expectedUsage, usageCaptor.getValue());
  }

  private void mockContactCoverage(
      String orgId,
      String productId,
      String metric,
      String billingProvider,
      String billingAccountId)
      throws ApiException {
    Contract contract1 =
        new Contract()
            .startDate(CLOCK.startOfCurrentMonth())
            .endDate(CLOCK.endOfDay(CLOCK.startOfCurrentMonth().plusDays(14)))
            .addMetricsItem(new Metric().metricId(metric).value(100));
    log.info("First: {}", contract1);
    // Simulate updated contract. Begins the start of the day after contract 1.
    Contract updatedContract =
        new Contract()
            .startDate(CLOCK.startOfDay(contract1.getEndDate().plusDays(1)))
            .addMetricsItem(new Metric().metricId(metric).value(200));

    log.info("Second: {}", updatedContract);
    when(contractsApi.getContract(orgId, productId, metric, billingProvider, billingAccountId))
        .thenReturn(List.of(contract1, updatedContract));
  }

  @Test
  void usageIsSentWhenContractIsMissingWithinWindow() throws Exception {
    BillableUsage usage = billable(OffsetDateTime.now(), 1.0);
    usage.setSnapshotDate(OffsetDateTime.now());
    usage.setUom(Uom.CORES);
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(true);
    when(contractsApi.getContract(any(), any(), any(), any(), any())).thenReturn(List.of());
    controller.submitBillableUsage(BillingWindow.MONTHLY, usage);
    verify(producer).produce(usage);
  }
}
