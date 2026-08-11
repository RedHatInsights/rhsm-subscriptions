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
package com.redhat.swatch.billable.usage.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.services.model.ContractCoverage;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ContractCoverageServiceTest {
  private static final String CONTRACT_METRIC_ID = "four_vcpu_0";
  private static final String CONTRACT_CONTROL_PLANE_METRIC_ID = "control_plane_0";
  private static final String CORES = "Cores";

  private static SubscriptionDefinitionRegistry originalReference;

  @InjectMock @RestClient DefaultApi contractsApi;
  @Inject ApplicationClock clock;
  @Inject ContractCoverageService contractCoverageService;
  private SubscriptionDefinitionRegistry subscriptionDefinitionRegistry;

  @BeforeAll
  static void setupClass() {
    originalReference = SubscriptionDefinitionRegistry.getInstance();
  }

  @AfterAll
  static void tearDownClass() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(instance, originalReference);
  }

  @BeforeEach
  void setupTest() {
    subscriptionDefinitionRegistry = mock(SubscriptionDefinitionRegistry.class);
    setMock(subscriptionDefinitionRegistry);
  }

  @AfterEach
  void tearDown() {
    // Reset so stubbed SubscriptionDefinitions do not pollute the tag cache for later tests.
    SubscriptionDefinitionRegistry.reset();
  }

  private void setMock(SubscriptionDefinitionRegistry mock) {
    try {
      Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
      instance.setAccessible(true);
      instance.set(instance, mock);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testIllegalStateExceptionThrownWhenMetricIdIsNotFoundForBillingProvider() {
    BillableUsage usage = defaultUsage();

    var variant = Variant.builder().tag(usage.getProductId()).build();
    var metric = new com.redhat.swatch.configuration.registry.Metric();
    metric.setId(usage.getMetricId());
    var subscriptionDefinition =
        SubscriptionDefinition.builder()
            .contractEnabled(true)
            .variants(Set.of(variant))
            .metrics(Set.of(metric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(subscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));

    assertThrows(
        IllegalStateException.class, () -> contractCoverageService.getContractCoverage(usage));
  }

  @Test
  void testIllegalStateExceptionThrownWhenMetricIdIsConfiguredAsEmptyForBillingProvider() {
    BillableUsage usage = defaultUsage();
    createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), null, "", true, false);

    assertThrows(
        IllegalStateException.class, () -> contractCoverageService.getContractCoverage(usage));
  }

  @Test
  void testGetContractCoverageIncludesMetricValuesFromAllContracts() throws Exception {
    BillableUsage usage = defaultUsage();
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));

    Contract contract2 = contractFromUsage(usage);
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(40));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));

    givenContractHasMetricWithContractEnabled(usage);
    stubContracts(usage, List.of(contract1, contract2));

    ContractCoverage contractCoverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(140, contractCoverage.getTotal());
  }

  @Test
  void testOverageLicenseIdSelectsNewestStartDate() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);

    Contract older = contractFromUsage(usage);
    older.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    older.setLicenseId("arn:aws:license-manager:us-east-1:1:license:older");
    older.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    Contract newer = contractFromUsage(usage);
    newer.setStartDate(clock.startOfCurrentMonth().minusMonths(1));
    newer.setLicenseId("arn:aws:license-manager:us-east-1:1:license:newer");
    newer.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    stubContracts(usage, List.of(older, newer));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(100, coverage.getTotal());
    assertEquals(newer.getLicenseId(), coverage.getLicenseId());
  }

  @Test
  void testOverageLicenseIdNullWhenNoContractHasLicenseId() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);

    Contract contract = contractFromUsage(usage);
    contract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    stubContracts(usage, List.of(contract));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(100, coverage.getTotal());
    assertNull(coverage.getLicenseId());
  }

  @Test
  void testOverageLicenseIdIgnoresBlankLicenseId() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);

    Contract blankLicense = contractFromUsage(usage);
    blankLicense.setStartDate(clock.startOfCurrentMonth().minusDays(5));
    blankLicense.setLicenseId(" ");
    blankLicense.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(40));

    Contract licensed = contractFromUsage(usage);
    licensed.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    licensed.setLicenseId("arn:aws:license-manager:us-east-1:1:license:real");
    licensed.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(60));

    stubContracts(usage, List.of(blankLicense, licensed));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(100, coverage.getTotal());
    assertEquals(licensed.getLicenseId(), coverage.getLicenseId());
  }

  @Test
  void testOverageLicenseIdLexicographicTieBreakWhenSameStartDate() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);
    OffsetDateTime sameStart = clock.startOfCurrentMonth().minusMonths(1);
    String smallerLicense = "arn:aws:license-manager:us-east-1:1:license:a";
    String largerLicense = "arn:aws:license-manager:us-east-1:1:license:z";

    Contract largerFirst = contractFromUsage(usage);
    largerFirst.setStartDate(sameStart);
    largerFirst.setLicenseId(largerLicense);
    largerFirst.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    Contract smallerSecond = contractFromUsage(usage);
    smallerSecond.setStartDate(sameStart);
    smallerSecond.setLicenseId(smallerLicense);
    smallerSecond.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    // Larger licenseId first in list; smaller must still win.
    stubContracts(usage, List.of(largerFirst, smallerSecond));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(100, coverage.getTotal());
    assertEquals(smallerLicense, coverage.getLicenseId());
  }

  @Test
  void testOverageLicenseIdFromLicensedSetOnlyWhileCoverageSumsMixed() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);

    Contract unlicensed = contractFromUsage(usage);
    unlicensed.setStartDate(clock.startOfCurrentMonth().minusDays(5));
    unlicensed.setLicenseId(null);
    unlicensed.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(30));

    Contract licensed = contractFromUsage(usage);
    licensed.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    licensed.setLicenseId("arn:aws:license-manager:us-east-1:1:license:only");
    licensed.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(70));

    stubContracts(usage, List.of(unlicensed, licensed));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(100, coverage.getTotal());
    assertEquals(licensed.getLicenseId(), coverage.getLicenseId());
  }

  @Test
  void testGetContractCoverageIncludesMetricValuesFromAllMetricsOfAContract() throws Exception {
    BillableUsage usage = defaultUsage();
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    Contract contract2 = contractFromUsage(usage);
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));

    stubContracts(usage, List.of(contract1, contract2));
    givenContractHasMetricWithContractEnabled(usage);

    ContractCoverage contractCoverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(200, contractCoverage.getTotal());
  }

  @Test
  void testSingleGratisEligibleAgreementIsGratisWithItsLicenseId() throws Exception {
    BillableUsage usage = defaultUsage();
    usage.setSnapshotDate(clock.startOfCurrentMonth().plusDays(24));
    givenContractHasMetricWithGratisEnabled(usage);

    String licenseId = "arn:aws:license-manager:us-east-1:1:license:gratis-only";
    Contract agreement = contractFromUsage(usage);
    agreement.setStartDate(clock.startOfCurrentMonth().plusDays(14));
    agreement.setEndDate(null);
    agreement.setLicenseId(licenseId);
    agreement.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    stubContracts(usage, List.of(agreement));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertTrue(coverage.isGratis());
    assertEquals(50, coverage.getTotal());
    assertEquals(licenseId, coverage.getLicenseId());
  }

  @Test
  void testMixedGratisAndEstablishedAgreementsIsNotGratisAndSelectsNewestLicenseId()
      throws Exception {
    BillableUsage usage = defaultUsage();
    usage.setSnapshotDate(clock.startOfCurrentMonth().plusDays(24));
    givenContractHasMetricWithGratisEnabled(usage);

    Contract establishedEarlyMonth = contractFromUsage(usage);
    establishedEarlyMonth.setStartDate(clock.startOfCurrentMonth());
    establishedEarlyMonth.setEndDate(null);
    establishedEarlyMonth.setLicenseId("arn:aws:license-manager:us-east-1:1:license:apr-1");
    establishedEarlyMonth.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    Contract establishedPriorMonth = contractFromUsage(usage);
    establishedPriorMonth.setStartDate(clock.startOfCurrentMonth().minusMonths(1));
    establishedPriorMonth.setEndDate(null);
    establishedPriorMonth.setLicenseId("arn:aws:license-manager:us-east-1:1:license:mar-1");
    establishedPriorMonth.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    Contract midMonth = contractFromUsage(usage);
    midMonth.setStartDate(clock.startOfCurrentMonth().plusDays(14));
    midMonth.setEndDate(null);
    midMonth.setLicenseId("arn:aws:license-manager:us-east-1:1:license:apr-15");
    midMonth.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    stubContracts(usage, List.of(establishedEarlyMonth, establishedPriorMonth, midMonth));

    ContractCoverage coverage = contractCoverageService.getContractCoverage(usage);
    assertFalse(coverage.isGratis());
    assertEquals(250, coverage.getTotal());
    assertEquals(midMonth.getLicenseId(), coverage.getLicenseId());
  }

  @Test
  void testAllGratisEligibleAgreementsIsGratisAndSelectsNewestLicenseId() throws Exception {
    BillableUsage usage = defaultUsage();
    Contract gratisContract = contractFromUsage(usage);
    gratisContract.setStartDate(clock.startOfCurrentMonth().plusHours(1));
    gratisContract.setLicenseId("arn:aws:license-manager:us-east-1:1:license:a");
    gratisContract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    Contract anotherGratisContract = contractFromUsage(usage);
    anotherGratisContract.setStartDate(clock.startOfCurrentMonth().plusHours(2));
    anotherGratisContract.setLicenseId("arn:aws:license-manager:us-east-1:1:license:b");
    anotherGratisContract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));

    stubContracts(usage, List.of(gratisContract, anotherGratisContract));
    givenContractHasMetricWithGratisEnabled(usage);

    ContractCoverage contractCoverage = contractCoverageService.getContractCoverage(usage);
    assertTrue(contractCoverage.isGratis());
    assertEquals(125, contractCoverage.getTotal());
    assertEquals(anotherGratisContract.getLicenseId(), contractCoverage.getLicenseId());
  }

  @Test
  void testContractsFilteredByDateWhenGettingCoverage() throws Exception {
    BillableUsage usage = defaultUsage();

    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    Contract contract2 = contractFromUsage(usage);
    contract2.setEndDate(clock.endOfMonth(contract2.getStartDate()));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));

    Contract contract3 = contractFromUsage(usage);
    contract3.setStartDate(clock.startOfCurrentMonth().plusMonths(1));
    contract3.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(5));

    Contract contract4 = contractFromUsage(usage);
    contract4.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    contract4.setEndDate(clock.endOfMonth(contract4.getStartDate().plusMonths(1)));
    contract4.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(10));

    givenContractHasMetricWithContractEnabled(usage);
    stubContracts(usage, List.of(contract1, contract2, contract3, contract4));

    ContractCoverage contractCoverage = contractCoverageService.getContractCoverage(usage);
    assertEquals(125, contractCoverage.getTotal());
  }

  private void stubContracts(BillableUsage usage, List<Contract> contracts) throws Exception {
    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(contracts);
  }

  private BillableUsage defaultUsage() {
    return new BillableUsage()
        .withOrgId("org123")
        .withProductId("RHEL Server")
        .withValue(24.5)
        .withUsage(BillableUsage.Usage.PRODUCTION)
        .withBillingAccountId("ba123")
        .withSla(BillableUsage.Sla.PREMIUM)
        .withBillingProvider(BillableUsage.BillingProvider.AWS)
        .withMetricId(CORES.toUpperCase())
        .withVendorProductCode("vendor_product_code")
        .withSnapshotDate(clock.now());
  }

  private Contract contractFromUsage(BillableUsage usage) {
    return new Contract()
        .orgId(usage.getOrgId())
        .productTags(List.of(usage.getProductId()))
        .endDate(null)
        .billingAccountId(usage.getBillingAccountId())
        .billingProvider(usage.getBillingProvider().value())
        .startDate(clock.startOfMonth(usage.getSnapshotDate()));
  }

  private void givenContractHasMetricWithGratisEnabled(BillableUsage usage) {
    createSubscriptionDefinition(usage.getProductId(), CORES, CONTRACT_METRIC_ID, null, true, true);
  }

  private void givenContractHasMetricWithContractEnabled(BillableUsage usage) {
    createSubscriptionDefinition(
        usage.getProductId(), CORES, CONTRACT_METRIC_ID, null, true, false);
  }

  private void createSubscriptionDefinition(
      String tag,
      String metricId,
      String awsDimension,
      String rhmDimension,
      boolean contractEnabled,
      boolean gratisEnabled) {
    var variant = Variant.builder().tag(tag).build();
    var awsMetric =
        com.redhat.swatch.configuration.registry.Metric.builder()
            .awsDimension(awsDimension)
            .rhmMetricId(rhmDimension)
            .enableGratisUsage(gratisEnabled)
            .id(metricId)
            .build();
    var subscriptionDefinition =
        SubscriptionDefinition.builder()
            .contractEnabled(contractEnabled)
            .variants(Set.of(variant))
            .metrics(Set.of(awsMetric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(subscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));
  }
}
