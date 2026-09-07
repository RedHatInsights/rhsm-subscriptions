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

import static api.BillableUsageTestHelper.createPhysicalMeasurement;
import static api.BillableUsageTestHelper.createTallySummaryWithMeasurements;
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Product;
import domain.RemittanceStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.Test;

public class AcmMetricSplitComponentTest extends BaseBillableUsageComponentTest {

  private static final Product RHACM = Product.RHACM;
  private static final MetricId VCPUS_SELF_MANAGED = MetricIdUtils.getVCpusSelfManaged();
  private static final double ACM_BILLING_FACTOR = 1.0;
  private static final String ACM_VCPU_HOURS = RHACM.getMetric(VCPUS).getAwsDimension();
  private static final String ACM_VCPU_HOURS_SLFMNG =
      RHACM.getMetric(VCPUS_SELF_MANAGED).getAwsDimension();
  private static final String ACM_VCPU_HOURS_MNG = RHACM.getMetric(VCPUS).getAzureDimension();

  private static String tallyId(TallySummary tallySummary) {
    return tallySummary.getTallySnapshots().getFirst().getId().toString();
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC001")
  void shouldCoverManagedOnlyForLegacyAwsContract() {
    double managedCoverage = 10.0;
    double managedTotal = 15.0;
    double selfManagedTotal = 8.0;
    double expectedManagedRemittance = managedTotal - managedCoverage;
    double expectedSelfManagedRemittance = selfManagedTotal;

    // Given: Legacy AWS contract covering managed dimension only
    givenLegacyAwsAcmContract(managedCoverage);

    // When: Tally with managed overage and full self-managed usage
    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AWS, managedTotal, selfManagedTotal);

    // Then: Managed and self-managed bill independently from contract coverage
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), expectedManagedRemittance, BillableUsage.BillingProvider.AWS);
    thenUsageHasValueFactorAndProvider(
        usages,
        VCPUS_SELF_MANAGED.toString(),
        expectedSelfManagedRemittance,
        BillableUsage.BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(
            VCPUS.toString(),
            expectedManagedRemittance,
            VCPUS_SELF_MANAGED.toString(),
            expectedSelfManagedRemittance),
        BillingProvider.AWS);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC002")
  void shouldApplyAwsCoveragePerAcmDimension() {
    double managedCoverage = 10.0;
    double selfManagedCoverage = 20.0;
    double managedTotal = 12.0;
    double selfManagedTotal = 15.0;
    double expectedManagedRemittance = managedTotal - managedCoverage;
    double expectedSelfManagedRemittance = Math.max(0.0, selfManagedTotal - selfManagedCoverage);

    // Given: AWS contract with independent coverage per ACM dimension
    givenDualDimensionAwsAcmContract(managedCoverage, selfManagedCoverage);

    // When: Tally with managed overage and self-managed within contract
    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AWS, managedTotal, selfManagedTotal);

    // Then: Managed bills overage only; self-managed fully covered
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), expectedManagedRemittance, BillableUsage.BillingProvider.AWS);
    thenUsageHasValueFactorAndProvider(
        usages,
        VCPUS_SELF_MANAGED.toString(),
        expectedSelfManagedRemittance,
        BillableUsage.BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(VCPUS.toString(), expectedManagedRemittance),
        BillingProvider.AWS);
    thenAccountRemittanceEquals(
        VCPUS_SELF_MANAGED.toString(), BillingProvider.AWS, expectedSelfManagedRemittance);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC003")
  void shouldUseAzureDimensionForAcmCoverage() {
    double managedCoverage = 16.0;
    double managedTotal = 20.0;
    double selfManagedTotal = 9.0;
    double expectedManagedRemittance = managedTotal - managedCoverage;
    double expectedSelfManagedRemittance = selfManagedTotal;

    // Given: Azure contract covering managed dimension only
    givenAzureManagedOnlyAcmContract(managedCoverage);

    // When: Tally with managed overage and full self-managed usage
    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(
            BillingProvider.AZURE, managedTotal, selfManagedTotal);

    // Then: Azure dimensions map to correct contract metrics for coverage
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), expectedManagedRemittance, BillableUsage.BillingProvider.AZURE);
    thenUsageHasValueFactorAndProvider(
        usages,
        VCPUS_SELF_MANAGED.toString(),
        expectedSelfManagedRemittance,
        BillableUsage.BillingProvider.AZURE);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(
            VCPUS.toString(),
            expectedManagedRemittance,
            VCPUS_SELF_MANAGED.toString(),
            expectedSelfManagedRemittance),
        BillingProvider.AZURE);
  }

  private void givenLegacyAwsAcmContract(double managedCoverage) {
    contractsWiremock.setupContractCoverage(
        orgId, RHACM.getName(), ACM_VCPU_HOURS, managedCoverage);
  }

  private void givenDualDimensionAwsAcmContract(
      double managedCoverage, double selfManagedCoverage) {
    contractsWiremock.setupMultiMetricContractCoverage(
        orgId,
        RHACM.getName(),
        Map.of(ACM_VCPU_HOURS, managedCoverage, ACM_VCPU_HOURS_SLFMNG, selfManagedCoverage));
  }

  private void givenAzureManagedOnlyAcmContract(double managedCoverage) {
    contractsWiremock.setupContractCoverage(
        orgId, RHACM.getName(), ACM_VCPU_HOURS_MNG, managedCoverage);
  }

  private TallySummary whenAcmTallyWithBothMetricsIsPublished(
      BillingProvider billingProvider, double managedTotal, double selfManagedTotal) {
    TallySummary tallySummary =
        createTallySummaryWithMeasurements(
            orgId,
            RHACM.getName(),
            billingProvider,
            billingAccountId,
            createPhysicalMeasurement(VCPUS.toString(), managedTotal),
            createPhysicalMeasurement(VCPUS_SELF_MANAGED.toString(), selfManagedTotal));
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private List<BillableUsage> thenTwoAcmUsagesReceived() {
    List<BillableUsage> usages =
        kafkaBridge.waitForKafkaMessage(
            BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, RHACM.getName()), 2);
    assertEquals(2, usages.size(), "Expected two billable-usage messages for rhacm");
    return usages;
  }

  private void thenUsageHasValueFactorAndProvider(
      List<BillableUsage> usages,
      String metricId,
      double expectedValue,
      BillableUsage.BillingProvider provider) {
    BillableUsage usage =
        usages.stream().filter(u -> metricId.equals(u.getMetricId())).findFirst().orElse(null);
    assertNotNull(usage, "Expected billable usage for metric " + metricId);
    assertEquals(
        expectedValue, usage.getValue(), 0.001, "Billable usage value for metric " + metricId);
    assertEquals(
        ACM_BILLING_FACTOR,
        usage.getBillingFactor(),
        0.001,
        "Billing factor for metric " + metricId);
    assertEquals(provider, usage.getBillingProvider(), "Billing provider for metric " + metricId);
  }

  private void thenTallyRemittancesMatch(
      String tallyId, Map<String, Double> expectedByMetric, BillingProvider billingProvider) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
          assertNotNull(remittances, "Remittances should exist for tally");
          assertEquals(
              expectedByMetric.size(), remittances.size(), "Tally remittance row count mismatch");
          Map<String, Double> actual =
              remittances.stream()
                  .collect(
                      Collectors.toMap(
                          TallyRemittance::getMetricId, TallyRemittance::getRemittedPendingValue));
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
      String metricId, BillingProvider billingProvider, double expectedRemittedValue) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<MonthlyRemittance> remittances =
              service.getRemittances(
                  RHACM.getName(),
                  orgId,
                  metricId,
                  billingProvider.toTallyApiModel().value(),
                  billingAccountId);
          assertFalse(remittances.isEmpty(), "Expected account remittance");
          assertEquals(
              expectedRemittedValue,
              remittances.get(0).getRemittedValue(),
              0.001,
              "Account remittance value mismatch");
        });
  }
}
