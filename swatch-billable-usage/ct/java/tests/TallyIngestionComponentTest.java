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
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.core.ConditionTimeoutException;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallyMeasurement;
import org.candlepin.subscriptions.billable.usage.TallySnapshot;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;

public class TallyIngestionComponentTest extends BaseBillableUsageComponentTest {

  private static final double VALUE = 8.0;
  private static final double BILLING_FACTOR = ROSA.getBillingFactor(CORES);
  private static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();
  // Instance-hours has no billingFactor defined in rosa.yaml, defaults to 1.0
  private static final double INSTANCE_HOURS_BILLING_FACTOR = 1.0;

  /**
   * Verify Azure billing provider tally snapshots are processed identically to AWS: billable usage
   * is emitted with the correct provider and a remittance record is created.
   */
  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC001")
  public void testProcessValidHourlyPaygTallySummaryForAzure() {
    // Given: No contract coverage; ROSA tally with Azure billing provider and a known account ID
    String azureAccountId = RandomUtils.generateRandom();
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());

    // When: One tally snapshot is published with billing_provider=azure
    TallySummary tallySummary =
        createTallySummary(
            orgId, ROSA.getName(), CORES.toString(), VALUE, BillingProvider.AZURE, azureAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Billable usage is emitted with billing_provider=azure and the correct billing account
    double expectedValue = VALUE * BILLING_FACTOR;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
            1);

    BillableUsage usage = billableUsages.get(0);
    assertEquals(
        BillableUsage.BillingProvider.AZURE,
        usage.getBillingProvider(),
        "Billable usage should have AZURE billing provider");
    assertEquals(azureAccountId, usage.getBillingAccountId(), "Billing account ID should match");

