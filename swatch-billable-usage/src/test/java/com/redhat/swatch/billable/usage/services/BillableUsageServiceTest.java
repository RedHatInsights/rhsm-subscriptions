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

import static com.redhat.swatch.billable.usage.services.BillableUsageService.BILLABLE_USAGE_METRIC;
import static com.redhat.swatch.billable.usage.services.BillableUsageService.COVERED_USAGE_METRIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceEntity;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.AccumulationPeriodFormatter;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
@QuarkusTest
class BillableUsageServiceTest {
  private static final ApplicationClock CLOCK =
      new ApplicationClock(
          Clock.fixed(
              LocalDateTime.of(2019, 5, 24, 12, 35, 0, 0).toInstant(ZoneOffset.UTC),
              ZoneOffset.UTC));

  private static final String AWS_METRIC_ID = "Instance-hours";
  private static final String ROSA = "rosa";
  private static final String ORG_ID = "org123";

  private static SubscriptionDefinitionRegistry originalReference;

  @InjectMock BillingProducer producer;
  @InjectSpy BillableUsageRemittanceRepository remittanceRepo;
  @InjectMock @RestClient DefaultApi contractsApi;

  @Inject ApplicationClock clock;
  @Inject BillableUsageService service;
  @Inject BillableUsageService billableUsageService;
  @Inject MeterRegistry meterRegistry;

  private final SubscriptionDefinitionRegistry mockSubscriptionDefinitionRegistry =
      mock(SubscriptionDefinitionRegistry.class);

  @BeforeAll
  static void setupClass() {
    originalReference = SubscriptionDefinitionRegistry.getInstance();
  }

  @Transactional
  @BeforeEach
  void setup() {
    remittanceRepo.deleteAll();
    // reset original subscription definition registry
    setSubscriptionDefinitionRegistry(originalReference);
    meterRegistry.clear();
  }

  @AfterEach
  void tearDown() {
    /* We need to reset the registry because the mock SubscriptionDefinitionRegistry uses a stubbed
     * SubscriptionDefinition.  If the test run order happens to result in that stub being used first for a tag lookup
     * then the SubscriptionDefinition cache will be populated with the stub's particulars which we don't want. */
    SubscriptionDefinitionRegistry.reset();
  }

  @Test
  void monthlyWindowNoCurrentRemittance() {
    BillableUsage usage = givenInstanceHoursUsageForRosa(0.0003);
    givenExistingContractForUsage(usage);

    service.submitBillableUsage(usage);

    thenRemittanceIsUpdated(usage, 1.0);
    thenUsageIsSent(usage, 1.0);
    thenBillableMeterMatches(usage, 1.0);
  }

  @Test
  void monthlyWindowWithRemittanceUpdate() {
    // currentTotal: from multiple snapshots (2.1, 2.3)
    BillableUsage usage = givenInstanceHoursUsageForRosa(2.3, 4.4);
    givenExistingContractForUsage(usage);
    givenExistingRemittanceForUsage(usage, 3.0);

    service.submitBillableUsage(usage);

    thenRemittanceIsUpdated(usage, 2.0);
    thenUsageIsSent(usage, 2.0);
    thenBillableMeterMatches(usage, 2.0);
  }

  @Test
  void monthlyWindowRemittanceMultipleOfBillingFactor() {
    BillableUsage usage = givenCoresUsageForRosa(68.103, 1064.104);
    givenExistingContractForUsage(usage);
    givenExistingRemittanceForUsage(usage, 996.0);

    service.submitBillableUsage(usage);

    // applicable_usage(68.1) converted to billable_usage as 68.1/4 = 17.025. Rounds to 18. 18 *
    // 4(Billing_factor) = 72
    thenRemittanceIsUpdated(usage, 72.0);
    thenUsageIsSent(usage, 18.0);
    thenBillableMeterMatches(usage, 72.0);
  }

  @Test
  void monthlyWindowWithNoRemittanceUpdate() {
    // currentTotal: from multiple snapshots (0.02, 0.03)
    BillableUsage usage = givenInstanceHoursUsageForRosa(0.03, 0.05);
    givenExistingContractForUsage(usage);
    givenExistingRemittanceForUsage(usage, 1.0);

    service.submitBillableUsage(usage);

    thenUsageIsSent(usage, 0.0);
    thenBillableMeterMatches(usage, 0.0);
  }

