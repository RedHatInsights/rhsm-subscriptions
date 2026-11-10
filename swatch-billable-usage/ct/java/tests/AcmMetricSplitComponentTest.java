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
import static domain.BillingProvider.AWS;
import static domain.BillingProvider.AZURE;
import static domain.Product.RHACM;
import static domain.RemittanceStatus.COVERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static utils.TallyMeasurementBuilder.buildPhysicalMeasurement;
import static utils.TallySummaryBuilder.buildTallySummary;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.BillingProvider;
import domain.RemittanceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;

public class AcmMetricSplitComponentTest extends BaseBillableUsageComponentTest {

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC001")
  void shouldCoverManagedOnlyForLegacyAwsContract() {
    double managedCoverage = 10.0;
    double managedTotal = 15.0;
    double selfManagedTotal = 8.0;

    // Given: Legacy AWS contract covering managed dimension only
    givenAcmContractCoverageForAwsWithVCPUsOnly(managedCoverage);

    // When: Tally with managed overage and full self-managed usage
    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(AWS, managedTotal, selfManagedTotal);

    // Then: Managed and self-managed bill independently of contract coverage
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(usages, VCPUS, managedTotal - managedCoverage, AWS);
    thenUsageHasValueFactorAndProvider(usages, VCPUS_SELF_MANAGED, selfManagedTotal, AWS);
    thenTallyRemittancesMatch(
        tallySummary,
        AWS,
        Map.of(VCPUS, managedTotal - managedCoverage, VCPUS_SELF_MANAGED, selfManagedTotal));
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC002")
  void shouldApplyAwsCoveragePerAcmDimension() {
    double managedCoverage = 10.0;
    double selfManagedCoverage = 20.0;
    double managedTotal = 12.0;
    double selfManagedTotal = 15.0;

    // Given: AWS contract with independent coverage per ACM dimension
    givenAcmContractCoverageForAws(managedCoverage, selfManagedCoverage);

    // When: Tally with managed overage and self-managed within contract
    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(AWS, managedTotal, selfManagedTotal);

    // Then: Managed bills overage only; self-managed fully covered
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(usages, VCPUS, managedTotal - managedCoverage, AWS);
    thenUsageHasValueFactorAndProvider(usages, VCPUS_SELF_MANAGED, 0.0, AWS);
    thenTallyRemittancesMatch(tallySummary, AWS, Map.of(VCPUS, managedTotal - managedCoverage));
    thenAccountRemittanceEquals(VCPUS_SELF_MANAGED, AWS, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC003")
  void shouldUseAzureDimensionForAcmCoverage() {
    double managedCoverage = 16.0;
    double managedTotal = 20.0;
    double selfManagedTotal = 9.0;

    // Given: Azure contract covering managed dimension only
    givenAcmContractCoverageForAzureWithVCPUsOnly(managedCoverage);

    // When: Tally with managed overage and full self-managed usage
    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(AZURE, managedTotal, selfManagedTotal);

    // Then: Azure dimensions map to correct contract metrics for coverage
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(usages, VCPUS, managedTotal - managedCoverage, AZURE);
    thenUsageHasValueFactorAndProvider(usages, VCPUS_SELF_MANAGED, selfManagedTotal, AZURE);
    thenTallyRemittancesMatch(
        tallySummary,
        AZURE,
        Map.of(VCPUS, managedTotal - managedCoverage, VCPUS_SELF_MANAGED, selfManagedTotal));
  }

  private void givenAcmContractCoverageForAwsWithVCPUsOnly(double managedCoverage) {
    contractsWiremock
        .forContractsAPI()
        .stubContractCoverage(
            orgId, RHACM, RHACM.getMetric(VCPUS).getAwsDimension(), managedCoverage);
  }

  private void givenAcmContractCoverageForAzureWithVCPUsOnly(double managedCoverage) {
    contractsWiremock
        .forContractsAPI()
        .stubContractCoverage(
            orgId, RHACM, RHACM.getMetric(VCPUS).getAzureDimension(), managedCoverage);
  }

  private void givenAcmContractCoverageForAws(double managedCoverage, double selfManagedCoverage) {
    contractsWiremock
        .forContractsAPI()
        .stubMultiMetricContractCoverage(
            orgId,
            RHACM,
            Map.of(
                RHACM.getMetric(VCPUS).getAwsDimension(),
                managedCoverage,
                RHACM.getMetric(VCPUS_SELF_MANAGED).getAwsDimension(),
                selfManagedCoverage));
  }

  private TallySummary whenAcmTallyWithBothMetricsIsPublished(
      BillingProvider billingProvider, double managedTotal, double selfManagedTotal) {
    TallySummary tallySummary =
        buildTallySummary(
            orgId,
            RHACM.getName(),
            billingProvider,
            billingAccountId,
            buildPhysicalMeasurement(VCPUS.toString(), managedTotal),
            buildPhysicalMeasurement(VCPUS_SELF_MANAGED.toString(), selfManagedTotal));
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private List<BillableUsage> thenTwoAcmUsagesReceived() {
    List<BillableUsage> usages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, RHACM), 2);
    assertEquals(2, usages.size(), "Expected two billable-usage messages for rhacm");
    return usages;
  }

  private void thenUsageHasValueFactorAndProvider(
      List<BillableUsage> usages,
      MetricId metricId,
      double expectedValue,
      BillingProvider provider) {
    BillableUsage usage =
        usages.stream()
            .filter(u -> metricId.toString().equals(u.getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(usage, "Expected billable usage for metric " + metricId);
    assertEquals(
        expectedValue, usage.getValue(), 0.001, "Billable usage value for metric " + metricId);
    assertEquals(
        RHACM.getBillingFactor(metricId),
        usage.getBillingFactor(),
        0.001,
        "Billing factor for metric " + metricId);
    assertEquals(
        provider.toBillableUsageApiModel(),
        usage.getBillingProvider(),
        "Billing provider for metric " + metricId);
  }

  private void thenTallyRemittancesMatch(
      TallySummary tallySummary,
      BillingProvider billingProvider,
      Map<MetricId, Double> expectedByMetric) {
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();
    AwaitilityUtils.untilAsserted(() -> {
      List<TallyRemittance> remittances =
          service.getRemittancesByTally(tallyId).stream()
              .filter(row -> !COVERED.name().equalsIgnoreCase(row.getStatus()))
              .toList();
      assertNotNull(remittances, "Remittances should exist for tally");
      assertEquals(
          expectedByMetric.size(), remittances.size(), "Tally remittance row count mismatch");
      Map<MetricId, Double> actual =
          remittances.stream()
              .collect(
                  Collectors.toMap(
                      r -> MetricId.fromString(r.getMetricId()),
                      TallyRemittance::getRemittedPendingValue));
      assertEquals(expectedByMetric, actual, "Tally remittances mismatch");
      String expectedProvider = billingProvider.toTallyApiModel().value();
      for (TallyRemittance remittance : remittances) {
        assertEquals(
            RemittanceStatus.PENDING.name(),
            remittance.getStatus(),
            "Remittance status for metric " + remittance.getMetricId());
        assertEquals(
            expectedProvider,
            remittance.getBillingProvider(),
            "Remittance billing provider for metric " + remittance.getMetricId());
      }
    });
  }

  private void thenAccountRemittanceEquals(
      MetricId metricId, BillingProvider billingProvider, double expectedRemittedValue) {
    AwaitilityUtils.untilAsserted(() -> {
      double actual =
          service
              .getRemittances(
                  orgId,
                  RHACM,
                  metricId,
                  billingProvider.toTallyApiModel().value(),
                  billingAccountId)
              .stream()
              .filter(row -> !COVERED.name().equalsIgnoreCase(row.getRemittanceStatus()))
              .mapToDouble(MonthlyRemittance::getRemittedValue)
              .sum();
      assertEquals(expectedRemittedValue, actual, 0.001, "Account remittance value mismatch");
    });
  }
}
