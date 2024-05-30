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
package com.redhat.swatch.billable.usage.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.billable.usage.data.RemittanceSummaryProjection;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
class BillableUsageServiceTest {
  private static SubscriptionDefinitionRegistry originalReference;
  private static final ApplicationClock CLOCK =
      new ApplicationClock(
          Clock.fixed(
              LocalDateTime.of(2019, 5, 24, 12, 35, 0, 0).toInstant(ZoneOffset.UTC),
              ZoneOffset.UTC));
  private static final String AWS_METRIC_ID = "aws_metric";

  private static final String CORES = "CORES";

  @Mock BillingProducer producer;
  @Mock BillableUsageRemittanceRepository remittanceRepo;
  @Mock DefaultApi contractsApi;
  @InjectMocks ContractsController contractsController;

  private BillableUsageService service;
  private SubscriptionDefinitionRegistry subscriptionDefinitionRegistry;

  @BeforeAll
  static void setupClass() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    originalReference =
        (SubscriptionDefinitionRegistry) instance.get(SubscriptionDefinitionRegistry.class);
  }

  @AfterAll
  static void tearDown() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(instance, originalReference);
  }

  @BeforeEach
  void setup() {
    service = new BillableUsageService(CLOCK, producer, remittanceRepo, contractsController);
    subscriptionDefinitionRegistry = mock(SubscriptionDefinitionRegistry.class);
    setMock(subscriptionDefinitionRegistry);
    var usage = billable(CLOCK.startOfCurrentMonth(), 0.0);
    createSubscriptionDefinition(
        usage.getProductId(), "Storage-gibibytes", usage.getBillingFactor(), false);
  }

  private void setMock(SubscriptionDefinitionRegistry mock) {
    try {
      Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(instance, mock);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void monthlyWindowNoCurrentRemittance() {
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 0.0003).withUuid(UUID.randomUUID());
    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(RemittanceSummaryProjection.builder().totalRemittedPendingValue(0.0).build());

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    service.submitBillableUsage(usage);

    BillableUsageRemittanceEntity expectedRemittance =
        remittance(usage, usage.getSnapshotDate(), 1.0);
    BillableUsage expectedUsage =
        billable(usage.getSnapshotDate(), 1.0, usage.getCurrentTotal()).withUuid(usage.getUuid());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(remittanceRepo).persistAndFlush(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowWithRemittanceUpdate() {
    // currentTotal: from multiple snapshots (2.1, 2.3)
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 2.3, 4.4);
    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(RemittanceSummaryProjection.builder().totalRemittedPendingValue(3.0).build());

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    service.submitBillableUsage(usage);

    BillableUsageRemittanceEntity expectedRemittance =
        remittance(usage, usage.getSnapshotDate(), 2.0);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 2.0, usage.getCurrentTotal());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(remittanceRepo).persistAndFlush(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowRemittanceMultipleOfBillingFactor() {
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 68.103, 1064.104);
    usage.setProductId("osd");
    usage.setMetricId(CORES);
    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(RemittanceSummaryProjection.builder().totalRemittedPendingValue(996.0).build());

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    createSubscriptionDefinition("osd", usage.getMetricId(), 0.25, false);

    service.submitBillableUsage(usage);

    // applicable_usage(68.1) converted to billable_usage as 68.1/4 = 17.025. Rounds to 18. 18 *
    // 4(Billing_factor) = 72
    BillableUsageRemittanceEntity expectedRemittance =
        remittance(usage, usage.getSnapshotDate(), 72.0);
    BillableUsage expectedUsage = billable(usage.getSnapshotDate(), 18.0, usage.getCurrentTotal());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    expectedUsage.setProductId("osd");
    expectedUsage.setMetricId(MetricIdUtils.getCores().getValue());
    expectedUsage.setBillingFactor(0.25);

    verify(remittanceRepo).persistAndFlush(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void monthlyWindowWithNoRemittanceUpdate() {
    // currentTotal: from multiple snapshots (0.02, 0.03)
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 0.03, 0.05);
    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(RemittanceSummaryProjection.builder().totalRemittedPendingValue(1.0).build());
    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);
    service.submitBillableUsage(usage);

    BillableUsage expectedUsage =
        billable(usage.getSnapshotDate(), 0.0, usage.getCurrentTotal()); // Nothing billed
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    verify(producer).produce(expectedUsage);
  }

  @Test
  void billingFactorAppliedInRecalculationEvenNumber() {
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 8.0, 16.0);
    usage.setProductId("osd");
    usage.setMetricId(CORES);
    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(RemittanceSummaryProjection.builder().totalRemittedPendingValue(6.0).build());

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    createSubscriptionDefinition("osd", usage.getMetricId(), 0.25, false);

    service.submitBillableUsage(usage);

    BillableUsageRemittanceEntity expectedRemittance =
        remittance(usage, usage.getSnapshotDate(), 12.0);
    BillableUsage expectedUsage =
        billable(usage.getSnapshotDate(), usage.getValue(), usage.getCurrentTotal());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    expectedUsage.setProductId("osd");
    expectedUsage.setMetricId(usage.getMetricId());
    expectedUsage.setBillingFactor(0.25);
    verify(remittanceRepo).persistAndFlush(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  @Test
  void billingFactorAppliedInRecalculation() {
    BillableUsage usage = billable(CLOCK.startOfCurrentMonth(), 8.0, 32.3);
    usage.setProductId("osd");
    usage.setMetricId(CORES);

    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(RemittanceSummaryProjection.builder().totalRemittedPendingValue(6.0).build());

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    createSubscriptionDefinition("osd", usage.getMetricId(), 0.25, false);

    service.submitBillableUsage(usage);

    BillableUsageRemittanceEntity expectedRemittance =
        remittance(usage, usage.getSnapshotDate(), 28.00);
    BillableUsage expectedUsage =
        billable(usage.getSnapshotDate(), usage.getValue(), usage.getCurrentTotal());
    expectedUsage.setId(usage.getId()); // Id will be regenerated above.
    expectedUsage.setProductId("osd");
    expectedUsage.setMetricId(usage.getMetricId());
    expectedUsage.setBillingFactor(0.25);
    verify(remittanceRepo).persistAndFlush(expectedRemittance);
    verify(producer).produce(expectedUsage);
  }

  // Simulates progression through contract billing.
  static Stream<Arguments> contractRemittanceParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth();
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedRemitted, expectedBilledValue)
        // NOTES:
        //    - currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        //    - Test setup mocks contracts as follows:
        //        100 coverage from 2019-05-01T00:00Z to 2019-05-15T23:59:59.999999999Z
        //        200 coverage from 2019-05-16T00:00Z onwards
        arguments(startOfUsage, 50.0, 0.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(5), 150.0, 0.0, 50.0, 50.0),
        arguments(startOfUsage.plusDays(13), 200.0, 50.0, 50.0, 50.0),
        // NOTE: Contract bumps to 200 here.
        arguments(startOfUsage.plusDays(20), 300.0, 100.0, 0.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 350.00, 100.0, 50.0, 50.0),
        // NOTE: New month so simulate remittance reset, contract remains at 200.
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
        //    - gtez is a function that returns 0 if the value is less than 0

        // contract = 100 (billable_units)
        // applicable_contract = 100 / 0.5 = 200 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(100 - 200)
        //                  = 0 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / prev_billing_factor))
        //          = gtez(0 - (0/0.5)
        //          = 0 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(0) * 0.5
        //        = 0.0 (billable units)
        // remitted = billed / billable_factor
        //          = 0.0 / 0.5
        //          = 0.0
        arguments(startOfUsage, 100.0, 0.0, 0.0, 0.0),

        // contract = 100 (billable_units)
        // applicable_contract = 100 / 0.5 = 200 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(250 - 200)
        //                  = 50 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / billing_factor))
        //          = gtez(50 - (0.0/0.5))
        //          = 50 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(50) * 0.5
        //        = 25 (billable units)
        // remitted = billed / billing_factor
        //          = 25 / 0.5
        //          = 50
        arguments(startOfUsage.plusDays(10), 250.0, 0.0, 50.0, 25.0),

        // contract = 100 (billable_units)
        // applicable_contract = 100 / 0.5 = 200 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(400 - 200)
        //                  = 200 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance))
        //          = gtez(200 - (50))
        //          = 150 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(150) * 0.5
        //        = 75 (billable units)
        // remitted = billed / billing_factor
        //          = 75 / 0.5
        //          = 100
        arguments(startOfUsage.plusDays(5), 400.0, 50.0, 150.0, 75.0),

        // NOTE: Contract rolls to 200 here.
        //
        // contract = 200 (billable_units)
        // applicable_contract = 200 / 0.5 = 400 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(500 - 400)
        //                  = 100 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / prev_billing_factor))
        //          = gtez(100 - (100.0/0.5))
        //          = gtez(-100)
        //          = 0 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(0) * 0.5
        //        = 0 (billable units)
        // remitted = billed / billing_factor
        //          = 0 / 0.5
        //          = 0.0
        arguments(startOfUsage.plusDays(20), 500.0, 200.0, 0.0, 0.0),

        // contract = 200 (billable_units)
        // applicable_contract = 200 / 0.5 = 400 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(601.25 - 400)
        //                  = 201.25 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / prev_billing_factor))
        //          = gtez(201.25 - (100.0/0.5))
        //          = gtez(1.25)
        //          = 1.25 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(1.25) * 0.5
        //        = 1 (billable units)
        // remitted = billed / billing_factor
        //          = 1 / 0.5
        //          = 2
        arguments(CLOCK.endOfCurrentMonth(), 601.25, 200.0, 2.0, 1.0),

        // NOTE: New billing month so simulate remittance of 0 with 200 contract.
        //
        // contract = 200 (billable_units)
        // applicable_contract = 200 / 0.5 = 400 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(400 - 400)
        //                  = 0 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / prev_billing_factor))
        //          = gtez(0 - (0.0/0.5))
        //          = gtez(0)
        //          = 0 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(0) * 0.5
        //        = 0 (billable units)
        // remitted = billed + current_remittance
        //          = 0 + 0
        //          = 0
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 400.0, 0.0, 0.0, 0.0));
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
        // arguments(usageDate, currentUsage, currentRemittance, expectedRemitted,
        // expectedBilledValue)
        // NOTE: currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        arguments(startOfUsage, 0.5, 0.0, 1.0, 1.0),
        arguments(startOfUsage.plusDays(5), 1.0, 1.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(13), 1.5, 1.0, 1.0, 1.0),
        arguments(startOfUsage.plusDays(20), 2.0, 2.0, 0.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 2.5, 2.0, 1.0, 1.0),
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

  @Test
  // See SWATCH-2494
  void testGetTotalRemittedConsidersAllStatuses() {
    record RemittanceTuple(double value, RemittanceStatus status, OffsetDateTime date) {}
    ;

    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth().plusDays(4);
    var t1 = new RemittanceTuple(1.0, RemittanceStatus.SUCCEEDED, startOfUsage);
    var t2 = new RemittanceTuple(5.0, RemittanceStatus.PENDING, startOfUsage.plusDays(2));
    var t3 = new RemittanceTuple(10.0, RemittanceStatus.FAILED, startOfUsage.plusDays(4));
    var t4 = new RemittanceTuple(20.0, null, startOfUsage.plusDays(4));

    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    for (var tuple : List.of(t1, t2, t3, t4)) {
      summaries.add(
          RemittanceSummaryProjection.builder()
              .totalRemittedPendingValue(tuple.value)
              .status(tuple.status)
              .remittancePendingDate(tuple.date)
              .build());
    }

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    BillableUsage usage = billable(CLOCK.endOfCurrentMonth(), 0.0);
    var result = controller.getTotalRemitted(usage);
    assertEquals(36.0, result);
  }

  static Stream<Arguments> remittanceBillingFactorParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth().plusDays(4);
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedRemitted, expectedBilledValue)
        // NOTE: currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        arguments(startOfUsage, 0.5, 0.0, 2.0, 1.0),
        arguments(startOfUsage.plusDays(5), 1.0, 1.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(13), 1.5, 2.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(20), 2.0, 2.0, 0.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 2.5, 2.0, 2.0, 1.0),
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 3.0, 0.0, 4.0, 2.0));
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
    return billable(date, value, value);
  }

  private BillableUsage billable(OffsetDateTime date, Double value, Double currentTotal) {
    return new BillableUsage()
        .withUsage(BillableUsage.Usage.PRODUCTION)
        .withId(UUID.randomUUID())
        .withBillingAccountId("aws-account1")
        .withBillingFactor(1.0)
        .withBillingProvider(BillableUsage.BillingProvider.AWS)
        .withOrgId("org123")
        .withProductId("rosa")
        .withSla(BillableUsage.Sla.STANDARD)
        .withMetricId("STORAGE_GIBIBYTES")
        .withSnapshotDate(date)
        .withStatus(BillableUsage.Status.PENDING)
        .withCurrentTotal(currentTotal)
        .withValue(value);
  }

  private BillableUsageRemittanceEntity remittance(
      BillableUsage usage, OffsetDateTime remittedDate, Double value) {
    return BillableUsageRemittanceEntity.builder()
        .usage(usage.getUsage().value())
        .orgId(usage.getOrgId())
        .billingProvider(usage.getBillingProvider().value())
        .billingAccountId(usage.getBillingAccountId())
        .productId(usage.getProductId())
        .sla(usage.getSla().value())
        .metricId(MetricId.fromString(usage.getMetricId()).getValue())
        .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(usage.getSnapshotDate()))
        .remittancePendingDate(remittedDate)
        .remittedPendingValue(value)
        .tallyId(usage.getId())
        .hardwareMeasurementType(usage.getHardwareMeasurementType())
        .status(RemittanceStatus.PENDING)
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
    BillableUsage usage = billable(usageDate, currentUsage, currentUsage);

    // Enable contracts for the current product.

    BillableUsageRemittanceEntity existingRemittance =
        remittance(usage, CLOCK.now().minusHours(1), currentRemittance);

    List<RemittanceSummaryProjection> summaries = new ArrayList<>();
    summaries.add(
        RemittanceSummaryProjection.builder()
            .totalRemittedPendingValue(existingRemittance.getRemittedPendingValue())
            .build());

    when(remittanceRepo.getRemittanceSummaries(any())).thenReturn(summaries);

    // Configure contract data, if defined
    if (isContractEnabledTest) {
      createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), billingFactor, true);

      mockContractCoverage(
          usage.getOrgId(),
          usage.getProductId(),
          AWS_METRIC_ID,
          usage.getVendorProductCode(),
          usage.getBillingProvider().value(),
          usage.getBillingAccountId(),
          usage.getSnapshotDate());
    } else {
      createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), billingFactor, false);
    }

    // Remittance should only be saved when it changes.
    // NOTE: Using argument captors make errors caught by mocks a little easier to debug.
    BillableUsageRemittanceEntity expectedRemittance =
        remittance(usage, CLOCK.now(), expectedRemitted);

    service.submitBillableUsage(usage);

    ArgumentCaptor<BillableUsageRemittanceEntity> remitted =
        ArgumentCaptor.forClass(BillableUsageRemittanceEntity.class);
    if (expectedRemitted == 0.0) {
      verify(remittanceRepo, times(0)).persistAndFlush(remitted.capture());
      assertTrue(remitted.getAllValues().isEmpty());
    } else {
      verify(remittanceRepo, times(1)).persistAndFlush(remitted.capture());
      assertThat(remitted.getAllValues(), containsInAnyOrder(expectedRemittance));
    }

    BillableUsage expectedUsage =
        billable(usage.getSnapshotDate(), expectedBilledValue, usage.getCurrentTotal());
    expectedUsage.setId(usage.getId());
    expectedUsage.setBillingFactor(billingFactor);
    ArgumentCaptor<BillableUsage> usageCaptor = ArgumentCaptor.forClass(BillableUsage.class);
    verify(producer).produce(usageCaptor.capture());
    assertEquals(expectedUsage, usageCaptor.getValue());
  }

  private void mockContractCoverage(
      String orgId,
      String productId,
      String metric,
      String vendorProductCode,
      String billingProvider,
      String billingAccountId,
      OffsetDateTime startDate)
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
    when(contractsApi.getContract(
            orgId, productId, vendorProductCode, billingProvider, billingAccountId, startDate))
        .thenReturn(List.of(contract1, updatedContract));
  }

  @Test
  void usageIsSentWhenContractIsMissingWithinWindow() throws Exception {
    BillableUsage usage = billable(OffsetDateTime.now(), 1.0);
    usage.setSnapshotDate(OffsetDateTime.now());
    usage.setMetricId(CORES);
    createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), 1.0, true);
    when(contractsApi.getContract(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

    service.submitBillableUsage(usage);
    verify(producer).produce(null);
  }

  @Test
  void noUsageProcessedWhenBillingProviderNotSupportedByContractService() {
    BillableUsage usage = billable(OffsetDateTime.now(), 1.0);
    usage.setSnapshotDate(OffsetDateTime.now());
    usage.setBillingProvider(BillableUsage.BillingProvider.GCP);
    createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), 1.0, true);
    service.submitBillableUsage(usage);
    verify(producer).produce(null);
  }

  private void createSubscriptionDefinition(
      String tag, String metricId, double billingFactor, boolean contractEnabled) {
    metricId = metricId.replace('_', '-');
    var variant = Variant.builder().tag(tag).build();
    var awsMetric =
        com.redhat.swatch.configuration.registry.Metric.builder()
            .awsDimension(AWS_METRIC_ID)
            .billingFactor(billingFactor)
            .id(metricId)
            .build();
    var subscriptionDefinition =
        SubscriptionDefinition.builder()
            .contractEnabled(contractEnabled)
            .variants(List.of(variant))
            .metrics(List.of(awsMetric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(subscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));
  }
}