  @Test
  void
      monthlyWindowRemittanceWhenContractStartsOnCurrentMonthAndMetricIsGratisThenUsageIsGratisAndNotSent() {
    givenInstanceHoursMetricIsGratisForRosa();
    BillableUsage usage = givenInstanceHoursUsageForRosa(1.0);
    usage.setSnapshotDate(clock.startOfCurrentMonth().plusDays(5));
    // When the contract starts on the current month:
    // anytime other than 1st day of the month at midnight UTC exactly [e.g. Feb 1st 00:00.000 UTC]
    Contract contract = new Contract();
    contract.setStartDate(clock.startOfCurrentMonth().plusDays(1));
    givenExistingContractForUsage(contract, usage);

    service.submitBillableUsage(usage);

    thenRemittanceIsUpdatedWithStatusGratis(usage, 1.0);
    thenUsageIsNotSent();
  }

  @Test
  void billingFactorAppliedInRecalculationEvenNumber() {
    BillableUsage usage = givenCoresUsageForRosa(8.0, 16.0);
    givenExistingContractForUsage(usage);
    givenExistingRemittanceForUsage(usage, 6.0);

    service.submitBillableUsage(usage);

    thenRemittanceIsUpdated(usage, 12.0);
    thenUsageIsSent(usage, usage.getValue());
    thenBillableMeterMatches(usage, 12.0);
  }

  @Test
  void billingFactorAppliedInRecalculation() {
    BillableUsage usage = givenCoresUsageForRosa(8.0, 32.3);
    givenExistingContractForUsage(usage);
    givenExistingRemittanceForUsage(usage, 6.0);

    service.submitBillableUsage(usage);

    thenRemittanceIsUpdated(usage, 28.0);
    thenUsageIsSent(usage, usage.getValue());
    thenBillableMeterMatches(usage, 28.0);
  }

  /**
   * Contract removed mid-month: remittance already recorded is preserved; additional usage after
   * contract restore is adjusted. One metric per {@link #contractAdjustmentForRemoveContract()}
   * row.
   */
  @ParameterizedTest
  @MethodSource("contractAdjustmentForRemoveContract")
  void verifyContractAdjustmentForRemoveContract(
      MetricId metricId, double expectedInitialRemitted, double expectedFinalRemitted)
      throws ApiException {
    double applicableMetricUsage = 100.0;
    double totalBillingUsage = applicableMetricUsage * 2;

    // Phase 1: contract (coverage 6), first usage event (100)
    BillableUsage firstUsage =
        givenUsageForRosa(metricId, applicableMetricUsage, applicableMetricUsage);
    givenExistingContractForUsage(contractWithCoverage(firstUsage, 6), firstUsage);

    service.submitBillableUsage(firstUsage);

    assertEquals(expectedInitialRemitted, getAccountRemitted(firstUsage));

    // Phase 2: no contract, re-process same cumulative usage (increment 0)
    givenContractApiReturnsNoContracts();

    BillableUsage retally = givenUsageForRosa(metricId, 0.0, applicableMetricUsage);
    service.submitBillableUsage(retally);
    assertEquals(expectedInitialRemitted, getAccountRemitted(retally));

    // Phase 3: contract restored, second event (increment 100, total 200)
    givenExistingContractForUsage(contractWithCoverage(firstUsage, 6), firstUsage);
    BillableUsage secondUsage =
        givenUsageForRosa(metricId, applicableMetricUsage, totalBillingUsage);
    service.submitBillableUsage(secondUsage);

    assertEquals(expectedFinalRemitted, getAccountRemitted(secondUsage));
  }

