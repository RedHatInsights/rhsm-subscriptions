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

import static api.BillableUsageTestHelper.createTallySummaryWithDefaults;
import static api.BillableUsageTestHelper.createTallySummaryWithGranularity;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE_STATUS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import domain.RemittanceErrorCode;
import domain.RemittanceStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

  private static final double TOTAL_USAGE = 8.0;
  private static final double BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  private static final String AWS_DIMENSION = ROSA.getMetric(CORES).getAwsDimension();
  private static final double CONTRACT_COVERAGE = 1.0;

  @BeforeAll
  static void subscribeToBillableUsageTopic() {
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE);
  }

  /** Verify tally summary is processed and billable usage is produced with no contract coverage. */
  @Test
  public void testBasicTallyToBillableUsageFlow() {
    // Setup wiremock endpoints
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // Create and send tally summary
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), TOTAL_USAGE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Verify billable usage is produced
    // Expected: 8.0 × 0.25 = 2.0 four_vcpu_hour units
    double expectedValue = TOTAL_USAGE * BILLING_FACTOR;
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
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), TOTAL_USAGE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Verify billable usage accounts for contract coverage
    // Expected: (8.0 × 0.25) - 1.0 = 1.0 four_vcpu_hour units
    double expectedValue = TOTAL_USAGE * BILLING_FACTOR - CONTRACT_COVERAGE;
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE,
        MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
        1);
  }

  /**
   * Verify that when both DAILY and HOURLY tally snapshots are sent, only the HOURLY snapshot
   * produces billable usage and DAILY is filtered out by the consumer.
   */
  @Test
  public void testOnlyHourlyGranularityIsProcessed() {
    // Setup wiremock endpoints
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    UUID dailySnapshotId = UUID.randomUUID();
    UUID hourlySnapshotId = UUID.randomUUID();

    // Create and send tally summary with DAILY granularity
    TallySummary dailyTallySummary =
        createTallySummaryWithGranularity(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            TOTAL_USAGE,
            TallySnapshot.Granularity.DAILY,
            dailySnapshotId);
    kafkaBridge.produceKafkaMessage(TALLY, dailyTallySummary);

    // Create and send tally summary with HOURLY granularity
    TallySummary hourlyTallySummary =
        createTallySummaryWithGranularity(
            orgId,
            ROSA.getName(),
            CORES.toString(),
            TOTAL_USAGE,
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
        createBillableUsageAggregate(
            orgId,
            ROSA.getName(),
            "aws",
            "aws-account-123",
            BillableUsage.Status.SUCCEEDED,
            null,
            expectedBilledOnTime, // Use the captured time
            List.of(billableUsage.getUuid().toString()));

    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_STATUS, statusUpdate);

    // Verify final SUCCEEDED status via API (polling waits for consumer processing)
    waitForRemittanceStatus(tallyId, RemittanceStatus.SUCCEEDED);

    // Verify billed_on field was populated with proper timestamp
    List<TallyRemittance> remittances = billableUsageService.getRemittancesByTallyId(tallyId);
    assertNotNull(remittances, "Remittances should exist");
    assertTrue(!remittances.isEmpty(), "Should have at least one remittance");
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
        createBillableUsageAggregate(
            orgId,
            ROSA.getName(),
            "aws",
            "aws-account-123",
            BillableUsage.Status.FAILED,
            BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND,
            null,
            List.of(billableUsage.getUuid().toString()));

    kafkaBridge.produceKafkaMessage(BILLABLE_USAGE_STATUS, statusUpdate);

    // Verify final FAILED status via API (polling waits for consumer processing)
    waitForRemittanceStatus(tallyId, RemittanceStatus.FAILED);

    // Verify error code was populated properly
    List<TallyRemittance> remittances = billableUsageService.getRemittancesByTallyId(tallyId);
    assertNotNull(remittances, "Remittances should exist");
    assertTrue(!remittances.isEmpty(), "Should have at least one remittance");
    verifyErrorCode(remittances.get(0), RemittanceErrorCode.SUBSCRIPTION_NOT_FOUND.name());
  }

  private BillableUsageAggregate createBillableUsageAggregate(
      String orgId,
      String productId,
      String billingProvider,
      String billingAccountId,
      BillableUsage.Status status,
      BillableUsage.ErrorCode errorCode,
      OffsetDateTime billedOn,
      List<String> remittanceUuids) {

    var aggregateKey = new BillableUsageAggregateKey();
    aggregateKey.setOrgId(orgId);
    aggregateKey.setProductId(productId);
    aggregateKey.setMetricId(CORES.toString());
    aggregateKey.setSla("Premium");
    aggregateKey.setUsage("Production");
    aggregateKey.setBillingProvider(billingProvider);
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
    TallySummary tallySummary =
        createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), TOTAL_USAGE);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Wait for billable usage to be produced
    double expectedValue = TOTAL_USAGE * BILLING_FACTOR;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
            1);

    assertEquals(1, billableUsages.size(), "Expected exactly 1 billable usage message");
    return billableUsages.get(0);
  }

  /** Wait for remittance to reach expected status using API polling */
  private void waitForRemittanceStatus(String tallyId, RemittanceStatus expectedStatus) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<TallyRemittance> remittances =
                  billableUsageService.getRemittancesByTallyId(tallyId);
              assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
              assertTrue(!remittances.isEmpty(), "Should have at least one remittance");

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
}
