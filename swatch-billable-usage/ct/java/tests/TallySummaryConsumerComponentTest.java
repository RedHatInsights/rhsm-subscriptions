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
import com.fasterxml.jackson.databind.JsonNode;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
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
  static void subscribeToTopics() {
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE);
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE_STATUS);
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
    BillableUsage billableUsage = billableUsages.get(0);

    // Verify initial PENDING status via API (remittance created automatically)
    String tallyId = billableUsage.getTallyId().toString();
    waitForRemittanceStatus(tallyId, "PENDING");

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

    // Verify Kafka status message was processed
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(orgId, BillableUsage.Status.SUCCEEDED),
        1);

    // Verify final SUCCEEDED status via API
    waitForRemittanceStatus(tallyId, "SUCCEEDED");

    // Verify billed_on field was populated with proper timestamp
    String remittanceJson = getRemittanceByTallyId(tallyId);
    assertNotNull(remittanceJson, "Remittance should exist");
    verifyBilledOnTimestamp(remittanceJson, expectedBilledOnTime);
  }

  /** Verify that status consumer updates remittance with failed status. */
  @Test
  public void testStatusConsumerUpdatesRemittanceWithFailedStatus() {
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
    BillableUsage billableUsage = billableUsages.get(0);

    // Verify initial PENDING status via API (remittance created automatically)
    String tallyId = billableUsage.getTallyId().toString();
    waitForRemittanceStatus(tallyId, "PENDING");

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

    // Verify Kafka status message was processed
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE_STATUS,
        MessageValidators.aggregateMatches(orgId, BillableUsage.Status.FAILED),
        1);

    // Verify final FAILED status via API
    waitForRemittanceStatus(tallyId, "FAILED");

    // Verify error code was populated properly
    String remittanceJson = getRemittanceByTallyId(tallyId);
    assertNotNull(remittanceJson, "Remittance should exist");
    verifyErrorCode(remittanceJson, "SUBSCRIPTION_NOT_FOUND");
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

  /** Wait for remittance to reach expected status using API polling */
  private void waitForRemittanceStatus(String tallyId, String expectedStatus) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              String remittanceJson = getRemittanceByTallyId(tallyId);
              assertNotNull(remittanceJson, "Remittance should exist for tallyId: " + tallyId);
              // Simple status check by looking for status in JSON response
              // In a real implementation, we'd parse the JSON properly
              assertTrue(
                  remittanceJson.contains("\"status\":\"" + expectedStatus + "\""),
                  "Expected status '"
                      + expectedStatus
                      + "' not found in response: "
                      + remittanceJson);
            });
  }

  /** Get remittance by tally ID using internal API */
  private String getRemittanceByTallyId(String tallyId) {
    try {
      Response response =
          RestAssured.given()
              .baseUri("http://localhost")
              .port(service.getMappedPort(8080))
              .basePath("/api/swatch-billable-usage/internal")
              .when()
              .get("/remittance/accountRemittances/" + tallyId)
              .then()
              .extract()
              .response();

      if (response.statusCode() == 200) {
        return response.asString();
      } else {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  /** Verify that billed_on timestamp is populated and within expected range */
  private void verifyBilledOnTimestamp(String remittanceJson, OffsetDateTime expectedTime) {
    try {
      JsonNode remittanceArray = JsonUtils.getObjectMapper().readTree(remittanceJson);
      assertTrue(
          remittanceArray.isArray() && remittanceArray.size() > 0,
          "Remittance JSON should be a non-empty array");

      JsonNode remittance = remittanceArray.get(0);
      JsonNode billedOnNode = remittance.get("billedOn");

      assertNotNull(billedOnNode, "billedOn field should be present");
      assertTrue(!billedOnNode.isNull(), "billedOn should not be null for SUCCEEDED status");

      String billedOnStr = billedOnNode.asText();
      OffsetDateTime actualBilledOn = OffsetDateTime.parse(billedOnStr);

      // Verify billed_on is within 30 seconds of expected time (allowing for processing delay)
      Duration timeDiff = Duration.between(expectedTime, actualBilledOn).abs();
      assertTrue(
          timeDiff.toSeconds() <= 30,
          String.format(
              "billedOn timestamp %s should be within 30 seconds of expected time %s",
              actualBilledOn, expectedTime));

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse and verify billedOn timestamp", e);
    }
  }

  /** Verify that error code matches expected value */
  private void verifyErrorCode(String remittanceJson, String expectedErrorCode) {
    try {
      JsonNode remittanceArray = JsonUtils.getObjectMapper().readTree(remittanceJson);
      assertTrue(
          remittanceArray.isArray() && remittanceArray.size() > 0,
          "Remittance JSON should be a non-empty array");

      JsonNode remittance = remittanceArray.get(0);
      JsonNode errorCodeNode = remittance.get("errorCode");

      assertNotNull(errorCodeNode, "errorCode field should be present");
      assertTrue(!errorCodeNode.isNull(), "errorCode should not be null for FAILED status");

      String actualErrorCode = errorCodeNode.asText();
      assertEquals(
          expectedErrorCode,
          actualErrorCode,
          String.format(
              "Expected error code '%s' but got '%s'", expectedErrorCode, actualErrorCode));

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse and verify error code", e);
    }
  }
}