  /**
   * Second contract added mid-month: remittance stays at first-contract value until usage exceeds
   * combined coverage. Component twin: {@code ContractAdjustmentComponentTest}.
   */
  @ParameterizedTest
  @MethodSource("contractAdjustmentWhenAddMoreContractInMidMonth")
  void verifyContractAdjustmentWhenAddMoreContractInMidMonth(
      MetricId metricId, double expectedInitialRemitted, double expectedFinalRemitted)
      throws ApiException {
    double applicableMetricUsage = 100.0;
    double totalAfterSecondEvent = applicableMetricUsage * 2;
    double totalAfterThirdEvent = totalAfterSecondEvent + 301.0;

    // Phase 1: first contract (coverage 10), usage 100
    BillableUsage firstUsage =
        givenUsageForRosa(metricId, applicableMetricUsage, applicableMetricUsage);
    givenExistingContractForUsage(contractWithCoverage(firstUsage, 10), firstUsage);

    service.submitBillableUsage(firstUsage);

    assertEquals(expectedInitialRemitted, getAccountRemitted(firstUsage));

    // Phase 2: second contract added, re-process without new usage
    Contract initialContract = contractWithCoverage(firstUsage, 10);
    Contract addedContract = contractWithCoverage(firstUsage, 100);
    givenContractApiReturnsContracts(initialContract, addedContract);

    BillableUsage retally = givenUsageForRosa(metricId, 0.0, applicableMetricUsage);
    service.submitBillableUsage(retally);
    assertEquals(expectedInitialRemitted, getAccountRemitted(retally));

    // Phase 3: second usage event (cumulative 200) — still first-contract remittance
    BillableUsage secondUsage =
        givenUsageForRosa(metricId, applicableMetricUsage, totalAfterSecondEvent);
    service.submitBillableUsage(secondUsage);
    assertEquals(expectedInitialRemitted, getAccountRemitted(secondUsage));

    // Phase 4: large third event (cumulative 501) — combined contract coverage
    BillableUsage thirdUsage = givenUsageForRosa(metricId, 301.0, totalAfterThirdEvent);
    service.submitBillableUsage(thirdUsage);

    assertEquals(expectedFinalRemitted, getAccountRemitted(thirdUsage));
  }

  static Stream<Arguments> contractAdjustmentForRemoveContract() {
    // Remittance = ceil(usage × billingFactor) / billingFactor − contractCoverage / billingFactor
    // Phase 3 (total usage 200): alreadyRemitted + ceil((200 − alreadyRemitted) × bf) / bf − 6/bf
    //   Cores (bf=0.25): initial 100 − 24 = 76; final 76 + (124 − 24) = 176
    //   Instance-hours (bf=1): initial 100 − 6 = 94; final 94 + (106 − 6) = 194
    return Stream.of(
        arguments(MetricIdUtils.getCores(), 76.0, 176.0),
        arguments(MetricIdUtils.getInstanceHours(), 94.0, 194.0));
  }

  static Stream<Arguments> contractAdjustmentWhenAddMoreContractInMidMonth() {
    // Phase 1: contract coverage 10 billable units, usage 100
    // Phase 4: combined contract coverage 110, cumulative usage 501 (100 + 100 + 301)
    //   Cores (bf=0.25): initial 100 − 40 = 60; final 60 + (444 − 440) = 64
    //   Instance-hours (bf=1): initial 100 − 10 = 90; final 90 + (411 − 110) = 391
    return Stream.of(
        arguments(MetricIdUtils.getCores(), 60.0, 64.0),
        arguments(MetricIdUtils.getInstanceHours(), 90.0, 391.0));
  }

  /** BillableUsage snapshotDate is overwritten during processing; reset for remittance lookup. */
  private double getAccountRemitted(BillableUsage usage) {
    usage.setSnapshotDate(CLOCK.startOfCurrentMonth());
    return service.getTotalRemitted(usage);
  }

