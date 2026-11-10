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

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_HOURLY_AGGREGATE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static domain.BillingProvider.AWS;
import static domain.Product.RHEL_PAYG_ADDON;
import static domain.Product.ROSA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static utils.TallySummaryBuilder.buildTallySummaryWithDefaults;
import static utils.TallySummaryBuilder.buildTallySummaryWithGranularity;

import api.MessageValidators;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import domain.RemittanceStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.TallySnapshot;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;
import utils.TallySummaryBuilder;

public class TallySummaryConsumerComponentTest extends BaseBillableUsageComponentTest {

  private static final double VALUE = 8.0;
  private static final double CONTRACT_COVERAGE = 1.0;

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC001")
  void shouldCreateBillableUsageWithoutContractCoverage() {
    // Given: No contract coverage for ROSA.
    givenNoContractCoverageForProduct(ROSA);

    // When: A tally summary is sent.
    TallySummary tallySummary =
        buildTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), VALUE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Billable usage is produced with the billing factor applied.
    // Expected: 8.0 × 0.25 = 2.0 four_vcpu_hour units
    double expectedValue = VALUE * ROSA.getBillingFactor(CORES);
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedValue),
        1);
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC002")
  void shouldApplyContractCoverageToBillableUsage() {
    // Given: Contract coverage for ROSA.
    givenRosaCoresContractCoverage(CONTRACT_COVERAGE);

    // When: A tally summary is sent.
    TallySummary tallySummary =
        buildTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), VALUE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Billable usage is reduced by contract coverage.
    // Expected: (8.0 × 0.25) - 1.0 = 1.0 four_vcpu_hour units
    double expectedValue = VALUE * ROSA.getBillingFactor(CORES) - CONTRACT_COVERAGE;
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedValue),
        1);
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC003")
  void shouldKeepTallyValueInRemittanceBelowUnitBillingFactor() {
    // Given: No contract coverage; product with billing factor below one (ROSA Cores = 0.25)
    givenNoContractCoverageForProduct(ROSA);
    double tallyValue = 20.0;
    double expectedValueAfterBillingFactor =
        tallyValue * ROSA.getBillingFactor(CORES); // 20 * 0.25 = 5.0

    // When: One tally snapshot is sent
    TallySummary tallySummary = whenSendTallySummaryForRosaWithCoresValue(tallyValue);

    // Then: Billable usage is produced with value 5.0 (tally value × billing factor)
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, ROSA, expectedValueAfterBillingFactor),
            1);
    assertEquals(
        1, billableUsages.size(), "Expected exactly one billable usage message for the tally");
    BillableUsage usage = billableUsages.getFirst();

    // Then: Remittance by tally has the correct remitted value (metric/tally value 20.0)
    thenEachBillableUsageHasOneRemittanceWithValue(List.of(usage), tallyValue);

    // Then: Hourly aggregate is produced with totalValue 5.0 (tally value × billing factor)
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 1);
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFound(aggregates, tallySummary),
        Math.ceil(tallyValue * ROSA.getBillingFactor(CORES)));
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC004")
  void shouldKeepTallyValueWhenBillingFactorIsOne() {
    // Given: No contract coverage; product with billing factor 1.0 (RHEL addon vCPUs)
    givenNoContractCoverageForProduct(RHEL_PAYG_ADDON);
    double tallyValue = 12.0;

    // When: One tally snapshot is sent for RHEL addon product
    TallySummary tallySummary = whenSendTallySummaryForRhelAddonWithVCPUsValue(tallyValue);

    // Then: Billable usage is produced with value 12.0 (tally value × 1.0 = tally value)
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, RHEL_PAYG_ADDON, tallyValue),
            1);
    assertEquals(
        1, billableUsages.size(), "Expected exactly one billable usage message for the tally");
    BillableUsage usage = billableUsages.getFirst();

    // Then: Remittance by tally has the correct remitted value (metric/tally value 12.0)
    thenEachBillableUsageHasOneRemittanceWithValue(List.of(usage), tallyValue);

    // Then: Hourly aggregate is produced with totalValue 12.0 (no scaling)
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 1);
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFound(aggregates, tallySummary), tallyValue);
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC005")
  void shouldProcessOnlyHourlyGranularity() {
    // Given: No contract coverage for ROSA.
    givenNoContractCoverageForProduct(ROSA);

    UUID dailySnapshotId = UUID.randomUUID();
    UUID hourlySnapshotId = UUID.randomUUID();

    // Create and send tally summary with DAILY granularity
    TallySummary dailyTallySummary =
        buildTallySummaryWithGranularity(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            VALUE,
            TallySnapshot.Granularity.DAILY,
            dailySnapshotId);
    kafkaBridge.produceKafkaMessage(TALLY, dailyTallySummary);

    // Create and send tally summary with HOURLY granularity
    TallySummary hourlyTallySummary =
        buildTallySummaryWithGranularity(
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
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA), 1);

    // Assert exactly 1 message was produced
    assertEquals(1, messages.size(), "Expected exactly 1 billable usage for HOURLY granularity");

    // Assert it came from the HOURLY tally (not DAILY)
    assertEquals(
        hourlySnapshotId,
        messages.get(0).getTallyId(),
        "Billable usage should be generated from HOURLY tally snapshot");
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC006")
  void shouldKeepDifferentBillingAccountsSeparate() {
    // Given: No contract coverage and two distinct billing account IDs for the same org
    givenNoContractCoverageForProduct(ROSA);
    double value1 = 10.0;
    double value2 = 6.0;

    TallySummary tallySummary1 = whenSendTallySummaryForRosaWithCoresValue(value1);
    TallySummary tallySummary2 = whenSendTallySummaryForRosaWithCoresValue(value2);

    // Then: Two separate hourly aggregate messages are produced (one per billing account).
    // Use a longer timeout because aggregation window must close before messages are emitted.
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 2);

    BillableUsageAggregate aggregateForAccount1 =
        thenBillableUsageAggregateIsFound(aggregates, tallySummary1);
    thenBillableUsageAggregateHasTotalValue(
        aggregateForAccount1, Math.ceil(value1 * ROSA.getBillingFactor(CORES)));

    BillableUsageAggregate aggregateForAccount2 =
        thenBillableUsageAggregateIsFound(aggregates, tallySummary2);
    thenBillableUsageAggregateHasTotalValue(
        aggregateForAccount2, Math.ceil(value2 * ROSA.getBillingFactor(CORES)));
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC007")
  void shouldCreateRemittanceAndAggregatePerOrg() {
    // Given: No contract coverage for two distinct orgs, with different tally values per org
    double value1 = 10.0;
    double value2 = 6.0;
    String org1 = orgId;
    String org2 = givenOrgId();
    Set<String> orgIds = Set.of(org1, org2);
    givenNoContractCoverageForProduct(org1, ROSA);
    givenNoContractCoverageForProduct(org2, ROSA);

    // When: Tally snapshots are sent for each org (different values per org)
    whenTallySummaryIsSentForOrg(org1, value1);
    whenTallySummaryIsSentForOrg(org2, value2);

    // Then: Billable usage is produced for each org with expected value per org
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatchesAnyOrg(orgIds, ROSA), 2);
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
        Math.ceil(value1 * ROSA.getBillingFactor(CORES)));
    thenBillableUsageAggregateHasTotalValue(
        thenBillableUsageAggregateIsFoundForOrg(aggregates, org2),
        Math.ceil(value2 * ROSA.getBillingFactor(CORES)));
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC008")
  void shouldCreateRemittanceAndAggregatePerMetric() {
    // Given: No contract coverage, one org, one billing account, two different metrics
    double valueCores = 4.0; // Cores (billing factor 0.25 -> 1.0)
    double valueInstanceHours = 9.0; // Instance-hours (billing factor 1.0 -> 9.0)
    String billingAccountId = RandomUtils.generateRandom();
    givenNoContractCoverageForProduct(ROSA);

    // When: Tally snapshots are sent for the same org/billing account with different metrics
    whenTallySummariesAreSentForTwoMetrics(billingAccountId, valueCores, valueInstanceHours);

    // Then: Billable usage is produced for each metric with expected value per metric
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA), 2);
    assertEquals(
        2, billableUsages.size(), "Expected exactly 2 billable usage messages (one per metric)");
    thenBillableUsageHasValueForMetric(
        billableUsages, CORES.toString(), valueCores * ROSA.getBillingFactor(CORES));
    thenBillableUsageHasValueForMetric(
        billableUsages, INSTANCE_HOURS.toString(), valueInstanceHours);

    // Then: Remittance table has a record for each (one remittance per tally)
    thenRemittanceExistsForEachBillableUsage(billableUsages);

    // Then: An hourly aggregate message is sent for each metric with correct totalValue
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, 2);
    thenHourlyAggregatePerMetricHasExpectedTotalValues(
        aggregates, valueCores * ROSA.getBillingFactor(CORES), valueInstanceHours);
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC009")
  void shouldCreateRemittanceAndAggregatePerProduct() {
    // Given: No contract coverage for two distinct products, same org and billing account
    double valueRosa = 40.0; // ROSA Cores (billing factor 0.25 -> 10.0)
    double valueRhelAddon = 17.0; // RHEL addon vCPUs (billing factor 1.0 -> 17.0)
    String billingAccountId = RandomUtils.generateRandom();
    Set<String> productIds = Set.of(ROSA.getName(), RHEL_PAYG_ADDON.getName());
    givenNoContractCoverageForProduct(ROSA);
    givenNoContractCoverageForProduct(RHEL_PAYG_ADDON);

    // When: Tally snapshots are sent for the same org/billing account with different products
    whenTallySummariesAreSentForTwoProducts(billingAccountId, valueRosa, valueRhelAddon);

    // Then: Billable usage is produced for each product with expected value per product
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatchesAnyProduct(orgId, productIds), 2);
    assertEquals(
        2, billableUsages.size(), "Expected exactly 2 billable usage messages (one per product)");
    thenBillableUsageHasValueForProduct(
        billableUsages, ROSA.getName(), valueRosa * ROSA.getBillingFactor(CORES));
    thenBillableUsageHasValueForProduct(billableUsages, RHEL_PAYG_ADDON.getName(), valueRhelAddon);

    // Then: Remittance table has a record for each (one remittance per tally)
    thenRemittanceExistsForEachBillableUsage(billableUsages);

    // Then: An hourly aggregate message is sent for each product with correct totalValue
    List<BillableUsageAggregate> aggregates = whenHourlyAggregatesAreReceived(orgId, productIds, 2);
    thenHourlyAggregatePerProductHasExpectedTotalValues(
        aggregates, valueRosa * ROSA.getBillingFactor(CORES), valueRhelAddon);
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC010")
  void shouldCreateRemittanceForTallyUsageDifference() {
    // Given: First remittance exists (value 6) and has succeeded
    double valueExisting = 6.0;
    double valueLargerNew = 10.0;
    double expectedDiff = valueLargerNew - valueExisting;
    String billingAccountId = RandomUtils.generateRandom();
    BillableUsage firstBillableUsage =
        givenFirstRemittanceWithStatus(
            RHEL_PAYG_ADDON,
            RHEL_PAYG_ADDON.getMetric(VCPUS),
            AWS,
            valueExisting,
            billingAccountId,
            BillableUsage.Status.SUCCEEDED,
            null,
            RemittanceStatus.SUCCEEDED);

    // When: Second tally summary is sent with value 10 (greater than first)
    whenSecondTallySummaryIsSent(valueLargerNew, billingAccountId);

    // Then: Second billable usage is produced with the diff value (4)
    List<BillableUsage> secondBillableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, RHEL_PAYG_ADDON, expectedDiff),
            1);
    assertEquals(
        1, secondBillableUsages.size(), "Expected exactly 1 billable usage for second tally");

    // Then: Two remittances exist - first with value 6, second with value 4 (the diff)
    thenRemittanceValueIs(firstBillableUsage, valueExisting);
    thenRemittanceValueIs(secondBillableUsages.getFirst(), expectedDiff);
  }

  @Test
  @TestPlanName("billable-usage-tally-summary-consumer-TC011")
  void shouldSeparateRemittancesByAccumulationMonth() {
    // Given: No contract coverage, one org, one billing account, same product/metric for both
    double value = 5.0;
    String billingAccountId = RandomUtils.generateRandom();
    OffsetDateTime snapshotDateLastMonth = OffsetDateTime.now(ZoneOffset.UTC).minusDays(35);
    OffsetDateTime snapshotDateCurrentMonth = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
    givenNoContractCoverageForProduct(RHEL_PAYG_ADDON);

    // When: Tally snapshots are sent for last month and current month (same account/product/metric)
    whenTallySummariesAreSentWithSnapshotDates(
        billingAccountId, value, snapshotDateLastMonth, snapshotDateCurrentMonth);

    // Then: Both tallies are processed (2 billable usage messages)
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, RHEL_PAYG_ADDON), 2);
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

  private void givenRosaCoresContractCoverage(double coverage) {
    contractsWiremock
        .forContractsAPI()
        .stubContractCoverage(orgId, ROSA, ROSA.getMetric(CORES).getAwsDimension(), coverage);
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

  private TallySummary whenSendTallySummaryForRosaWithCoresValue(double value) {
    TallySummary tallySummary =
        buildTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), value);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private TallySummary whenSendTallySummaryForRhelAddonWithVCPUsValue(double value) {
    TallySummary tallySummary =
        buildTallySummaryWithDefaults(orgId, RHEL_PAYG_ADDON.getName(), VCPUS.toString(), value);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private void whenSecondTallySummaryIsSent(double value, String billingAccountId) {
    TallySummary secondTallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.getValue(),
            value,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, secondTallySummary);
  }

  private void whenTallySummaryIsSentForOrg(String orgId, double value) {
    TallySummary tallySummary =
        buildTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), value);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
  }

  private void whenTallySummariesAreSentForTwoMetrics(
      String billingAccountId, double valueCores, double valueInstanceHours) {
    TallySummary tallySummaryCores =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            valueCores,
            BillingProvider.AWS,
            billingAccountId);
    TallySummary tallySummaryInstanceHours =
        TallySummaryBuilder.buildTallySummary(
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
        TallySummaryBuilder.buildTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.getValue(),
            value,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDateLastMonth);
    TallySummary tallyCurrentMonth =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.getValue(),
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
        TallySummaryBuilder.buildTallySummary(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            valueRosa,
            BillingProvider.AWS,
            billingAccountId);
    TallySummary tallySummaryRhelAddon =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            RHEL_PAYG_ADDON.getName(),
            VCPUS.getValue(),
            valueRhelAddon,
            BillingProvider.AWS,
            billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummaryRosa);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummaryRhelAddon);
  }

  private BillableUsageAggregate thenBillableUsageAggregateIsFound(
      List<BillableUsageAggregate> aggregates, TallySummary tallySummary) {
    String billingAccountId = tallySummary.getTallySnapshots().get(0).getBillingAccountId();
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(a ->
                billingAccountId.equals(
                    a.getAggregateKey() != null ? a.getAggregateKey().getBillingAccountId() : null))
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
    double expectedValue = Math.ceil(value * ROSA.getBillingFactor(CORES));
    assertEquals(
        expectedValue,
        usage.getValue(),
        0.001,
        "Billable usage value for org " + orgId + " should be " + expectedValue);
  }

  private void thenRemittanceExistsForEachBillableUsage(List<BillableUsage> billableUsages) {
    for (BillableUsage usage : billableUsages) {
      thenRemittanceStatusIs(usage, RemittanceStatus.PENDING);
    }
  }

  private BillableUsageAggregate thenBillableUsageAggregateIsFoundForMetric(
      List<BillableUsageAggregate> aggregates, String metricId) {
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(a ->
                a.getAggregateKey() != null && metricId.equals(a.getAggregateKey().getMetricId()))
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
            .filter(a ->
                a.getAggregateKey() != null && productId.equals(a.getAggregateKey().getProductId()))
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

  private void thenEachBillableUsageHasOneRemittanceWithValue(
      List<BillableUsage> billableUsages, double expectedValue) {
    for (BillableUsage usage : billableUsages) {
      var remittance = thenRemittanceStatusIs(usage, RemittanceStatus.PENDING);
      assertNotNull(remittance.getAccumulationPeriod(), "Accumulation period should be set");
      double actualValue =
          remittance.getRemittedPendingValue() != null ? remittance.getRemittedPendingValue() : 0.0;
      assertEquals(expectedValue, actualValue, 0.001, "Remittance value should match tally value");
    }
  }

  private void thenRemittancesSpanExpectedMonths(
      List<BillableUsage> billableUsages,
      String expectedPeriodFirstMonth,
      String expectedPeriodSecondMonth) {
    List<String> periods =
        billableUsages.stream()
            .map(u ->
                service.getRemittancesByTally(u.getTallyId()).getFirst().getAccumulationPeriod())
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
