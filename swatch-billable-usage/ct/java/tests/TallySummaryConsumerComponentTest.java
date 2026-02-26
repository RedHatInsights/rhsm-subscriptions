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

import static api.BillableUsageTestHelper.createTallySummary;
import static api.BillableUsageTestHelper.createTallySummaryWithDefaults;
import static api.BillableUsageTestHelper.createTallySummaryWithGranularity;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.RemittanceErrorCode;
import domain.RemittanceStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.candlepin.subscriptions.billable.usage.TallySnapshot;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallySummaryConsumerComponentTest extends BaseBillableUsageComponentTest {

  private static final double VALUE = 8.0;
  private static final double BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  private static final String AWS_DIMENSION = ROSA.getMetric(CORES).getAwsDimension();
  private static final double CONTRACT_COVERAGE = 1.0;
  private static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();

  @BeforeAll
  static void subscribeToBillableUsageTopics() {
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE);
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE_HOURLY_AGGREGATE);
  }

  /** Verify tally summary is processed and billable usage is produced with no contract coverage. */
  @Test
  public void testBasicTallyToBillableUsageFlow() {
    // Setup wiremock endpoints
    givenNoContractCoverageForRosa();

    // Create and send tally summary
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), VALUE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Verify billable usage is produced
    // Expected: 8.0 × 0.25 = 2.0 four_vcpu_hour units
    double expectedValue = VALUE * BILLING_FACTOR;
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
        1);
  }

  /** Verify billable usage correctly accounts for contract coverage. */
  @Test
  public void testBillableUsageWithContractCoverage() {
    // Setup wiremock endpoints
    contractsWiremock.setupContractCoverage(
        orgId, ROSA.getName(), AWS_DIMENSION, CONTRACT_COVERAGE);

    // Create and send tally summary
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), VALUE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Verify billable usage accounts for contract coverage
    // Expected: (8.0 × 0.25) - 1.0 = 1.0 four_vcpu_hour units
    double expectedValue = VALUE * BILLING_FACTOR - CONTRACT_COVERAGE;
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
        1);
  }

  /**
   * Verify remittance and hourly aggregate match tally snapshot data for a non-contract product
   * with billing factor below one: billable usage and hourly aggregate use the scaled value (e.g.
   * 20 Cores × 0.25 = 5.0); remittance stores the metric/tally value (20.0).
   */
  @Test
  public void testRemittanceMatchesTallyWhenBillingFactorBelowOne() {
    // Given: No contract coverage; product with billing factor below one (ROSA Cores = 0.25)
    givenNoContractCoverageForRosa();
    double tallyValue = 20.0;
    double expectedValueAfterBillingFactor = tallyValue * BILLING_FACTOR; // 20 * 0.25 = 5.0

    // When: One tally snapshot is sent
    TallySummary tallySummary = whenSendTallySummaryWithValue(tallyValue);

    // Then: Billable usage is produced with value 5.0 (tally value × billing factor)
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, ROSA.getName(), expectedValueAfterBillingFactor),
            1);
    assertEquals(
        1, billableUsages.size(), "Expected exactly one billable usage message for the tally");
    BillableUsage usage = billableUsages.get(0);

    // Then: Remittance by tally has the correct remitted value (metric/tally value 20.0)
    thenEachBillableUsageHasOneRemittanceWithValue(List.of(usage), tallyValue);

    // Then: Hourly aggregate is produced with totalValue 5.0 (tally value × billing factor)
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 1);
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFound(aggregates, tallySummary),
        Math.ceil(tallyValue * BILLING_FACTOR));
  }

  /**
   * Verify remittance and hourly aggregate match tally snapshot data for a non-contract product
   * with billing factor equal to one: billable usage, remittance, and hourly aggregate all use the
   * same value (no scaling applied).
   */
  @Test
  public void testRemittanceMatchesTallyWhenBillingFactorEqualOne() {
    // Given: No contract coverage; product with billing factor 1.0 (RHEL addon vCPUs)
    givenNoContractCoverageForRhelAddon();
    double tallyValue = 12.0;

    // When: One tally snapshot is sent for RHEL addon product
    TallySummary tallySummary = whenSendTallySummaryForRhelAddonWithValue(tallyValue);

    // Then: Billable usage is produced with value 12.0 (tally value × 1.0 = tally value)
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, RHEL_PAYG_ADDON.getName(), tallyValue),
            1);
    assertEquals(
        1, billableUsages.size(), "Expected exactly one billable usage message for the tally");
    BillableUsage usage = billableUsages.get(0);

    // Then: Remittance by tally has the correct remitted value (metric/tally value 12.0)
    thenEachBillableUsageHasOneRemittanceWithValue(List.of(usage), tallyValue);

    // Then: Hourly aggregate is produced with totalValue 12.0 (no scaling)
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 1);
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFound(aggregates, tallySummary), tallyValue);
  }

  /**
   * Verify that when both DAILY and HOURLY tally snapshots are sent, only the HOURLY snapshot
   * produces billable usage and DAILY is filtered out by the consumer.
   */
  @Test
  public void testOnlyHourlyGranularityIsProcessed() {
    // Setup wiremock endpoints
    givenNoContractCoverageForRosa();

    UUID dailySnapshotId = UUID.randomUUID();
    UUID hourlySnapshotId = UUID.randomUUID();

    // Create and send tally summary with DAILY granularity
    TallySummary dailyTallySummary =
        createTallySummaryWithGranularity(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            VALUE,
            TallySnapshot.Granularity.DAILY,
            dailySnapshotId);
    kafkaBridge.produceKafkaMessage(TALLY, dailyTallySummary);

    // Create and send tally summary with HOURLY granularity
    TallySummary hourlyTallySummary =
        createTallySummaryWithGranularity(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            VALUE,
            TallySnapshot.Granularity.HOURLY,
            hourlySnapshotId);
    kafkaBridge.produceKafkaMessage(TALLY, hourlyTallySummary);

    // Verify that only the HOURLY tally summary produces billable usage
    List<BillableUsage> messages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA.getName()), 1);

    // Assert exactly 1 message was produced
    assertEquals(1, messages.size(), "Expected exactly 1 billable usage for HOURLY granularity");

    // Assert it came from the HOURLY tally (not DAILY)
    assertEquals(
        hourlySnapshotId,
        messages.get(0).getTallyId(),
        "Billable usage should be generated from HOURLY tally snapshot");
  }

  /** Verify that status consumer updates remittance with succeeded status. */
  @Test
  public void testStatusConsumerUpdatesRemittanceWithSucceededStatus() {
    // Given a pending remittance exists
    BillableUsage billableUsage = givenPendingRemittanceExists();

    // Verify initial PENDING status via API (remittance created automatically)
    String tallyId = billableUsage.getTallyId().toString();
    waitForRemittanceStatus(tallyId, RemittanceStatus.PENDING);

    // Capture expected billed_on time before creating status update
    OffsetDateTime expectedBilledOnTime = OffsetDateTime.now(ZoneOffset.UTC);

    // Create and send status update message with SUCCEEDED status
    BillableUsageAggregate statusUpdate =
        givenBillableUsageAggregate(
            orgId,
            ROSA.getName(),
            billableUsage.getBillingAccountId(),
            BillableUsage.Status.SUCCEEDED,
            null,
            expectedBilledOnTime, // Use the captured time
            List.of(billableUsage.getUuid().toString()));

    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_STATUS, statusUpdate);

    // Verify final SUCCEEDED status via API (polling waits for consumer processing)
    waitForRemittanceStatus(tallyId, RemittanceStatus.SUCCEEDED);

    // Verify billed_on field was populated with proper timestamp
    List<TallyRemittance> remittances = service.getRemittancesByTallyId(tallyId);
    assertNotNull(remittances, "Remittances should exist");
    assertFalse(remittances.isEmpty(), "Should have at least one remittance");
    verifyBilledOnTimestamp(remittances.get(0), expectedBilledOnTime);
  }

  /** Verify that status consumer updates remittance with failed status. */
  @Test
  public void testStatusConsumerUpdatesRemittanceWithFailedStatus() {
    // Given a pending remittance exists
    BillableUsage billableUsage = givenPendingRemittanceExists();

    // Verify initial PENDING status via API (remittance created automatically)
    String tallyId = billableUsage.getTallyId().toString();
    waitForRemittanceStatus(tallyId, RemittanceStatus.PENDING);

    // Create and send status update message with FAILED status
    BillableUsageAggregate statusUpdate =
        givenBillableUsageAggregate(
            orgId,
            ROSA.getName(),
            billableUsage.getBillingAccountId(),
            BillableUsage.Status.FAILED,
            BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND,
            null,
            List.of(billableUsage.getUuid().toString()));

    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_STATUS, statusUpdate);

    // Verify final FAILED status via API (polling waits for consumer processing)
    waitForRemittanceStatus(tallyId, RemittanceStatus.FAILED);

    // Verify error code was populated properly
    List<TallyRemittance> remittances = service.getRemittancesByTallyId(tallyId);
    assertNotNull(remittances, "Remittances should exist");
    assertFalse(remittances.isEmpty(), "Should have at least one remittance");
    verifyErrorCode(remittances.get(0), RemittanceErrorCode.SUBSCRIPTION_NOT_FOUND.name());
  }

  /**
   * Verify that usage from different billing accounts is not aggregated together. When the same org
   * sends tally snapshots with two different billing account IDs, two separate hourly aggregate
   * messages must be produced (one per billing account).
   */
  @Test
  public void testMultipleTallySummariesNotAggregatedForDifferentBillingAccountIds() {
    // Given: No contract coverage and two distinct billing account IDs for the same org
    givenNoContractCoverageForRosa();
    double value1 = 10.0;
    double value2 = 6.0;

    TallySummary tallySummary1 = whenSendTallySummaryWithValue(value1);
    TallySummary tallySummary2 = whenSendTallySummaryWithValue(value2);

    // Then: Two separate hourly aggregate messages are produced (one per billing account).
    // Use a longer timeout because aggregation window must close before messages are emitted.
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 2);

    BillableUsageAggregate aggregateForAccount1 =
        thenBillableUsageAggregateIsFound(aggregates, tallySummary1);
    thenBillableUsageAggregateHasTotalValue(
        aggregateForAccount1, Math.ceil(value1 * BILLING_FACTOR));

    BillableUsageAggregate aggregateForAccount2 =
        thenBillableUsageAggregateIsFound(aggregates, tallySummary2);
    thenBillableUsageAggregateHasTotalValue(
        aggregateForAccount2, Math.ceil(value2 * BILLING_FACTOR));
  }

  /**
   * Verify that when multiple tally snapshots are sent for different orgs, a remittance record is
   * created for each org and an hourly aggregate message is sent for each org with the correct
   * totalValue per org.
   */
  @Test
  public void testMultipleOrgsProduceRemittanceAndHourlyAggregatePerOrg() {
    // Given: No contract coverage for two distinct orgs, with different tally values per org
    double value1 = 10.0;
    double value2 = 6.0;
    String org1 = orgId;
    String org2 = RandomUtils.generateRandom();
    Set<String> orgIds = Set.of(org1, org2);
    contractsWiremock.setupNoContractCoverage(org1, ROSA.getName());
    contractsWiremock.setupNoContractCoverage(org2, ROSA.getName());

    // When: Tally snapshots are sent for each org (different values per org)
    whenTallySummaryIsSentForOrg(org1, value1);
    whenTallySummaryIsSentForOrg(org2, value2);

    // Then: Billable usage is produced for each org with expected value per org
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesAnyOrg(orgIds, ROSA.getName()),
            2);
    assertEquals(
        2, billableUsages.size(), "Expected exactly 2 billable usage messages (one per org)");
    thenBillableUsageHasValueForOrg(billableUsages, org1, value1);
    thenBillableUsageHasValueForOrg(billableUsages, org2, value2);

    // Then: Remittance table has a record for each org (one remittance per tally)
    thenRemittanceExistsForEachBillableUsage(billableUsages);

    // Then: An hourly aggregate message is sent for each org with correct totalValue
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgIds, 2);
    assertEquals(
        2, aggregates.size(), "Expected exactly 2 hourly aggregate messages (one per org)");
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForOrg(aggregates, org1),
        Math.ceil(value1 * BILLING_FACTOR));
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForOrg(aggregates, org2),
        Math.ceil(value2 * BILLING_FACTOR));
  }

  /**
   * Verify that when multiple tally snapshots are sent for the same org and billing account but
   * different metric IDs, a remittance is created for each metric and an hourly aggregate message
   * is sent for each metric with the correct totalValue.
   */
  @Test
  public void testMultipleMetricIdsProduceRemittanceAndHourlyAggregatePerMetric() {
    // Given: No contract coverage, one org, one billing account, two different metrics
    double valueCores = 4.0; // Cores (billing factor 0.25 -> 1.0)
    double valueInstanceHours = 9.0; // Instance-hours (billing factor 1.0 -> 9.0)
    String billingAccountId = RandomUtils.generateRandom();
    givenNoContractCoverageForRosa();

    // When: Tally snapshots are sent for the same org/billing account with different metrics
    whenTallySummariesAreSentForTwoMetrics(billingAccountId, valueCores, valueInstanceHours);

    // Then: Billable usage is produced for each metric with expected value per metric
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA.getName()), 2);
    assertEquals(
        2, billableUsages.size(), "Expected exactly 2 billable usage messages (one per metric)");
    thenBillableUsageHasValueForMetric(
        billableUsages, CORES.toString(), valueCores * BILLING_FACTOR);
    thenBillableUsageHasValueForMetric(
        billableUsages, INSTANCE_HOURS.toString(), valueInstanceHours);

    // Then: Remittance table has a record for each (one remittance per tally)
    thenRemittanceExistsForEachBillableUsage(billableUsages);

    // Then: An hourly aggregate message is sent for each metric with correct totalValue
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 2);
    thenHourlyAggregatePerMetricHasExpectedTotalValues(
        aggregates, valueCores * BILLING_FACTOR, valueInstanceHours);
  }

  /**
   * Verify that when multiple tally snapshots are sent for the same org and billing account but
   * different product IDs, a remittance is created for each product and an hourly aggregate message
   * is sent for each product with the correct totalValue.
   */
  @Test
  public void testMultipleProductIdsProduceRemittanceAndHourlyAggregatePerProduct() {
    // Given: No contract coverage for two distinct products, same org and billing account
    double valueRosa = 40.0; // ROSA Cores (billing factor 0.25 -> 10.0)
    double valueRhelAddon = 17.0; // RHEL addon vCPUs (billing factor 1.0 -> 17.0)
    String billingAccountId = RandomUtils.generateRandom();
    Set<String> productIds = Set.of(ROSA.getName(), RHEL_PAYG_ADDON.getName());
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
    contractsWiremock.setupNoContractCoverage(orgId, RHEL_PAYG_ADDON.getName());

    // When: Tally snapshots are sent for the same org/billing account with different products
    whenTallySummariesAreSentForTwoProducts(billingAccountId, valueRosa, valueRhelAddon);

    // Then: Billable usage is produced for each product with expected value per product
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatchesAnyProduct(orgId, productIds), 2);
    assertEquals(
        2, billableUsages.size(), "Expected exactly 2 billable usage messages (one per product)");
    thenBillableUsageHasValueForProduct(billableUsages, ROSA.getName(), valueRosa * BILLING_FACTOR);
    thenBillableUsageHasValueForProduct(billableUsages, RHEL_PAYG_ADDON.getName(), valueRhelAddon);

    // Then: Remittance table has a record for each (one remittance per tally)
    thenRemittanceExistsForEachBillableUsage(billableUsages);

    // Then: An hourly aggregate message is sent for each product with correct totalValue
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, productIds, 2);
    thenHourlyAggregatePerProductHasExpectedTotalValues(
        aggregates, valueRosa * BILLING_FACTOR, valueRhelAddon);
  }

  /**
   * Verify that when a new tally summary has a current_total greater than already remitted usage, a
   * new remittance is created with the difference. First tally (6) creates remittance with value 6;
   * after it succeeds, second tally (10) creates remittance with value 4 (10 - 6). Uses RHEL addon
   * (billing factor 1.0) so remittance values match tally values directly.
   */
  @Test
  public void testVerifyRemittanceDiffWhenTallySummaryTotalGreaterThanRemittanceUsage() {
    // Given: First remittance exists (value 6) and has succeeded
    double valueExisting = 6.0;
    double valueLargerNew = 10.0;
    double expectedDiff = valueLargerNew - valueExisting;
    String billingAccountId = RandomUtils.generateRandom();
    BillableUsage firstBillableUsage =
        givenFirstRemittanceSucceeded(valueExisting, billingAccountId);

    // When: Second tally summary is sent with value 10 (greater than first)
    whenSecondTallySummaryIsSent(valueLargerNew, billingAccountId);

    // Then: Second billable usage is produced with the diff value (4)
    List<BillableUsage> secondBillableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, RHEL_PAYG_ADDON.getName(), expectedDiff),
            1);
    assertEquals(
        1, secondBillableUsages.size(), "Expected exactly 1 billable usage for second tally");

    // Then: Two remittances exist - first with value 6, second with value 4 (the diff)
    thenRemittanceHasValue(firstBillableUsage.getTallyId().toString(), valueExisting);
    thenRemittanceHasValue(secondBillableUsages.get(0).getTallyId().toString(), expectedDiff);
  }

  /**
   * Verify that usage from last month is not tallied with usage from the current month: two tally
   * snapshots with different snapshot dates (last month and current month) produce two separate
   * remittances (one per accumulation period), not aggregated into one.
   */
  @Test
  public void testLastMonthUsageNotTalliedWithCurrentMonth() {
    // Given: No contract coverage, one org, one billing account, same product/metric for both
    double value = 5.0;
    String billingAccountId = RandomUtils.generateRandom();
    OffsetDateTime snapshotDateLastMonth = OffsetDateTime.now(ZoneOffset.UTC).minusDays(35);
    OffsetDateTime snapshotDateCurrentMonth = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
    contractsWiremock.setupNoContractCoverage(orgId, RHEL_PAYG_ADDON.getName());

    // When: Tally snapshots are sent for last month and current month (same account/product/metric)
    whenTallySummariesAreSentWithSnapshotDates(
        billingAccountId, value, snapshotDateLastMonth, snapshotDateCurrentMonth);

    // Then: Both tallies are processed (2 billable usage messages)
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatches(orgId, RHEL_PAYG_ADDON.getName()),
            2);
    assertEquals(2, billableUsages.size(), "Expected exactly 2 billable usage messages");

    // Then: Each usage has one remittance with accumulation period and value set
    thenEachBillableUsageHasOneRemittanceWithValue(billableUsages, value);

    // Then: The two remittances are for different months (not aggregated)
    String expectedPeriodLastMonth =
        snapshotDateLastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    String expectedPeriodCurrentMonth =
        snapshotDateCurrentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    thenRemittancesSpanExpectedMonths(
        billableUsages, expectedPeriodLastMonth, expectedPeriodCurrentMonth);
  }

  private void givenNoContractCoverageForRosa() {
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
  }

  private void givenNoContractCoverageForRhelAddon() {
    contractsWiremock.setupNoContractCoverage(orgId, RHEL_PAYG_ADDON.getName());
  }

  /**
   * Sets up test preconditions: creates tally summary, waits for billable usage with pending
   * remittance
   *
   * @return The generated BillableUsage that has an associated pending remittance
   */
  private BillableUsage givenPendingRemittanceExists() {
    // Setup wiremock endpoints
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // Create and send tally summary to generate billable usage with remittance
    whenSendTallySummaryWithValue(VALUE);

    // Wait for billable usage to be produced
    double expectedValue = VALUE * BILLING_FACTOR;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
            1);

    assertEquals(1, billableUsages.size(), "Expected exactly 1 billable usage message");
    return billableUsages.get(0);
  }

  /**
   * Sets up first remittance (RHEL addon, billing factor 1.0): sends tally, waits for billable
   * usage, sends SUCCEEDED status update.
   */
  private BillableUsage givenFirstRemittanceSucceeded(double value, String billingAccountId) {
    givenNoContractCoverageForRhelAddon();

    TallySummary firstTallySummary =
        createTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            value,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, firstTallySummary);

    List<BillableUsage> firstBillableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, RHEL_PAYG_ADDON.getName(), value),
            1);
    assertEquals(
        1, firstBillableUsages.size(), "Expected exactly 1 billable usage for first tally");
    BillableUsage firstBillableUsage = firstBillableUsages.get(0);
    waitForRemittanceStatus(firstBillableUsage.getTallyId().toString(), RemittanceStatus.PENDING);

    BillableUsageAggregate statusUpdate =
        givenBillableUsageAggregate(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            billingAccountId,
            BillableUsage.Status.SUCCEEDED,
            null,
            OffsetDateTime.now(ZoneOffset.UTC),
            List.of(firstBillableUsage.getUuid().toString()));
    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_STATUS, statusUpdate);
    waitForRemittanceStatus(firstBillableUsage.getTallyId().toString(), RemittanceStatus.SUCCEEDED);

    return firstBillableUsage;
  }

  /** Builds a billable usage aggregate (status update) for use in status consumer tests. */
  private BillableUsageAggregate givenBillableUsageAggregate(
      String orgId,
      String productId,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      OffsetDateTime billedOn,
      List<String> remittanceUuids) {
    return givenBillableUsageAggregate(
        orgId,
        productId,
        CORES.toString(),
        billingAccountId,
        status,
        errorCode,
        billedOn,
        remittanceUuids);
  }

  private BillableUsageAggregate givenBillableUsageAggregate(
      String orgId,
      String productId,
      String metricId,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      OffsetDateTime billedOn,
      List<String> remittanceUuids) {

    var aggregateKey = new BillableUsageAggregateKey();
    aggregateKey.setOrgId(orgId);
    aggregateKey.setProductId(productId);
    aggregateKey.setMetricId(metricId);
    aggregateKey.setSla("Premium");
    aggregateKey.setUsage("Production");
    aggregateKey.setBillingProvider(BillingProvider.AWS.name());
    aggregateKey.setBillingAccountId(billingAccountId);

    var aggregate = new BillableUsageAggregate();
    aggregate.setAggregateKey(aggregateKey);
    aggregate.setStatus(status);
    aggregate.setRemittanceUuids(remittanceUuids);
    aggregate.setTotalValue(BigDecimal.valueOf(5.0));
    aggregate.setWindowTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
    aggregate.setAggregateId(UUID.randomUUID());
    Set<OffsetDateTime> snapshotDateSet = new HashSet<>();
    snapshotDateSet.add(OffsetDateTime.now(ZoneOffset.UTC).minusDays(3));
    aggregate.setSnapshotDates(snapshotDateSet);

    if (errorCode != null) {
      aggregate.setErrorCode(errorCode);
    }
    if (billedOn != null) {
      aggregate.setBilledOn(billedOn);
    }

    return aggregate;
  }

  private List<BillableUsageAggregate> whenHourlyAggregatesAreReceived(
      String orgId, int expectedCount) {
    return kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_HOURLY_AGGREGATE,
        MessageValidators.billableUsageAggregateMatchesOrg(orgId),
        expectedCount,
        AwaitilitySettings.defaults()
            .onConditionNotMet(service::flushBillableUsageAggregationTopic));
  }

  private List<BillableUsageAggregate> whenHourlyAggregatesAreReceived(
      Set<String> orgIds, int expectedCount) {
    return kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_HOURLY_AGGREGATE,
        MessageValidators.billableUsageAggregateMatchesAnyOrg(orgIds),
        expectedCount,
        AwaitilitySettings.defaults()
            .onConditionNotMet(service::flushBillableUsageAggregationTopic));
  }

  private List<BillableUsageAggregate> whenHourlyAggregatesAreReceived(
      String orgId, Set<String> productIds, int expectedCount) {
    return kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_HOURLY_AGGREGATE,
        MessageValidators.billableUsageAggregateMatchesAnyProduct(orgId, productIds),
        expectedCount,
        AwaitilitySettings.defaults()
            .onConditionNotMet(service::flushBillableUsageAggregationTopic));
  }

  private TallySummary whenSendTallySummaryWithValue(double value) {
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), value);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private TallySummary whenSendTallySummaryForRhelAddonWithValue(double value) {
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, RHEL_PAYG_ADDON.getName(), VCPUS.toString(), value);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private void whenSecondTallySummaryIsSent(double value, String billingAccountId) {
    TallySummary secondTallySummary =
        createTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            value,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, secondTallySummary);
  }

  private void whenTallySummaryIsSentForOrg(String orgId, double value) {
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), value);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
  }

  private void whenTallySummariesAreSentForTwoMetrics(
      String billingAccountId, double valueCores, double valueInstanceHours) {
    TallySummary tallySummaryCores =
        createTallySummary(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            valueCores,
            BillingProvider.AWS,
            billingAccountId);
    TallySummary tallySummaryInstanceHours =
        createTallySummary(
            orgId,
            ROSA.getName(),
            INSTANCE_HOURS.toString(),
            valueInstanceHours,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummaryCores);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummaryInstanceHours);
  }

  private void whenTallySummariesAreSentWithSnapshotDates(
      String billingAccountId,
      double value,
      OffsetDateTime snapshotDateLastMonth,
      OffsetDateTime snapshotDateCurrentMonth) {
    TallySummary tallyLastMonth =
        createTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            value,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDateLastMonth);
    TallySummary tallyCurrentMonth =
        createTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            value,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDateCurrentMonth);
    kafkaBridge.produceKafkaMessage(TALLY, tallyLastMonth);
    kafkaBridge.produceKafkaMessage(TALLY, tallyCurrentMonth);
  }

  private void whenTallySummariesAreSentForTwoProducts(
      String billingAccountId, double valueRosa, double valueRhelAddon) {
    TallySummary tallySummaryRosa =
        createTallySummary(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            valueRosa,
            BillingProvider.AWS,
            billingAccountId);
    TallySummary tallySummaryRhelAddon =
        createTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.toString(),
            valueRhelAddon,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummaryRosa);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummaryRhelAddon);
  }

  /** Wait for remittance to reach expected status using API polling */
  private void waitForRemittanceStatus(String tallyId, RemittanceStatus expectedStatus) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<TallyRemittance> remittances = service.getRemittancesByTallyId(tallyId);
              assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
              assertFalse(remittances.isEmpty(), "Should have at least one remittance");

              String actualStatusString = remittances.get(0).getStatus();
              RemittanceStatus actualStatus = RemittanceStatus.valueOf(actualStatusString);
              assertEquals(
                  expectedStatus,
                  actualStatus,
                  "Expected status " + expectedStatus + " but got " + actualStatus);
            });
  }

  /** Verify that billed_on timestamp is populated and within expected range */
  private void verifyBilledOnTimestamp(TallyRemittance remittance, OffsetDateTime expectedTime) {
    assertNotNull(remittance.getBilledOn(), "billedOn field should be present");

    OffsetDateTime actualBilledOn = remittance.getBilledOn();
    Duration timeDiff = Duration.between(expectedTime, actualBilledOn).abs();

    assertTrue(
        timeDiff.toSeconds() <= 1,
        String.format(
            "billedOn timestamp %s should be within 1 second of expected time %s",
            actualBilledOn, expectedTime));
  }

  /** Verify that error code matches expected value */
  private void verifyErrorCode(TallyRemittance remittance, String expectedErrorCode) {
    assertNotNull(remittance.getErrorCode(), "errorCode field should be present");
    assertEquals(
        expectedErrorCode,
        remittance.getErrorCode(),
        String.format(
            "Expected error code '%s' but got '%s'", expectedErrorCode, remittance.getErrorCode()));
  }

  private BillableUsageAggregate thenBillableUsageAggregateIsFound(
      List<BillableUsageAggregate> aggregates, TallySummary tallySummary) {
    String billingAccountId = tallySummary.getTallySnapshots().get(0).getBillingAccountId();
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(
                a ->
                    billingAccountId.equals(
                        a.getAggregateKey() != null
                            ? a.getAggregateKey().getBillingAccountId()
                            : null))
            .findFirst()
            .orElse(null);
    assertNotNull(found, "Expected one aggregate for billing account " + billingAccountId);
    return found;
  }

  private BillableUsageAggregate thenBillableUsageAggregateIsFoundForOrg(
      List<BillableUsageAggregate> aggregates, String orgId) {
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(
                a -> a.getAggregateKey() != null && orgId.equals(a.getAggregateKey().getOrgId()))
            .findFirst()
            .orElse(null);
    assertNotNull(found, "Expected one aggregate for org " + orgId);
    return found;
  }

  private void thenBillableUsageHasValueForOrg(
      List<BillableUsage> billableUsages, String orgId, double value) {
    BillableUsage usage =
        billableUsages.stream().filter(u -> orgId.equals(u.getOrgId())).findFirst().orElse(null);
    assertNotNull(usage, "Expected billable usage for org " + orgId);
    double expectedValue = Math.ceil(value * BILLING_FACTOR);
    assertEquals(
        expectedValue,
        usage.getValue(),
        0.001,
        "Billable usage value for org " + orgId + " should be " + expectedValue);
  }

  private void thenRemittanceExistsForEachBillableUsage(List<BillableUsage> billableUsages) {
    for (BillableUsage usage : billableUsages) {
      String tallyId = usage.getTallyId().toString();
      waitForRemittanceStatus(tallyId, RemittanceStatus.PENDING);
      List<TallyRemittance> remittances = service.getRemittancesByTallyId(tallyId);
      assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
      assertFalse(
          remittances.isEmpty(), "Should have at least one remittance for org " + usage.getOrgId());
    }
  }

  private void thenRemittanceHasValue(String tallyId, double expectedValue) {
    List<TallyRemittance> remittances = service.getRemittancesByTallyId(tallyId);
    assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
    assertEquals(1, remittances.size(), "Exactly one remittance per tally");
    assertEquals(
        expectedValue,
        remittances.get(0).getRemittedPendingValue(),
        0.001,
        "Remittance should have value " + expectedValue);
  }

  private BillableUsageAggregate thenBillableUsageAggregateIsFoundForMetric(
      List<BillableUsageAggregate> aggregates, String metricId) {
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(
                a ->
                    a.getAggregateKey() != null
                        && metricId.equals(a.getAggregateKey().getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(found, "Expected one aggregate for metric " + metricId);
    return found;
  }

  private void thenBillableUsageAggregateHasTotalValue(
      BillableUsageAggregate aggregate, double expectedValue) {
    assertEquals(
        expectedValue,
        aggregate.getTotalValue().doubleValue(),
        0.001,
        "Aggregate should have total value " + expectedValue);
  }

  private void thenHourlyAggregatePerMetricHasExpectedTotalValues(
      List<BillableUsageAggregate> aggregates,
      double expectedValueCores,
      double expectedValueInstanceHours) {
    assertEquals(
        2, aggregates.size(), "Expected exactly 2 hourly aggregate messages (one per metric)");
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForMetric(aggregates, CORES.toString()),
        expectedValueCores);
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForMetric(aggregates, INSTANCE_HOURS.toString()),
        expectedValueInstanceHours);
  }

  private void thenBillableUsageHasValueForMetric(
      List<BillableUsage> billableUsages, String metricId, double expectedValue) {
    BillableUsage usage =
        billableUsages.stream()
            .filter(u -> metricId.equals(u.getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(usage, "Expected billable usage for metric " + metricId);
    assertEquals(
        expectedValue,
        usage.getValue(),
        0.001,
        "Billable usage value for metric " + metricId + " should be " + expectedValue);
  }

  private BillableUsageAggregate thenBillableUsageAggregateIsFoundForProduct(
      List<BillableUsageAggregate> aggregates, String productId) {
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(
                a ->
                    a.getAggregateKey() != null
                        && productId.equals(a.getAggregateKey().getProductId()))
            .findFirst()
            .orElse(null);
    assertNotNull(found, "Expected one aggregate for product " + productId);
    return found;
  }

  private void thenBillableUsageHasValueForProduct(
      List<BillableUsage> billableUsages, String productId, double expectedValue) {
    BillableUsage usage =
        billableUsages.stream()
            .filter(u -> productId.equals(u.getProductId()))
            .findFirst()
            .orElse(null);
    assertNotNull(usage, "Expected billable usage for product " + productId);
    assertEquals(
        expectedValue,
        usage.getValue(),
        0.001,
        "Billable usage value for product " + productId + " should be " + expectedValue);
  }

  /**
   * Asserts each billable usage has exactly one remittance with non-null accumulation period and
   * expected value. The service overwrites usage.snapshotDate before sending to Kafka, so
   * accumulation period is not matched to usage here; use thenRemittancesSpanExpectedMonths for
   * month coverage.
   */
  private void thenEachBillableUsageHasOneRemittanceWithValue(
      List<BillableUsage> billableUsages, double expectedValue) {
    for (BillableUsage usage : billableUsages) {
      waitForRemittanceStatus(usage.getTallyId().toString(), RemittanceStatus.PENDING);
      List<TallyRemittance> remittances =
          service.getRemittancesByTallyId(usage.getTallyId().toString());
      assertNotNull(remittances, "Remittances should exist for tallyId: " + usage.getTallyId());
      assertEquals(1, remittances.size(), "Exactly one remittance per tally");
      TallyRemittance remittance = remittances.get(0);
      assertNotNull(remittance.getAccumulationPeriod(), "Accumulation period should be set");
      double actualValue =
          remittance.getRemittedPendingValue() != null ? remittance.getRemittedPendingValue() : 0.0;
      assertEquals(expectedValue, actualValue, 0.001, "Remittance value should match tally value");
    }
  }

  /**
   * Asserts the remittances for the given usages have exactly the two expected accumulation
   * periods.
   */
  private void thenRemittancesSpanExpectedMonths(
      List<BillableUsage> billableUsages,
      String expectedPeriodFirstMonth,
      String expectedPeriodSecondMonth) {
    List<String> periods =
        billableUsages.stream()
            .map(
                u ->
                    service
                        .getRemittancesByTallyId(u.getTallyId().toString())
                        .get(0)
                        .getAccumulationPeriod())
            .sorted()
            .toList();
    List<String> expectedPeriods =
        List.of(expectedPeriodFirstMonth, expectedPeriodSecondMonth).stream().sorted().toList();
    assertEquals(
        expectedPeriods,
        periods,
        "Should have one remittance for last month and one for current month");
  }

  private void thenHourlyAggregatePerProductHasExpectedTotalValues(
      List<BillableUsageAggregate> aggregates,
      double expectedValueRosa,
      double expectedValueRhelAddon) {
    assertEquals(
        2, aggregates.size(), "Expected exactly 2 hourly aggregate messages (one per product)");
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForProduct(aggregates, ROSA.getName()), expectedValueRosa);
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForProduct(aggregates, RHEL_PAYG_ADDON.getName()),
        expectedValueRhelAddon);
  }
}
