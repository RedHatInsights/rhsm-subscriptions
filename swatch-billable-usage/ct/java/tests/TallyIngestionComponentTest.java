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
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static domain.HardwareMeasurementType.PHYSICAL;
import static domain.HardwareMeasurementType.TOTAL;
import static domain.Product.ROSA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static utils.TallyMeasurementBuilder.buildMeasurement;
import static utils.TallyMeasurementBuilder.buildPhysicalMeasurement;
import static utils.TallySummaryBuilder.buildTallySummaryWithDefaults;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import domain.RemittanceStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.awaitility.core.ConditionTimeoutException;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;
import utils.TallySummaryBuilder;

public class TallyIngestionComponentTest extends BaseBillableUsageComponentTest {

  private static final double VALUE = 8.0;

  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC001")
  void shouldProcessValidHourlyPaygTallySummaryForAzure() {
    // Given: No contract coverage; ROSA tally with Azure billing provider and a known account ID
    String azureAccountId = RandomUtils.generateRandom();
    givenNoContractCoverageForProduct(ROSA);

    // When: One tally snapshot is published with billing_provider=azure
    TallySummary tallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId, ROSA.getName(), CORES.toString(), VALUE, BillingProvider.AZURE, azureAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Billable usage is emitted with billing_provider=azure and the correct billing account
    double expectedValue = VALUE * ROSA.getBillingFactor(CORES);
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedValue),
            1);

    BillableUsage usage = billableUsages.getFirst();
    assertEquals(
        BillableUsage.BillingProvider.AZURE,
        usage.getBillingProvider(),
        "Billable usage should have AZURE billing provider");
    assertEquals(azureAccountId, usage.getBillingAccountId(), "Billing account ID should match");

    // Then: A remittance record is created with PENDING status
    thenRemittanceStatusIs(usage, RemittanceStatus.PENDING);
  }

  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC002")
  void shouldIgnoreTotalHardwareMeasurementDuplicates() {
    // Given: No contract coverage; a snapshot with both PHYSICAL and TOTAL measurements for CORES
    givenNoContractCoverageForProduct(ROSA);
    String billingAccountId = RandomUtils.generateRandom();

    double physicalValue = 8.0;
    double totalValue = 12.0; // Different value to distinguish which was processed

    TallySummary tallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            ROSA.getName(),
            BillingProvider.AWS,
            billingAccountId,
            buildMeasurement(CORES.toString(), PHYSICAL, physicalValue, physicalValue),
            buildMeasurement(CORES.toString(), TOTAL, totalValue, totalValue));

    // When: The snapshot is published to the TALLY topic
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Only the PHYSICAL measurement produces billable usage; TOTAL is filtered out
    double billingFactor = ROSA.getBillingFactor(CORES);
    double expectedPhysicalValue = physicalValue * billingFactor;
    double expectedTotalValue = totalValue * billingFactor;
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedPhysicalValue),
            1);

    // Verify only PHYSICAL measurement produced billable usage; TOTAL was filtered
    assertEquals(
        1,
        billableUsages.size(),
        "Expected exactly one billable usage (TOTAL measurement should be filtered)");
    assertEquals(
        expectedPhysicalValue,
        billableUsages.getFirst().getValue(),
        0.001,
        "Billable usage value should match PHYSICAL measurement, not TOTAL");

    // Confirm no message with TOTAL's value arrives within a short bounded window
    assertThrows(
        ConditionTimeoutException.class,
        () ->
            kafkaBridge.waitForKafkaMessage(
                BILLABLE_USAGE,
                MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedTotalValue),
                1,
                AwaitilitySettings.usingTimeout(Duration.ofSeconds(3))),
        "TOTAL measurement should not produce a billable usage message");
  }

  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC003")
  void shouldMapOneTallySummaryToMultipleBillableUsages() {
    // Given: No contract coverage; a single snapshot with two PHYSICAL measurements (Cores and
    // Instance-hours), each with its own billing factor
    givenNoContractCoverageForProduct(ROSA);
    String billingAccountId = RandomUtils.generateRandom();

    double coresValue = 4.0;
    double instanceHoursValue = 9.0;

    TallySummary tallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            ROSA.getName(),
            BillingProvider.AWS,
            billingAccountId,
            buildPhysicalMeasurement(CORES.toString(), coresValue),
            buildPhysicalMeasurement(INSTANCE_HOURS.toString(), instanceHoursValue));

    // When: The snapshot is published to the TALLY topic
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);

    // Then: Two billable usage messages are emitted — one per metric — each scaled by its factor
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, ROSA), 2);

    assertEquals(2, billableUsages.size(), "Expected exactly two billable usage messages");

    BillableUsage coresUsage =
        billableUsages.stream()
            .filter(u -> CORES.toString().equals(u.getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(coresUsage, "Should have billable usage for Cores metric");
    assertEquals(
        coresValue * ROSA.getBillingFactor(CORES),
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
        instanceHoursValue * ROSA.getBillingFactor(INSTANCE_HOURS),
        instanceHoursUsage.getValue(),
        0.001,
        "Instance-hours billable usage should match value with billing factor");

    // Then: Each metric produces an independent remittance record (shared tally ID from snapshot)
    UUID tallyId = coresUsage.getTallyId();
    var coresRemittance = thenRemittanceForMetricStatusIs(tallyId, CORES, RemittanceStatus.PENDING);
    var instanceHoursRemittance =
        thenRemittanceForMetricStatusIs(tallyId, INSTANCE_HOURS, RemittanceStatus.PENDING);

    // Collect remittances by metric ID — toMap enforces uniqueness (one per metric)
    Map<String, Double> remittedValueByMetric =
        service.getRemittancesByTally(tallyId).stream()
            .filter(r -> r.getRemittedPendingValue() != null)
            .collect(
                Collectors.toMap(
                    TallyRemittance::getMetricId, TallyRemittance::getRemittedPendingValue));

    assertEquals(coresValue, coresRemittance.getRemittedPendingValue());
    assertEquals(instanceHoursValue, instanceHoursRemittance.getRemittedPendingValue());
  }

  @Test
  @TestPlanName("billable-usage-tally-ingestion-TC004")
  void shouldRejectInvalidSnapshotDateWithoutServiceCrash() {
    // Given: No contract coverage; a raw JSON payload with snapshot_date="testerday" (unparseable)
    givenNoContractCoverageForProduct(ROSA);
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
                            PHYSICAL.name(),
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
          buildTallySummaryWithDefaults(orgId, ROSA.getName(), CORES.toString(), VALUE);
      kafkaBridge.produceKafkaMessage(TALLY, validTally);
    }

    // Then: Both valid tallies produce billable usage; the bad message is skipped without crashing
    double expectedValue = VALUE * ROSA.getBillingFactor(CORES);
    List<BillableUsage> billableUsages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE,
            MessageValidators.billableUsageMatchesWithValue(orgId, ROSA, expectedValue),
            2);

    assertEquals(
        2,
        billableUsages.size(),
        "Expected exactly two billable usage messages from valid tallies");

    for (BillableUsage usage : billableUsages) {
      thenRemittanceStatusIs(usage, RemittanceStatus.PENDING);
    }
  }
}
