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
import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.MonthlyRemittance;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.ContractStub;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ContractCoverageComponentTest extends BaseBillableUsageComponentTest {

  private static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();
  private static final MetricId MANAGED_NODES = MetricIdUtils.getManagedNodes();
  private static final String CORES_AWS_DIMENSION = ROSA.getMetric(CORES).getAwsDimension();
  private static final OffsetDateTime ANSIBLE_CONTRACT_START_MONTH =
      OffsetDateTime.of(2026, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime ANSIBLE_GRATIS_SNAPSHOT_DATE =
      OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime ANSIBLE_NEXT_MONTH_SNAPSHOT_DATE =
      OffsetDateTime.of(2026, 2, 5, 12, 0, 0, 0, ZoneOffset.UTC);

  @BeforeAll
  static void subscribeToBillableUsageTopic() {
    kafkaBridge.subscribeToTopic(BILLABLE_USAGE);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC001")
  void testSkipProcessingWhenContractEnabledProductHasNoContract() {
    givenNoContractExistsForRosa();

    whenRosaTallyIsPublished(CORES.toString(), 20.0, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittanceEquals(ROSA.getName(), CORES.toString(), 0.0);
    thenNoBillableUsageKafkaMessage(ROSA.getName());
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC002")
  void testContractFullyCoversUsage() {
    double contractMetricValue = 10.0;
    double currentTotal = 4.0;
    givenRosaContractCoverage(INSTANCE_HOURS, contractMetricValue);

    whenRosaTallyIsPublished(
        INSTANCE_HOURS.toString(), currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittanceEquals(ROSA.getName(), INSTANCE_HOURS.toString(), 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC003")
  void testContractPartiallyCoversUsage() {
    double contractMetricValue = 3.0;
    double currentTotal = 4.0;
    givenRosaContractCoverage(INSTANCE_HOURS, contractMetricValue);

    TallySummary tallySummary =
        whenRosaTallyIsPublished(
            INSTANCE_HOURS.toString(), currentTotal, OffsetDateTime.now(ZoneOffset.UTC));
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittancePendingValueEquals(tallyId, 1.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC004")
  void testContractWithNoMetricsStillAllowsBilling() {
    double currentTotal = 4.0;
    givenRosaContractWithEmptyMetrics();

    TallySummary tallySummary =
        whenRosaTallyIsPublished(
            INSTANCE_HOURS.toString(), currentTotal, OffsetDateTime.now(ZoneOffset.UTC));
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittancePendingValueEquals(tallyId, currentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC005")
  void testCreateGratisRemittanceWithoutKafkaEmission() {
    double usageValue = 2.0;
    givenAnsibleContractStartingInCurrentMonth(ANSIBLE_GRATIS_SNAPSHOT_DATE);

    TallySummary tallySummary =
        whenAnsibleTallyIsPublished(usageValue, ANSIBLE_GRATIS_SNAPSHOT_DATE);
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittanceStatusEquals(tallyId, RemittanceStatus.GRATIS);
    thenNoBillableUsageKafkaMessage(ANSIBLE_AAP_MANAGED.getName());
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC006")
  void testGratisNotAppliedInMonthAfterContractStart() {
    double usageValue = 2.0;
    givenAnsibleContractStartingInCurrentMonth(ANSIBLE_CONTRACT_START_MONTH);

    TallySummary tallySummary =
        whenAnsibleTallyIsPublished(usageValue, ANSIBLE_NEXT_MONTH_SNAPSHOT_DATE);
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittanceStatusEquals(tallyId, RemittanceStatus.PENDING);
    thenBillableUsageKafkaMessageEmitted(ANSIBLE_AAP_MANAGED.getName(), usageValue);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC007")
  void testResolveAwsDimensionAsContractMetricId() {
    double contractMetricValue = 10.0;
    double currentTotal = 4.0;
    // Contract stub uses AWS dimension (four_vcpu_hour), not the SWATCH metric id (Cores).
    givenRosaContractCoverageOnAwsDimension(CORES_AWS_DIMENSION, contractMetricValue);

    whenRosaTallyIsPublished(CORES.toString(), currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittanceEquals(ROSA.getName(), CORES.toString(), 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC008")
  void testHandleContractsApiUnavailable() {
    givenContractsApiReturnsServerError();

    whenRosaTallyIsPublished(CORES.toString(), 8.0, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittanceEquals(ROSA.getName(), CORES.toString(), 0.0);
    thenNoBillableUsageKafkaMessage(ROSA.getName());
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC009")
  void shouldSkipRemittanceWhenUsageWithinMultiContractCoverage() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA.getName(),
        List.of(
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2),
                end,
                Map.of(awsDimension, 3.0),
                "arn:aws:license-manager:1:license:a"),
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1),
                end,
                Map.of(awsDimension, 3.0),
                "arn:aws:license-manager:1:license:b")));

    whenRosaTallyIsPublished(INSTANCE_HOURS.toString(), 5.0, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittanceEquals(ROSA.getName(), INSTANCE_HOURS.toString(), 0.0);
    // TODO(SWATCH-5443): restore once zero remitted value no longer emits BillableUsage to Kafka
    // thenNoBillableUsageKafkaMessage(ROSA.getName());
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC010")
  void shouldStampNewestLicenseIdWhenUsageExceedsCoverage() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String newerLicense = "arn:aws:license-manager:1:license:newer";
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA.getName(),
        List.of(
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2),
                end,
                Map.of(awsDimension, 2.0),
                "arn:aws:license-manager:1:license:older"),
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1),
                end,
                Map.of(awsDimension, 2.0),
                newerLicense)));

    TallySummary tallySummary =
        whenRosaTallyIsPublished(
            INSTANCE_HOURS.toString(), 5.0, OffsetDateTime.now(ZoneOffset.UTC));
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittancePendingValueAndLicenseEquals(tallyId, 1.0, newerLicense);
    thenBillableUsageKafkaMessageWithLicense(ROSA.getName(), 1.0, newerLicense);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC011")
  void shouldSelectLicensedAgreementWhenMixedWithUnlicensed() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String licensedId = "arn:aws:license-manager:1:license:only";
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA.getName(),
        List.of(
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(2),
                end,
                Map.of(awsDimension, 2.0),
                licensedId),
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(10),
                end,
                Map.of(awsDimension, 2.0),
                null)));

    TallySummary tallySummary =
        whenRosaTallyIsPublished(
            INSTANCE_HOURS.toString(), 5.0, OffsetDateTime.now(ZoneOffset.UTC));
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittancePendingValueAndLicenseEquals(tallyId, 1.0, licensedId);
    thenBillableUsageKafkaMessageWithLicense(ROSA.getName(), 1.0, licensedId);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC012")
  void shouldBillWhenMixedGratisAndEstablishedAgreements() {
    // Gratis requires EVERY active agreement to start after month start — not only the newest.
    String awsDimension = ANSIBLE_AAP_MANAGED.getMetric(MANAGED_NODES).getAwsDimension();
    OffsetDateTime monthStart = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime end = ANSIBLE_GRATIS_SNAPSHOT_DATE.plusYears(1);
    String newestLicense = "arn:aws:license-manager:1:license:jan-15";
    givenContracts(
        ANSIBLE_AAP_MANAGED.getName(),
        List.of(
            new ContractStub(
                monthStart,
                end,
                Map.of(awsDimension, 1.0),
                "arn:aws:license-manager:1:license:jan-1"),
            new ContractStub(
                ANSIBLE_GRATIS_SNAPSHOT_DATE.withDayOfMonth(15),
                end,
                Map.of(awsDimension, 1.0),
                newestLicense)));

    TallySummary tallySummary = whenAnsibleTallyIsPublished(3.0, ANSIBLE_GRATIS_SNAPSHOT_DATE);
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittanceStatusLicenseAndValueEquals(
        tallyId, RemittanceStatus.PENDING, newestLicense, 1.0);
    thenBillableUsageKafkaMessageWithLicense(ANSIBLE_AAP_MANAGED.getName(), 1.0, newestLicense);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC013")
  void shouldMarkGratisWhenAllAgreementsAreGratisEligible() {
    String awsDimension = ANSIBLE_AAP_MANAGED.getMetric(MANAGED_NODES).getAwsDimension();
    OffsetDateTime snapshotDate = OffsetDateTime.of(2026, 1, 25, 12, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime end = snapshotDate.plusYears(1);
    String newestLicense = "arn:aws:license-manager:1:license:jan-20";
    givenContracts(
        ANSIBLE_AAP_MANAGED.getName(),
        List.of(
            new ContractStub(
                OffsetDateTime.of(2026, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC),
                end,
                Map.of(awsDimension, 1.0),
                "arn:aws:license-manager:1:license:jan-10"),
            new ContractStub(
                OffsetDateTime.of(2026, 1, 20, 12, 0, 0, 0, ZoneOffset.UTC),
                end,
                Map.of(awsDimension, 1.0),
                newestLicense)));

    TallySummary tallySummary = whenAnsibleTallyIsPublished(3.0, snapshotDate);
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittanceStatusLicenseAndValueEquals(
        tallyId, RemittanceStatus.GRATIS, newestLicense, 1.0);
    thenNoBillableUsageKafkaMessage(ANSIBLE_AAP_MANAGED.getName());
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC014")
  void shouldSelectLexicographicallySmallerLicenseOnTie() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String smallerLicense = "arn:aws:license-manager:1:license:a";
    String largerLicense = "arn:aws:license-manager:1:license:z";
    OffsetDateTime sameStart = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1);
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA.getName(),
        List.of(
            new ContractStub(sameStart, end, Map.of(awsDimension, 2.0), largerLicense),
            new ContractStub(sameStart, end, Map.of(awsDimension, 2.0), smallerLicense)));

    TallySummary tallySummary =
        whenRosaTallyIsPublished(
            INSTANCE_HOURS.toString(), 5.0, OffsetDateTime.now(ZoneOffset.UTC));
    String tallyId = tallySummary.getTallySnapshots().getFirst().getId().toString();

    thenTallyRemittancePendingValueAndLicenseEquals(tallyId, 1.0, smallerLicense);
    thenBillableUsageKafkaMessageWithLicense(ROSA.getName(), 1.0, smallerLicense);
  }

  private void givenNoContractExistsForRosa() {
    contractsWiremock.setupContractNotFound(orgId, ROSA.getName());
  }

  private void givenContracts(String productId, List<ContractStub> contracts) {
    contractsWiremock.setupContracts(orgId, productId, contracts);
  }

  private void givenRosaContractWithEmptyMetrics() {
    contractsWiremock.setupNoContractCoverage(orgId, ROSA.getName());
  }

  private void givenRosaContractCoverage(MetricId metricId, double contractBillableUnits) {
    contractsWiremock.setupContractCoverage(
        orgId, ROSA.getName(), ROSA.getMetric(metricId).getAwsDimension(), contractBillableUnits);
  }

  private void givenRosaContractCoverageOnAwsDimension(
      String awsDimension, double contractBillableUnits) {
    contractsWiremock.setupContractCoverage(
        orgId, ROSA.getName(), awsDimension, contractBillableUnits);
  }

  private void givenAnsibleContractStartingInCurrentMonth(OffsetDateTime snapshotDate) {
    OffsetDateTime contractStart =
        snapshotDate.withDayOfMonth(1).plusHours(2).withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime contractEnd = contractStart.plusYears(1);
    contractsWiremock.setupContractCoverage(
        orgId, ANSIBLE_AAP_MANAGED.getName(), contractStart, contractEnd);
  }

  private void givenContractsApiReturnsServerError() {
    contractsWiremock.setupContractServiceError(orgId);
  }

  private TallySummary whenRosaTallyIsPublished(
      String metricId, double currentTotal, OffsetDateTime snapshotDate) {
    TallySummary tallySummary =
        createTallySummary(
            orgId,
            ROSA.getName(),
            metricId,
            currentTotal,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDate);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private TallySummary whenAnsibleTallyIsPublished(double usageValue, OffsetDateTime snapshotDate) {
    TallySummary tallySummary =
        createTallySummary(
            orgId,
            ANSIBLE_AAP_MANAGED.getName(),
            MANAGED_NODES.toString(),
            usageValue,
            BillingProvider.AWS,
            billingAccountId,
            snapshotDate);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private void thenAccountRemittanceEquals(
      String productId, String metricId, double expectedRemittedValue) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<MonthlyRemittance> remittances =
              service.getRemittances(
                  productId,
                  orgId,
                  metricId,
                  BillingProvider.AWS.toTallyApiModel().value(),
                  billingAccountId);
          assertFalse(remittances.isEmpty(), "Expected account remittance response");
          assertEquals(
              expectedRemittedValue,
              remittances.get(0).getRemittedValue(),
              0.001,
              "Account remittance value mismatch");
        });
  }

  private void thenTallyRemittancePendingValueEquals(
      String tallyId, double expectedRemittedPendingValue) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
          assertNotNull(remittances, "Remittances should exist for tally " + tallyId);
          assertEquals(1, remittances.size(), "Expected one remittance for tally " + tallyId);
          assertEquals(
              expectedRemittedPendingValue,
              remittances.get(0).getRemittedPendingValue(),
              0.001,
              "Remitted pending value mismatch");
        });
  }

  private void thenTallyRemittanceStatusEquals(String tallyId, RemittanceStatus expectedStatus) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
          assertNotNull(remittances, "Remittances should exist for tally " + tallyId);
          assertEquals(1, remittances.size(), "Expected one remittance for tally " + tallyId);
          assertEquals(
              expectedStatus.name(),
              remittances.get(0).getStatus(),
              "Remittance status mismatch for tally " + tallyId);
        });
  }

  private void thenNoBillableUsageKafkaMessage(String productId) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0,
                kafkaBridge
                    .waitForKafkaMessage(
                        BILLABLE_USAGE,
                        MessageValidators.billableUsageMatches(orgId, productId),
                        0,
                        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                    .size(),
                "Expected no billable-usage Kafka message for org/product"),
        AwaitilitySettings.usingTimeout(Duration.ofSeconds(15)));
  }

  private void thenBillableUsageKafkaMessageEmitted(String productId, double expectedValue) {
    thenBillableUsageKafkaMessageWithLicense(productId, expectedValue, null);
  }

  private void thenTallyRemittancePendingValueAndLicenseEquals(
      String tallyId, double expectedRemittedPendingValue, String expectedLicenseId) {
    thenTallyRemittanceStatusLicenseAndValueEquals(
        tallyId, null, expectedLicenseId, expectedRemittedPendingValue);
  }

  private void thenTallyRemittanceStatusLicenseAndValueEquals(
      String tallyId,
      RemittanceStatus expectedStatus,
      String expectedLicenseId,
      double expectedRemittedPendingValue) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
          assertNotNull(remittances, "Remittances should exist for tally " + tallyId);
          assertEquals(1, remittances.size(), "Expected one remittance for tally " + tallyId);
          assertEquals(
              expectedRemittedPendingValue,
              remittances.get(0).getRemittedPendingValue(),
              0.001,
              "Remitted pending value mismatch");
          if (expectedStatus != null) {
            assertEquals(
                expectedStatus.name(),
                remittances.getFirst().getStatus(),
                "Remittance status mismatch for tally " + tallyId);
          }
          String actualLicenseId = remittances.getFirst().getLicenseId();
          if (expectedLicenseId == null) {
            assertNull(actualLicenseId, "Expected null licenseId");
          } else {
            assertEquals(expectedLicenseId, actualLicenseId, "Remittance licenseId mismatch");
          }
        });
  }

  private void thenBillableUsageKafkaMessageWithLicense(
      String productId, double expectedValue, String expectedLicenseId) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<BillableUsage> messages =
              kafkaBridge.waitForKafkaMessage(
                  BILLABLE_USAGE,
                  MessageValidators.billableUsageMatchesWithValue(orgId, productId, expectedValue),
                  1,
                  AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(5)));
          assertEquals(
              1,
              messages.size(),
              "Expected one billable-usage Kafka message for org/product/value");
          String actualLicenseId = messages.getFirst().getLicenseId();
          if (expectedLicenseId == null) {
            assertNull(actualLicenseId, "Expected null licenseId on usage");
          } else {
            assertEquals(expectedLicenseId, actualLicenseId, "BillableUsage licenseId mismatch");
          }
        },
        AwaitilitySettings.usingTimeout(Duration.ofSeconds(60)));
  }
}