    // Then: A remittance record is created with PENDING status
    waitForRemittanceStatus(usage.getTallyId().toString(), RemittanceStatus.PENDING);
  }

  /**
   * Verify TOTAL hardware measurement types are filtered to prevent duplicate billing: only the
   * non-TOTAL (PHYSICAL) measurement produces a billable usage record.
   */
  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC002")
  public void testIgnoreTotalHardwareMeasurementDuplicates() {
    // Given: No contract coverage; a snapshot with both PHYSICAL and TOTAL measurements for CORES
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
    String billingAccountId = RandomUtils.generateRandom();

    double physicalValue = 8.0;
    double totalValue = 12.0; // Different value to distinguish which was processed

    var physicalMeasurement = new TallyMeasurement();
    physicalMeasurement.setHardwareMeasurementType("PHYSICAL");
    physicalMeasurement.setMetricId(CORES.toString());
    physicalMeasurement.setValue(physicalValue);
    physicalMeasurement.setCurrentTotal(physicalValue);

    var totalMeasurement = new TallyMeasurement();
    totalMeasurement.setHardwareMeasurementType("TOTAL");
    totalMeasurement.setMetricId(CORES.toString());
    totalMeasurement.setValue(totalValue);
    totalMeasurement.setCurrentTotal(totalValue);

    var snapshot = new TallySnapshot();
    snapshot.setId(UUID.randomUUID());
    snapshot.setProductId(ROSA.getName());
    snapshot.setBillingProvider(BillingProvider.AWS.toTallyApiModel());
    snapshot.setBillingAccountId(billingAccountId);
    snapshot.setSnapshotDate(
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC));
    snapshot.setSla(TallySnapshot.Sla.PREMIUM);
    snapshot.setUsage(TallySnapshot.Usage.PRODUCTION);
    snapshot.setGranularity(TallySnapshot.Granularity.HOURLY);
    snapshot.setTallyMeasurements(List.of(physicalMeasurement, totalMeasurement));

    var tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);
    tallySummary.setTallySnapshots(List.of(snapshot));

    // When: The snapshot is published to the TALLY topic
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Only the PHYSICAL measurement produces billable usage; TOTAL is filtered out
    double expectedPhysicalValue = physicalValue * BILLING_FACTOR;
    double expectedTotalValue = totalValue * BILLING_FACTOR;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(
                orgId, ROSA.getName(), expectedPhysicalValue),
            1);

    // Verify only PHYSICAL measurement produced billable usage; TOTAL was filtered
    assertEquals(
        1,
        billableUsages.size(),
        "Expected exactly one billable usage (TOTAL measurement should be filtered)");
    assertEquals(
        expectedPhysicalValue,
        billableUsages.get(0).getValue(),
        0.001,
        "Billable usage value should match PHYSICAL measurement, not TOTAL");

    // Confirm no message with TOTAL's value arrives within a short bounded window
    assertThrows(
        ConditionTimeoutException.class,
        () ->
            kafkaBridge.waitForKafkaMessage(
                BILLABLE_USAGE,
                MessageValidators.billableUsageMatchesWithValue(
                    orgId, ROSA.getName(), expectedTotalValue),
                1,
                AwaitilitySettings.usingTimeout(Duration.ofSeconds(3))),
        "TOTAL measurement should not produce a billable usage message");
  }

  /**
   * Verify a single tally summary with multiple metrics produces multiple billable usage records,
   * each billed independently with the correct billing factor.
   */
  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC003")
  public void testMapOneTallySummaryToMultipleBillableUsages() {
    // Given: No contract coverage; a single snapshot with two PHYSICAL measurements (Cores and
    // Instance-hours), each with its own billing factor
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
    String billingAccountId = RandomUtils.generateRandom();

    double coresValue = 4.0;
    double instanceHoursValue = 9.0;

    var coresMeasurement = new TallyMeasurement();
    coresMeasurement.setHardwareMeasurementType("PHYSICAL");
    coresMeasurement.setMetricId(CORES.toString());
    coresMeasurement.setValue(coresValue);
    coresMeasurement.setCurrentTotal(coresValue);

    var instanceHoursMeasurement = new TallyMeasurement();
    instanceHoursMeasurement.setHardwareMeasurementType("PHYSICAL");
    instanceHoursMeasurement.setMetricId(INSTANCE_HOURS.toString());
    instanceHoursMeasurement.setValue(instanceHoursValue);
    instanceHoursMeasurement.setCurrentTotal(instanceHoursValue);

    var snapshot = new TallySnapshot();
    snapshot.setId(UUID.randomUUID());
    snapshot.setProductId(ROSA.getName());
    snapshot.setBillingProvider(BillingProvider.AWS.toTallyApiModel());
    snapshot.setBillingAccountId(billingAccountId);
    snapshot.setSnapshotDate(
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC));
    snapshot.setSla(TallySnapshot.Sla.PREMIUM);
    snapshot.setUsage(TallySnapshot.Usage.PRODUCTION);
    snapshot.setGranularity(TallySnapshot.Granularity.HOURLY);
    snapshot.setTallyMeasurements(List.of(coresMeasurement, instanceHoursMeasurement));

    var tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);
    tallySummary.setTallySnapshots(List.of(snapshot));

    // When: The snapshot is published to the TALLY topic
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Two billable usage messages are emitted — one per metric — each scaled by its factor
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA.getName()), 2);

    assertEquals(2, billableUsages.size(), "Expected exactly two billable usage messages");

    BillableUsage coresUsage =
        billableUsages.stream()
            .filter(u -> CORES.toString().equals(u.getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(coresUsage, "Should have billable usage for Cores metric");
    assertEquals(
        coresValue * BILLING_FACTOR,
        coresUsage.getValue(),
        0.001,
        "Cores billable usage should match value with billing factor");

    BillableUsage instanceHoursUsage =
        billableUsages.stream()
            .filter(u -> INSTANCE_HOURS.toString().equals(u.getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(instanceHoursUsage, "Should have billable usage for Instance-hours metric");
    assertEquals(
        instanceHoursValue * INSTANCE_HOURS_BILLING_FACTOR,
        instanceHoursUsage.getValue(),
        0.001,
        "Instance-hours billable usage should match value with billing factor");

    // Then: Each metric produces an independent remittance record (shared tally ID from snapshot)
    String tallyId = coresUsage.getTallyId().toString();
    waitForRemittanceStatus(tallyId, RemittanceStatus.PENDING);

    // Collect remittances by metric ID — toMap enforces uniqueness (one per metric)
    Map<String, Double> remittedValueByMetric =
        service.getRemittancesByTallyId(tallyId).stream()
            .collect(
                Collectors.toMap(
                    TallyRemittance::getMetricId, TallyRemittance::getRemittedPendingValue));

    assertEquals(
        Map.of(CORES.toString(), coresValue, INSTANCE_HOURS.toString(), instanceHoursValue),
        remittedValueByMetric,
        "Should have one remittance per metric with the correct usage value");
  }

  /**
   * Verify malformed {@code snapshot_date} values do not crash the consumer and that subsequent
   * valid messages are still processed normally.
   */
  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC004")
  public void testRejectInvalidSnapshotDateWithoutServiceCrash() {
    // Given: No contract coverage; a raw JSON payload with snapshot_date="testerday" (unparseable)
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
    String billingAccountId = RandomUtils.generateRandom();

    // Raw JSON payload bypasses the typed API to guarantee the bad string reaches the consumer
    // and triggers a deserialization error there
    var rawSummary =
        Map.of(
            "org_id",
            orgId,
            "tally_snapshots",
            List.of(
                Map.of(
                    "id",
                    UUID.randomUUID().toString(),
                    "product_id",
                    ROSA.getName(),
                    "billing_provider",
                    "aws",
                    "billing_account_id",
                    billingAccountId,
                    "snapshot_date",
                    "testerday",
                    "sla",
                    "Premium",
                    "usage",
                    "Production",
                    "granularity",
                    "Hourly",
                    "tally_measurements",
                    List.of(
                        Map.of(
                            "hardware_measurement_type",
                            "PHYSICAL",
                            "metric_id",
                            CORES.toString(),
                            "value",
                            VALUE,
                            "currentTotal",
                            VALUE)))));

    // When: The malformed message is published, followed by two valid tally summaries
    kafkaBridge.produceKafkaMessage(TALLY, rawSummary);

    // Two valid messages must be processed normally after the bad one
    for (int i = 0; i < 2; i++) {
      TallySummary validTally =
          createTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), VALUE);
      kafkaBridge.produceKafkaMessage(TALLY, validTally);
    }

    // Then: Both valid tallies produce billable usage; the bad message is skipped without crashing
    double expectedValue = VALUE * BILLING_FACTOR;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA.getName(), expectedValue),
            2);

    assertEquals(
        2,
        billableUsages.size(),
        "Expected exactly two billable usage messages from valid tallies");

    for (BillableUsage usage : billableUsages) {
      waitForRemittanceStatus(usage.getTallyId().toString(), RemittanceStatus.PENDING);
    }
  }
}
