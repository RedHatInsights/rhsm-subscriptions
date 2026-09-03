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
import static api.BillableUsageTestHelper.createTallySummary;
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
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Product;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
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

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC001")
  void shouldMapAcmTallyToTwoIndependentAwsUsages() {
    givenAcmContractWithEmptyMetrics();

    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AWS, 8.0, 12.0);

    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), 8.0, BillableUsage.BillingProvider.AWS);
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS_SELF_MANAGED.toString(), 12.0, BillableUsage.BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(VCPUS.toString(), 8.0, VCPUS_SELF_MANAGED.toString(), 12.0),
        BillingProvider.AWS);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC002")
  void shouldCoverManagedOnlyForLegacyAwsContract() {
    givenLegacyAwsAcmContract(10.0);

    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AWS, 15.0, 8.0);

    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), 5.0, BillableUsage.BillingProvider.AWS);
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS_SELF_MANAGED.toString(), 8.0, BillableUsage.BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(VCPUS.toString(), 5.0, VCPUS_SELF_MANAGED.toString(), 8.0),
        BillingProvider.AWS);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC003")
  void shouldApplyAwsCoveragePerAcmDimension() {
    givenDualDimensionAwsAcmContract(10.0, 20.0);

    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AWS, 12.0, 15.0);

    // Wait for both measurements to finish. Do not assert the self-managed
    // Kafka value: a zero-value BillableUsage may still be emitted.
    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), 2.0, BillableUsage.BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(tallySummary), Map.of(VCPUS.toString(), 2.0), BillingProvider.AWS);
    thenAccountRemittanceEquals(VCPUS_SELF_MANAGED.toString(), BillingProvider.AWS, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC004")
  void shouldUseAzureDimensionForAcmCoverage() {
    givenAzureManagedOnlyAcmContract(16.0);

    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AZURE, 20.0, 9.0);

    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), 4.0, BillableUsage.BillingProvider.AZURE);
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS_SELF_MANAGED.toString(), 9.0, BillableUsage.BillingProvider.AZURE);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(VCPUS.toString(), 4.0, VCPUS_SELF_MANAGED.toString(), 9.0),
        BillingProvider.AZURE);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC005")
  void shouldKeepAcmRemittanceLedgersSeparate() {
    givenAcmContractWithEmptyMetrics();
    TallySummary managedTally = givenAcmManagedRemittance(10.0);

    TallySummary selfManagedTally =
        whenAcmSingleMetricTallyIsPublished(
            VCPUS_SELF_MANAGED.toString(), 6.0, BillingProvider.AWS);

    thenTallyRemittancesMatch(
        tallyId(managedTally), Map.of(VCPUS.toString(), 10.0), BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(selfManagedTally), Map.of(VCPUS_SELF_MANAGED.toString(), 6.0), BillingProvider.AWS);
    thenAccountRemittanceEquals(VCPUS.toString(), BillingProvider.AWS, 10.0);
    thenAccountRemittanceEquals(VCPUS_SELF_MANAGED.toString(), BillingProvider.AWS, 6.0);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC006")
  void shouldEmitHourlyAggregatePerAcmMetric() {
    givenAcmContractWithEmptyMetrics();
    OffsetDateTime snapshotDate =
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC);

    List<BillableUsageAggregate> aggregates =
        whenAcmHourlyAggregatesAreFlushed(snapshotDate, 8.0, 12.0);

    thenHourlyAggregatePerAcmMetric(aggregates, 8.0, 12.0);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC007")
  void shouldSkipBothAcmMetricsWhenNoContract() {
    givenNoAcmContract();

    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AWS, 8.0, 12.0);

    thenNoBillableUsageKafkaMessage();
    thenNoTallyRemittanceRows(tallyId(tallySummary));
    thenAccountRemittanceEquals(VCPUS.toString(), BillingProvider.AWS, 0.0);
    thenAccountRemittanceEquals(VCPUS_SELF_MANAGED.toString(), BillingProvider.AWS, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-acm-metric-split-TC008")
  void shouldRemitBothAcmDimensionsForAzurePayg() {
    givenAcmContractWithEmptyMetrics();

    TallySummary tallySummary =
        whenAcmTallyWithBothMetricsIsPublished(BillingProvider.AZURE, 16.0, 20.0);

    List<BillableUsage> usages = thenTwoAcmUsagesReceived();
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS.toString(), 16.0, BillableUsage.BillingProvider.AZURE);
    thenUsageHasValueFactorAndProvider(
        usages, VCPUS_SELF_MANAGED.toString(), 20.0, BillableUsage.BillingProvider.AZURE);
    thenTallyRemittancesMatch(
        tallyId(tallySummary),
        Map.of(VCPUS.toString(), 16.0, VCPUS_SELF_MANAGED.toString(), 20.0),
        BillingProvider.AZURE);
  }

  private static String tallyId(TallySummary tallySummary) {
    return tallySummary.getTallySnapshots().getFirst().getId().toString();
  }

  private void givenAcmContractWithEmptyMetrics() {
    contractsWiremock.setupNoContractCoverage(orgId, RHACM.getName());
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

  private void givenNoAcmContract() {
    contractsWiremock.setupContractNotFound(orgId, RHACM.getName());
  }

  private TallySummary givenAcmManagedRemittance(double currentTotal) {
    TallySummary tallySummary =
        whenAcmSingleMetricTallyIsPublished(VCPUS.toString(), currentTotal, BillingProvider.AWS);
    thenTallyRemittancesMatch(
        tallyId(tallySummary), Map.of(VCPUS.toString(), currentTotal), BillingProvider.AWS);
    return tallySummary;
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

  private TallySummary whenAcmSingleMetricTallyIsPublished(
      String metricId, double currentTotal, BillingProvider provider) {
    TallySummary tallySummary =
        createTallySummary(
            orgId, RHACM.getName(), metricId, currentTotal, provider, billingAccountId);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private List<BillableUsageAggregate> whenAcmHourlyAggregatesAreFlushed(
      OffsetDateTime snapshotDate, double managedTotal, double selfManagedTotal) {
    kafkaBridge.produceKafkaMessage(
        TALLY,
        createTallySummary(
            orgId,
            RHACM.getName(),
            VCPUS.toString(),
            managedTotal,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDate));
    kafkaBridge.produceKafkaMessage(
        TALLY,
        createTallySummary(
            orgId,
            RHACM.getName(),
            VCPUS_SELF_MANAGED.toString(),
            selfManagedTotal,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDate));
    kafkaBridge.waitForKafkaMessage(
        BILLABLE_USAGE, MessageValidators.billableUsageMatches(orgId, RHACM.getName()), 2);
    return whenHourlyAggregatesAreReceived(orgId, 2);
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

  private void thenNoBillableUsageKafkaMessage() {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0,
                kafkaBridge
                    .waitForKafkaMessage(
                        BILLABLE_USAGE,
                        MessageValidators.billableUsageMatches(orgId, RHACM.getName()),
                        0,
                        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                    .size(),
                "Expected no billable-usage Kafka message for rhacm"),
        AwaitilitySettings.usingTimeout(Duration.ofSeconds(15)));
  }

  private void thenNoTallyRemittanceRows(String tallyId) {
    service.getRemittancesByTallyExpectBadRequest(tallyId);
  }

  private void thenHourlyAggregatePerAcmMetric(
      List<BillableUsageAggregate> aggregates, double expectedManaged, double expectedSelfManaged) {
    assertEquals(2, aggregates.size(), "Expected two hourly aggregates (one per ACM metric)");
    thenAggregateHasMetricValue(aggregates, VCPUS.toString(), expectedManaged);
    thenAggregateHasMetricValue(aggregates, VCPUS_SELF_MANAGED.toString(), expectedSelfManaged);
  }

  private void thenAggregateHasMetricValue(
      List<BillableUsageAggregate> aggregates, String metricId, double expectedValue) {
    BillableUsageAggregate found =
        aggregates.stream()
            .filter(
                a ->
                    a.getAggregateKey() != null
                        && metricId.equals(a.getAggregateKey().getMetricId()))
            .findFirst()
            .orElse(null);
    assertNotNull(found, "Expected hourly aggregate for metric " + metricId);
    assertEquals(
        expectedValue,
        found.getTotalValue().doubleValue(),
        0.001,
        "Hourly aggregate totalValue for metric " + metricId);
  }
}
