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
import static domain.Product.ANSIBLE_AAP_MANAGED;
import static domain.Product.ROSA;
import static domain.RemittanceStatus.COVERED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import api.MessageValidators;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.ContractStub;
import domain.Product;
import domain.RemittanceStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.TallySummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.TallySummaryBuilder;

public class ContractCoverageComponentTest extends BaseBillableUsageComponentTest {

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
  void shouldSkipProcessingWhenContractEnabledProductHasNoContract() {
    givenNoContractExistsForRosa();

    whenRosaTallyIsPublished(CORES, 20.0, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittedUsageEquals(ROSA, CORES, 0.0);
    thenNoBillableUsageKafkaMessage(ROSA);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC002")
  void shouldFullyCoverUsageWhenWithinContract() {
    double contractMetricValue = 10.0;
    double currentTotal = 4.0;
    givenRosaContractCoverage(INSTANCE_HOURS, contractMetricValue);

    whenRosaTallyIsPublished(INSTANCE_HOURS, currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
    thenAccountCoveredUsageSumEquals(ROSA, INSTANCE_HOURS, currentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC003")
  void shouldPartiallyCoverUsageWhenAboveContract() {
    double contractMetricValue = 3.0;
    double currentTotal = 4.0;
    givenRosaContractCoverage(INSTANCE_HOURS, contractMetricValue);

    TallySummary tallySummary =
        whenRosaTallyIsPublished(INSTANCE_HOURS, currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenTallyRemittancePendingValueEquals(tallySummary, 1.0);
    thenAccountCoveredUsageSumEquals(ROSA, INSTANCE_HOURS, contractMetricValue);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC004")
  void shouldBillWhenContractHasNoMetrics() {
    double currentTotal = 4.0;
    givenRosaContractWithEmptyMetrics();

    TallySummary tallySummary =
        whenRosaTallyIsPublished(INSTANCE_HOURS, currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenTallyRemittancePendingValueEquals(tallySummary, currentTotal);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC005")
  void shouldCreateGratisRemittanceWithoutKafkaEmission() {
    double usageValue = 2.0;
    givenAnsibleContractStartingInCurrentMonth(ANSIBLE_GRATIS_SNAPSHOT_DATE);

    TallySummary tallySummary =
        whenAnsibleTallyIsPublished(usageValue, ANSIBLE_GRATIS_SNAPSHOT_DATE);

    thenTallyRemittanceStatusEquals(tallySummary, RemittanceStatus.GRATIS);
    thenNoBillableUsageKafkaMessage(ANSIBLE_AAP_MANAGED);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC006")
  void shouldNotApplyGratisInMonthAfterContractStart() {
    double usageValue = 2.0;
    givenAnsibleContractStartingInCurrentMonth(ANSIBLE_CONTRACT_START_MONTH);

    TallySummary tallySummary =
        whenAnsibleTallyIsPublished(usageValue, ANSIBLE_NEXT_MONTH_SNAPSHOT_DATE);

    thenTallyRemittanceStatusEquals(tallySummary, RemittanceStatus.PENDING);
    thenBillableUsageKafkaMessageEmitted(ANSIBLE_AAP_MANAGED, usageValue);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC007")
  void shouldResolveAwsDimensionAsContractMetricId() {
    double contractMetricValue = 10.0;
    double currentTotal = 4.0;
    // Contract stub uses AWS dimension (four_vcpu_hour), not the SWATCH metric id (Cores).
    givenRosaContractCoverage(CORES, contractMetricValue);

    whenRosaTallyIsPublished(CORES, currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittedUsageEquals(ROSA, CORES, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC008")
  void shouldSkipProcessingWhenContractsApiUnavailable() {
    givenContractsApiReturnsServerError();

    whenRosaTallyIsPublished(CORES, 8.0, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittedUsageEquals(ROSA, CORES, 0.0);
    thenNoBillableUsageKafkaMessage(ROSA);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC009")
  void shouldSkipRemittanceWhenUsageWithinMultiContractCoverage() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA,
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

    whenRosaTallyIsPublished(INSTANCE_HOURS, 5.0, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC010")
  void shouldStampNewestLicenseIdWhenUsageExceedsCoverage() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String newerLicense = "arn:aws:license-manager:1:license:newer";
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA,
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
        whenRosaTallyIsPublished(INSTANCE_HOURS, 5.0, OffsetDateTime.now(ZoneOffset.UTC));
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();

    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 1.0, newerLicense);
    thenTallyRemittancePendingValueAndLicenseEquals(tallyId, 1.0, newerLicense);
    thenBillableUsageKafkaMessageWithLicense(ROSA, 1.0, newerLicense);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC011")
  void shouldSelectLicensedAgreementWhenMixedWithUnlicensed() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String licensedId = "arn:aws:license-manager:1:license:only";
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA,
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
        whenRosaTallyIsPublished(INSTANCE_HOURS, 5.0, OffsetDateTime.now(ZoneOffset.UTC));
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();

    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 1.0, licensedId);
    thenTallyRemittancePendingValueAndLicenseEquals(tallyId, 1.0, licensedId);
    thenBillableUsageKafkaMessageWithLicense(ROSA, 1.0, licensedId);
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
        ANSIBLE_AAP_MANAGED,
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
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();

    thenAccountRemittedUsageEquals(ANSIBLE_AAP_MANAGED, MANAGED_NODES, 1.0, newestLicense);
    thenTallyRemittanceStatusLicenseAndValueEquals(
        tallyId, RemittanceStatus.PENDING, newestLicense, 1.0);
    thenBillableUsageKafkaMessageWithLicense(ANSIBLE_AAP_MANAGED, 1.0, newestLicense);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC013")
  void shouldMarkGratisWhenAllAgreementsAreGratisEligible() {
    String awsDimension = ANSIBLE_AAP_MANAGED.getMetric(MANAGED_NODES).getAwsDimension();
    OffsetDateTime snapshotDate = OffsetDateTime.of(2026, 1, 25, 12, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime end = snapshotDate.plusYears(1);
    String newestLicense = "arn:aws:license-manager:1:license:jan-20";
    givenContracts(
        ANSIBLE_AAP_MANAGED,
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
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();

    thenAccountRemittedUsageEquals(ANSIBLE_AAP_MANAGED, MANAGED_NODES, 1.0, newestLicense);
    thenTallyRemittanceStatusLicenseAndValueEquals(
        tallyId, RemittanceStatus.GRATIS, newestLicense, 1.0);
    thenNoBillableUsageKafkaMessage(ANSIBLE_AAP_MANAGED);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC014")
  void shouldSelectLexicographicallySmallerLicenseOnTie() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String lexicographicallySmallerLicenseId = "arn:aws:license-manager:1:license:a";
    String lexicographicallyLargerLicenseId = "arn:aws:license-manager:1:license:z";
    OffsetDateTime sameStart = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1);
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    givenContracts(
        ROSA,
        List.of(
            new ContractStub(
                sameStart, end, Map.of(awsDimension, 2.0), lexicographicallyLargerLicenseId),
            new ContractStub(
                sameStart, end, Map.of(awsDimension, 2.0), lexicographicallySmallerLicenseId)));

    TallySummary tallySummary =
        whenRosaTallyIsPublished(INSTANCE_HOURS, 5.0, OffsetDateTime.now(ZoneOffset.UTC));
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();

    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 1.0, lexicographicallySmallerLicenseId);
    thenTallyRemittancePendingValueAndLicenseEquals(
        tallyId, 1.0, lexicographicallySmallerLicenseId);
    thenBillableUsageKafkaMessageWithLicense(ROSA, 1.0, lexicographicallySmallerLicenseId);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC015")
  void shouldExposeSeparateMonthlyAccountRemittanceRowsPerLicenseId() {
    String awsDimension = ROSA.getMetric(INSTANCE_HOURS).getAwsDimension();
    String licenseA = "arn:aws:license-manager:1:license:a";
    String licenseB = "arn:aws:license-manager:1:license:b";
    OffsetDateTime end = OffsetDateTime.now(ZoneOffset.UTC).plusYears(1);
    OffsetDateTime firstSnapshot = OffsetDateTime.now(ZoneOffset.UTC);
    givenContracts(
        ROSA,
        List.of(
            new ContractStub(
                firstSnapshot.minusMonths(1), end, Map.of(awsDimension, 2.0), licenseA)));

    whenRosaTallyIsPublished(INSTANCE_HOURS, 3.0, firstSnapshot);
    thenAccountRemittedUsageRowCount(ROSA, INSTANCE_HOURS, 1);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 1.0, licenseA);

    givenContracts(
        ROSA,
        List.of(
            new ContractStub(
                firstSnapshot.minusMonths(1), end, Map.of(awsDimension, 2.0), licenseA),
            new ContractStub(
                firstSnapshot.minusDays(5), end, Map.of(awsDimension, 2.0), licenseB)));

    whenRosaTallyIsPublished(INSTANCE_HOURS, 6.0, firstSnapshot.plusHours(1));

    thenAccountRemittedUsageRowCount(ROSA, INSTANCE_HOURS, 2);
    thenAccountRemittedUsageSumEquals(ROSA, INSTANCE_HOURS, 2.0);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 1.0, licenseB);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 1.0, licenseA);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC016")
  void shouldExposeCoveredStatusInMonthlyAccountRemittanceApi() {
    String licenseId = "arn:aws:license-manager:1:license:prepaid";
    double contractMetricValue = 10.0;
    double currentTotal = 4.0;
    givenContracts(
        ROSA,
        List.of(
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1),
                OffsetDateTime.now(ZoneOffset.UTC).plusYears(1),
                Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), contractMetricValue),
                licenseId)));

    whenRosaTallyIsPublished(INSTANCE_HOURS, currentTotal, OffsetDateTime.now(ZoneOffset.UTC));

    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, currentTotal, licenseId);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
  }

  @Test
  @TestPlanName("billable-usage-contract-coverage-TC017")
  void shouldGrowCoveredRemittanceWhileUsageStaysPrepaid() {
    String licenseId = "arn:aws:license-manager:1:license:prepaid";
    double contractMetricValue = 100.0;
    double firstTotal = 40.0;
    double secondTotal = 90.0;
    givenContracts(
        ROSA,
        List.of(
            new ContractStub(
                OffsetDateTime.now(ZoneOffset.UTC).minusMonths(1),
                OffsetDateTime.now(ZoneOffset.UTC).plusYears(1),
                Map.of(ROSA.getMetric(INSTANCE_HOURS).getAwsDimension(), contractMetricValue),
                licenseId)));

    whenRosaTallyIsPublished(
        INSTANCE_HOURS, firstTotal, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, firstTotal, licenseId);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);

    whenRosaTallyIsPublished(
        INSTANCE_HOURS, secondTotal, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    thenAccountCoveredUsageEquals(ROSA, INSTANCE_HOURS, secondTotal, licenseId);
    thenAccountRemittedUsageEquals(ROSA, INSTANCE_HOURS, 0.0);
  }

  private void givenNoContractExistsForRosa() {
    contractsWiremock.forContractsAPI().stubContractNotFound(orgId, ROSA);
  }

  private void givenContracts(Product product, List<ContractStub> contracts) {
    contractsWiremock.forContractsAPI().stubContracts(orgId, product, contracts);
  }

  private void givenRosaContractWithEmptyMetrics() {
    givenNoContractCoverageForProduct(ROSA);
  }

  private void givenRosaContractCoverage(MetricId metricId, double contractBillableUnits) {
    contractsWiremock
        .forContractsAPI()
        .stubContractCoverage(
            orgId, ROSA, ROSA.getMetric(metricId).getAwsDimension(), contractBillableUnits);
  }

  private void givenAnsibleContractStartingInCurrentMonth(OffsetDateTime snapshotDate) {
    OffsetDateTime contractStart =
        snapshotDate.withDayOfMonth(1).plusHours(2).withOffsetSameInstant(ZoneOffset.UTC);
    OffsetDateTime contractEnd = contractStart.plusYears(1);
    contractsWiremock
        .forContractsAPI()
        .stubContractCoverage(orgId, ANSIBLE_AAP_MANAGED, contractStart, contractEnd);
  }

  private void givenContractsApiReturnsServerError() {
    contractsWiremock.forContractsAPI().stubContractServiceError(orgId);
  }

  private TallySummary whenRosaTallyIsPublished(
      MetricId metricId, double currentTotal, OffsetDateTime snapshotDate) {
    TallySummary tallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            ROSA.getName(),
            metricId.toString(),
            currentTotal,
            AWS,
            billingAccountId,
            snapshotDate);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private TallySummary whenAnsibleTallyIsPublished(double usageValue, OffsetDateTime snapshotDate) {
    TallySummary tallySummary =
        TallySummaryBuilder.buildTallySummary(
            orgId,
            ANSIBLE_AAP_MANAGED.getName(),
            MANAGED_NODES.toString(),
            usageValue,
            AWS,
            billingAccountId,
            snapshotDate);
    kafkaBridge.produceKafkaMessage(TALLY, tallySummary);
    return tallySummary;
  }

  private void thenTallyRemittancePendingValueEquals(
      TallySummary tallySummary, double expectedRemittedPendingValue) {
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();
    AwaitilityUtils.untilAsserted(() -> {
      TallyRemittance remittance = overageTallyRemittance(tallyId);
      assertEquals(
          expectedRemittedPendingValue,
          remittance.getRemittedPendingValue(),
          0.001,
          "Remitted pending value mismatch");
    });
  }

  private void thenTallyRemittanceStatusEquals(
      TallySummary tallySummary, RemittanceStatus expectedStatus) {
    UUID tallyId = tallySummary.getTallySnapshots().getFirst().getId();
    AwaitilityUtils.untilAsserted(() -> {
      TallyRemittance remittance = overageTallyRemittance(tallyId);
      assertEquals(
          expectedStatus.name(),
          remittance.getStatus(),
          "Remittance status mismatch for tally " + tallyId);
    });
  }

  private void thenNoBillableUsageKafkaMessage(Product product) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0,
                kafkaBridge
                    .waitForKafkaMessage(
                        BILLABLE_USAGE,
                        MessageValidators.billableUsageMatches(orgId, product),
                        0,
                        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(2)))
                    .size(),
                "Expected no billable-usage Kafka message for org/product"),
        AwaitilitySettings.usingTimeout(Duration.ofSeconds(15)));
  }

  private void thenBillableUsageKafkaMessageEmitted(Product product, double expectedValue) {
    thenBillableUsageKafkaMessageWithLicense(product, expectedValue, null);
  }

  private void thenTallyRemittancePendingValueAndLicenseEquals(
      UUID tallyId, double expectedRemittedPendingValue, String expectedLicenseId) {
    thenTallyRemittanceStatusLicenseAndValueEquals(
        tallyId, null, expectedLicenseId, expectedRemittedPendingValue);
  }

  private void thenTallyRemittanceStatusLicenseAndValueEquals(
      UUID tallyId,
      RemittanceStatus expectedStatus,
      String expectedLicenseId,
      double expectedRemittedPendingValue) {
    AwaitilityUtils.untilAsserted(() -> {
      TallyRemittance remittance = overageTallyRemittance(tallyId);
      assertEquals(
          expectedRemittedPendingValue,
          remittance.getRemittedPendingValue(),
          0.001,
          "Remitted pending value mismatch");
      if (expectedStatus != null) {
        assertEquals(
            expectedStatus.name(),
            remittance.getStatus(),
            "Remittance status mismatch for tally " + tallyId);
      }
      String actualLicenseId = remittance.getLicenseId();
      if (expectedLicenseId == null) {
        assertNull(actualLicenseId, "Expected null licenseId");
      } else {
        assertEquals(expectedLicenseId, actualLicenseId, "Remittance licenseId mismatch");
      }
    });
  }

  private TallyRemittance overageTallyRemittance(UUID tallyId) {
    List<TallyRemittance> remittances = service.getRemittancesByTally(tallyId);
    assertNotNull(remittances, "Remittances should exist for tally " + tallyId);
    List<TallyRemittance> overage =
        remittances.stream()
            .filter(row -> !COVERED.name().equalsIgnoreCase(row.getStatus()))
            .toList();
    assertEquals(1, overage.size(), "Expected one overage remittance for tally " + tallyId);
    return overage.getFirst();
  }

  private void thenBillableUsageKafkaMessageWithLicense(
      Product product, double expectedValue, String expectedLicenseId) {
    AwaitilityUtils.untilAsserted(
        () -> {
          List<BillableUsage> messages =
              kafkaBridge.waitForKafkaMessage(
                  BILLABLE_USAGE,
                  MessageValidators.billableUsageMatchesWithValue(orgId, product, expectedValue),
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