  // Simulates progression through contract billing.
  static Stream<Arguments> contractRemittanceParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth();
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedCoveredByContract, expectedRemitted,
        // expectedBilledValue)
        // NOTES:
        //    - currantUsage is the sum of all snapshots, NOT the value from BillableUsage.
        //    - Test setup mocks contracts as follows:
        //        100 coverage from 2019-05-01T00:00Z to 2019-05-15T23:59:59.999999999Z
        //        200 coverage from 2019-05-16T00:00Z onwards
        arguments(startOfUsage, 50.0, 0.0, 50.0, 0.0, 0.0),
        arguments(startOfUsage.plusDays(5), 150.0, 0.0, 100.0, 50.0, 50.0),
        arguments(startOfUsage.plusDays(13), 200.0, 50.0, 100.0, 50.0, 50.0),
        // NOTE: Contract bumps to 200 here.
        arguments(startOfUsage.plusDays(20), 300.0, 100.0, 200.0, 0.0, 0.0),
        arguments(CLOCK.endOfCurrentMonth(), 350.00, 100.0, 200.0, 50.0, 50.0),
        // NOTE: New month so simulate remittance reset, contract remains at 200.
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 150.0, 0.0, 150.0, 0.0, 0.0));
  }

  @ParameterizedTest
  @MethodSource("contractRemittanceParameters")
  void testContractRemittance(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedCoveredByContract,
      Double expectedRemitted,
      Double expectedBilledValue)
      throws Exception {
    performRemittanceTesting(
        usageDate,
        currentUsage,
        currentRemittance,
        expectedCoveredByContract,
        expectedRemitted,
        expectedBilledValue,
        1.0,
        true);
  }

  static Stream<Arguments> contractedRemittanceWithBillingFactorParameters() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth();
    return Stream.of(
        // arguments(currentUsage, currentRemittance, expectedCoveredByContract, expectedRemitted,
        // expectedBilledValue)
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
        // covered = contract / 0.5 = 200 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(0) * 0.5
        //        = 0.0 (billable units)
        // remitted = billed / billable_factor
        //          = 0.0 / 0.5
        //          = 0.0
        arguments(startOfUsage, 100.0, 0.0, 100.0, 0.0, 0.0),

        // contract = 100 (billable_units)
        // applicable_contract = 100 / 0.5 = 200 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(250 - 200)
        //                  = 50 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / billing_factor))
        //          = gtez(50 - (0.0/0.5))
        //          = 50 (measurement units)
        // covered = contract / 0.5 = 200 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(50) * 0.5
        //        = 25 (billable units)
        // remitted = billed / billing_factor
        //          = 25 / 0.5
        //          = 50
        arguments(startOfUsage.plusDays(10), 250.0, 0.0, 200.0, 50.0, 25.0),

        // contract = 100 (billable_units)
        // applicable_contract = 100 / 0.5 = 200 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(400 - 200)
        //                  = 200 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance))
        //          = gtez(200 - (50))
        //          = 150 (measurement units)
        // covered = contract / 0.5 = 200 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(150) * 0.5
        //        = 75 (billable units)
        // remitted = billed / billing_factor
        //          = 75 / 0.5
        //          = 100
        arguments(startOfUsage.plusDays(5), 400.0, 50.0, 200.0, 150.0, 75.0),

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
        // covered = contract / 0.5 = 400 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(0) * 0.5
        //        = 0 (billable units)
        // remitted = billed / billing_factor
        //          = 0 / 0.5
        //          = 0.0
        arguments(startOfUsage.plusDays(20), 500.0, 200.0, 400.0, 0.0, 0.0),

        // contract = 200 (billable_units)
        // applicable_contract = 200 / 0.5 = 400 (measurement units)
        // applicable_usage = gtez(current_usage - applicable_contact)
        //                  = gtez(601.25 - 400)
        //                  = 201.25 (measurement units)
        // unbilled = gtez(applicable_usage - (current_remittance / prev_billing_factor))
        //          = gtez(201.25 - (100.0/0.5))
        //          = gtez(1.25)
        //          = 1.25 (measurement units)
        // covered = contract / 0.5 = 400 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(1.25) * 0.5
        //        = 1 (billable units)
        // remitted = billed / billing_factor
        //          = 1 / 0.5
        //          = 2
        arguments(CLOCK.endOfCurrentMonth(), 601.25, 200.0, 400.0, 2.0, 1.0),

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
        // covered = contract / 0.5 = 400 (measurement units)
        // billed = ceil(unbilled) * billing_factor
        //        = ceil(0) * 0.5
        //        = 0 (billable units)
        // remitted = billed + current_remittance
        //          = 0 + 0
        //          = 0
        arguments(CLOCK.startOfCurrentMonth().plusMonths(1), 400.0, 0.0, 400.0, 0.0, 0.0));
  }

  @ParameterizedTest
  @MethodSource("contractedRemittanceWithBillingFactorParameters")
  void testContractRemittanceWithBillingFactor(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedCoveredByContract,
      Double expectedRemitted,
      Double expectedBilledValue)
      throws Exception {
    performRemittanceTesting(
        usageDate,
        currentUsage,
        currentRemittance,
        expectedCoveredByContract,
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

  @ParameterizedTest
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
        0.0,
        expectedRemitted,
        expectedBilledValue,
        1.0,
        false);
  }

  @Test
  // See SWATCH-2494
  void testGetTotalRemittedConsidersAllStatuses() {
    OffsetDateTime startOfUsage = CLOCK.startOfCurrentMonth().plusDays(4);

    BillableUsage usage = givenInstanceHoursUsageForRosa(0.0);
    givenExistingRemittanceForUsage(usage, startOfUsage, 1.0, RemittanceStatus.SUCCEEDED);
    givenExistingRemittanceForUsage(usage, startOfUsage.plusDays(2), 5.0, RemittanceStatus.PENDING);
    // failures be included
    givenExistingRemittanceForUsage(usage, startOfUsage.plusDays(4), 10.0, RemittanceStatus.FAILED);
    // failures with retry after null should be filtered out
    givenExistingRemittanceForUsage(usage, startOfUsage.plusDays(4), 30.0, RemittanceStatus.FAILED);
    givenExistingRemittanceForUsage(usage, startOfUsage.plusDays(4), 20.0, null);

    var result = service.getTotalRemitted(usage);
    assertEquals(26.0, result);
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
        0.0,
        expectedRemitted,
        expectedBilledValue,
        0.5,
        false);
  }

  private BillableUsage givenInstanceHoursUsageForRosa(Double value) {
    return givenInstanceHoursUsageForRosa(value, value);
  }

  private BillableUsage givenInstanceHoursUsageForRosa(Double value, Double currentTotal) {
    return givenInstanceHoursUsageForRosa(CLOCK.startOfCurrentMonth(), value, currentTotal);
  }

  private BillableUsage givenInstanceHoursUsageForRosa(
      OffsetDateTime date, Double value, Double currentTotal) {
    return billable(ROSA, MetricIdUtils.getInstanceHours(), date, value, currentTotal);
  }

  private BillableUsage givenCoresUsageForRosa(Double value, Double currentTotal) {
    return givenUsageForRosa(MetricIdUtils.getCores(), value, currentTotal);
  }

  private BillableUsage givenUsageForRosa(MetricId metricId, Double value, Double currentTotal) {
    return billable(ROSA, metricId, CLOCK.startOfCurrentMonth(), value, currentTotal);
  }

  private BillableUsage billable(
      String product, MetricId metricId, OffsetDateTime date, Double value, Double currentTotal) {
    return new BillableUsage()
        .withUsage(BillableUsage.Usage.PRODUCTION)
        .withTallyId(UUID.randomUUID())
        .withBillingAccountId("aws-account1")
        .withBillingFactor(1.0)
        .withBillingProvider(BillableUsage.BillingProvider.AWS)
        .withOrgId(ORG_ID)
        .withProductId(product)
        .withSla(BillableUsage.Sla.STANDARD)
        .withMetricId(metricId.getValue())
        .withSnapshotDate(date)
        .withStatus(BillableUsage.Status.PENDING)
        .withCurrentTotal(currentTotal)
        .withValue(value);
  }

  private void givenExistingContractForUsage(BillableUsage usage) {
    Contract contract = new Contract();
    contract.setStartDate(usage.getSnapshotDate().minusDays(10));
    givenExistingContractForUsage(contract, usage);
  }

  private Contract contractWithCoverage(BillableUsage usage, int billableUnits) {
    Contract contract = new Contract();
    contract.setStartDate(usage.getSnapshotDate().minusDays(10));
    contract.addMetricsItem(
        new Metric()
            .metricId(
                SubscriptionDefinition.getAwsDimension(
                    usage.getProductId(), MetricId.fromString(usage.getMetricId()).getValue()))
            .value(billableUnits));
    return contract;
  }

  private void givenExistingContractForUsage(Contract contract, BillableUsage usage) {
    try {
      when(contractsApi.getContract(
              eq(usage.getOrgId()),
              eq(usage.getProductId()),
              any(),
              eq(usage.getBillingProvider().value()),
              eq(usage.getBillingAccountId()),
              any(OffsetDateTime.class)))
          .thenReturn(List.of(contract));
    } catch (ApiException e) {
      fail(e);
    }
  }

  private void givenContractApiReturnsContracts(Contract... contracts) throws ApiException {
    when(contractsApi.getContract(any(), any(), any(), any(), any(), any(OffsetDateTime.class)))
        .thenReturn(List.of(contracts));
  }

  private void givenContractApiReturnsNoContracts() throws ApiException {
    when(contractsApi.getContract(any(), any(), any(), any(), any(), any(OffsetDateTime.class)))
        .thenReturn(List.of());
  }

  private List<Contract> givenExistingContract(
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
    return List.of(contract1, updatedContract);
  }

  void givenExistingRemittanceForUsage(BillableUsage usage, double remittedPendingValue) {
    givenExistingRemittanceForUsage(usage, CLOCK.now(), remittedPendingValue);
  }

  void givenExistingRemittanceForUsage(
      BillableUsage usage, OffsetDateTime remittancePendingDate, double remittedPendingValue) {
    givenExistingRemittanceForUsage(
        usage, remittancePendingDate, remittedPendingValue, RemittanceStatus.PENDING);
  }

  @Transactional
  void givenExistingRemittanceForUsage(
      BillableUsage usage,
      OffsetDateTime remittancePendingDate,
      double remittedPendingValue,
      RemittanceStatus status) {
    var newRemittance =
        BillableUsageRemittanceEntity.builder()
            .orgId(usage.getOrgId())
            .billingAccountId(usage.getBillingAccountId())
            .billingProvider(usage.getBillingProvider().value())
            .accumulationPeriod(AccumulationPeriodFormatter.toMonthId(usage.getSnapshotDate()))
            .metricId(MetricId.fromString(usage.getMetricId()).getValue())
            .productId(usage.getProductId())
            .sla(usage.getSla().value())
            .usage(usage.getUsage().value())
            .remittancePendingDate(remittancePendingDate)
            .tallyId(usage.getTallyId())
            .status(status)
            .build();
    // Remitted value should be set to usages metric_value rather than billing_value
    newRemittance.setRemittedPendingValue(remittedPendingValue);
    remittanceRepo.persist(newRemittance);
  }

  private void givenInstanceHoursMetricIsGratisForRosa() {
    var definition =
        originalReference.getSubscriptions().stream()
            .filter(s -> ROSA.equals(s.getId()))
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("ROSA subscription not found in configuration!"));

    var metric =
        definition.getMetrics().stream()
            .filter(m -> MetricIdUtils.getInstanceHours().toString().equals(m.getId()))
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("Instance-Hours not found in the Rosa subscription!"));

    metric.setEnableGratisUsage(true);

    setSubscriptionDefinitionRegistry(mockSubscriptionDefinitionRegistry);
    when(mockSubscriptionDefinitionRegistry.getSubscriptions()).thenReturn(List.of(definition));
  }

  private void performRemittanceTesting(
      OffsetDateTime usageDate,
      Double currentUsage,
      Double currentRemittance,
      Double expectedCoveredByContract,
      Double expectedRemitted,
      Double expectedBilledValue,
      Double billingFactor,
      boolean isContractEnabledTest)
      throws Exception {
    BillableUsage usage = givenInstanceHoursUsageForRosa(usageDate, currentUsage, currentUsage);

    // Enable contracts for the current product.
    givenExistingRemittanceForUsage(usage, CLOCK.now().minusHours(1), currentRemittance);

    setSubscriptionDefinitionRegistry(mockSubscriptionDefinitionRegistry);
    stubSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), billingFactor, isContractEnabledTest);

    List<Contract> contracts = new ArrayList<>();
    // Configure contract data, if defined
    if (isContractEnabledTest) {
      contracts =
          givenExistingContract(
              usage.getOrgId(),
              usage.getProductId(),
              AWS_METRIC_ID,
              usage.getVendorProductCode(),
              usage.getBillingProvider().value(),
              usage.getBillingAccountId(),
              usage.getSnapshotDate());
    }

    service.submitBillableUsage(usage);

    if (expectedRemitted == 0.0) {
      verify(remittanceRepo, times(0)).persistAndFlush(any());
    } else {
      thenRemittanceIsUpdated(usage, usageDate, expectedRemitted, null);
    }

    thenUsageIsSent(usage, expectedBilledValue);
    thenBillableMeterMatches(usage, expectedRemitted);
    if (!contracts.isEmpty()) {
      thenCoveredMeterMatches(usage, expectedCoveredByContract);
    }
  }

  private void thenUsageIsSent(BillableUsage usage, double expectedValue) {
    verify(producer)
        .produce(
            argThat(
                output -> {
                  assertEquals(usage.getUuid(), output.getUuid());
                  assertEquals(usage.getTallyId(), output.getTallyId());
                  assertEquals(expectedValue, output.getValue());
                  assertEquals(usage.getCurrentTotal(), output.getCurrentTotal());
                  return true;
                }));
  }

  private void thenBillableMeterMatches(BillableUsage usage, double expectedBillableValue) {

    var billableMeter =
        getUsageMetric(
            BILLABLE_USAGE_METRIC,
            usage.getProductId(),
            usage.getMetricId(),
            usage.getBillingProvider().value());
    if (expectedBillableValue > 0) {
      assertEquals(
          expectedBillableValue, billableMeter.get().measure().iterator().next().getValue());
      assertEquals(usage.getStatus().value(), billableMeter.get().getId().getTag("status"));
    } else {
      assertFalse(billableMeter.isPresent());
    }
  }

  private void thenCoveredMeterMatches(BillableUsage usage, Double expectedCoveredValue) {
    var coveredMeter =
        getUsageMetric(
            COVERED_USAGE_METRIC,
            usage.getProductId(),
            usage.getMetricId(),
            usage.getBillingProvider().value());
    if (expectedCoveredValue > 0) {
      assertEquals(expectedCoveredValue, coveredMeter.get().measure().iterator().next().getValue());
    } else {
      assertFalse(coveredMeter.isPresent());
    }
  }

  private void thenUsageIsNotSent() {
    verify(producer, times(0)).produce(any());
  }

  private void thenRemittanceIsUpdated(BillableUsage usage, double expectedRemittedPendingValue) {
    thenRemittanceIsUpdated(usage, CLOCK.now(), expectedRemittedPendingValue, null);
  }

  private void thenRemittanceIsUpdatedWithStatusGratis(
      BillableUsage usage, double expectedRemittedPendingValue) {
    thenRemittanceIsUpdated(
        usage, clock.now(), expectedRemittedPendingValue, RemittanceStatus.GRATIS);
  }

  private void thenRemittanceIsUpdated(
      BillableUsage usage,
      OffsetDateTime expectedAccumulationPeriodDate,
      double expectedRemittedPendingValue,
      RemittanceStatus expectedStatus) {
    verify(remittanceRepo)
        .persistAndFlush(
            argThat(
                remittance -> {
                  assertEquals(usage.getTallyId(), remittance.getTallyId());
                  assertEquals(usage.getProductId(), remittance.getProductId());
                  assertEquals(
                      MetricId.fromString(usage.getMetricId()).getValue(),
                      remittance.getMetricId());
                  assertEquals(usage.getBillingProvider().value(), remittance.getBillingProvider());
                  assertEquals(
                      AccumulationPeriodFormatter.toMonthId(expectedAccumulationPeriodDate),
                      remittance.getAccumulationPeriod());
                  assertEquals(expectedRemittedPendingValue, remittance.getRemittedPendingValue());
                  if (expectedStatus != null) {
                    assertEquals(expectedStatus, remittance.getStatus());
                  }
                  return true;
                }));
  }

  private void stubSubscriptionDefinition(
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
            .variants(Set.of(variant))
            .metrics(Set.of(awsMetric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(mockSubscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));
  }

  private static void setSubscriptionDefinitionRegistry(SubscriptionDefinitionRegistry instance) {
    try {
      Field field = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
      field.setAccessible(true);
      field.set(field, instance);
    } catch (Exception e) {
      fail(e);
    }
  }

  private Optional<Meter> getUsageMetric(
      String metric, String productTag, String metricId, String billingProvider) {
    return meterRegistry.getMeters().stream()
        .filter(
            m ->
                metric.equals(m.getId().getName())
                    && productTag.equals(m.getId().getTag("product"))
                    && MetricId.fromString(metricId)
                        .getValue()
                        .equals(m.getId().getTag("metric_id"))
                    && billingProvider.equals(m.getId().getTag("billing_provider")))
        .findFirst();
  }
}
