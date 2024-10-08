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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.exceptions.ContractMissingException;
import com.redhat.swatch.billable.usage.exceptions.ErrorCode;
import com.redhat.swatch.billable.usage.exceptions.ExternalServiceException;
import com.redhat.swatch.billable.usage.services.model.ContractCoverage;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
class ContractsControllerTest {
  private static final String CONTRACT_METRIC_ID = "four_vcpu_0";
  private static final String CONTRACT_CONTROL_PLANE_METRIC_ID = "control_plane_0";
  private static final String CORES = "Cores";

  private static SubscriptionDefinitionRegistry originalReference;

  @InjectMock @RestClient DefaultApi contractsApi;
  @Inject ApplicationClock clock;
  @Inject ContractsController controller;
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
    /* We need to reset the registry because the mock SubscriptionDefinitionRegistry uses stubbed
     * SubscriptionDefinitions.  If the test run order happens to result in the stubs being used first for a tag lookup
     * then the SubscriptionDefinition cache will be populated with the stub's particulars which we don't want. */
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

  /**
   * Remove the mocked instance from the class. It is important to clean up the class, because other
   * tests will be confused with the mocked instance.
   *
   * @throws Exception if the instance could not be accessible
   */
  @AfterEach
  public void resetSingleton() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(null, null);
  }

  @Test
  void testThrowsIllegalStateExceptionWhenProductIsNotContractEnabled() {
    BillableUsage usage = defaultUsage();
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, false, false);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> controller.getContractCoverage(usage));
    assertEquals(
        String.format("Product %s is not contract enabled.", usage.getProductId()), e.getMessage());
  }

  @Test
  void testContractApiCallMadeWithConfiguredAwsDimensionAsMetricIdWhenBillingProviderIsAws()
      throws Exception {
    BillableUsage usage = defaultUsage();

    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    // Make sure product is contract enabled.
    givenContractHasMetricWithContractEnabled(usage);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1));

    controller.getContractCoverage(usage);

    verify(contractsApi)
        .getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().toString(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate());
  }

  @Test
  void testContractApiMadeWithConfiguredRhmMetricsAsMetricId() throws Exception {
    BillableUsage usage = defaultUsage();
    usage.setBillingProvider(BillableUsage.BillingProvider.RED_HAT);

    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    // Make sure product is contract enabled.
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), null, CONTRACT_METRIC_ID, true, false);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1));

    controller.getContractCoverage(usage);

    verify(contractsApi)
        .getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().toString(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate());
  }

  @Test
  void testIllegalStateExceptionThrownWhenMetricIdIsNotFoundForBillingProvider() {
    BillableUsage usage = defaultUsage();

    // Make sure product is contract enabled.
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
        IllegalStateException.class,
        () -> {
          controller.getContractCoverage(usage);
        });
  }

  @Test
  void testIllegalStateExceptionThrownWhenMetricIdIsConfiguredAsEmptyForBillingProvider() {
    BillableUsage usage = defaultUsage();
    createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), null, "", true, false);

    assertThrows(
        IllegalStateException.class,
        () -> {
          controller.getContractCoverage(usage);
        });
  }

  @Test
  void testGetContractCoverageIncludesMetricValuesFromAllContracts() throws Exception {
    BillableUsage usage = defaultUsage();
    // Set up the mocked contract data
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));

    Contract contract2 = contractFromUsage(usage);
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(40));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));

    // Make sure product is contract enabled.
    givenContractHasMetricWithContractEnabled(usage);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    ContractCoverage contractCoverage = controller.getContractCoverage(usage);
    assertEquals(140, contractCoverage.getTotal());
  }

  @Test
  void testGetContractCoverageIncludesMetricValuesFromAllMetricsOfAContract() throws Exception {
    BillableUsage usage = defaultUsage();
    // Set up the mocked contract data
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(50));

    Contract contract2 = contractFromUsage(usage);
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_CONTROL_PLANE_METRIC_ID).value(20));

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Make sure product is contract enabled.
    givenContractHasMetricWithContractEnabled(usage);

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    ContractCoverage contractCoverage = controller.getContractCoverage(usage);
    assertEquals(200, contractCoverage.getTotal());
  }

  @Test
  void testGetContractCoverageWhenThereAreContractsGratisAndPendingThenIsNotGratis()
      throws Exception {
    BillableUsage usage = defaultUsage();
    // Set up the mocked contract data
    Contract gratisContract = contractFromUsage(usage);
    gratisContract.setStartDate(clock.startOfCurrentMonth().plusHours(1));
    gratisContract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    Contract regularContract = contractFromUsage(usage);
    regularContract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(gratisContract, regularContract));

    // Make sure product is contract enabled.
    givenContractHasMetricWithGratisEnabled(usage);

    // Contract coverage should return that is gratis because there is an existing contract with
    // gratis for the usage.
    ContractCoverage contractCoverage = controller.getContractCoverage(usage);
    assertFalse(contractCoverage.isGratis());
  }

  @Test
  void testGetContractCoverageWhenAllTheContractsAreThenIsGratis() throws Exception {
    BillableUsage usage = defaultUsage();
    // Set up the mocked contract data
    Contract gratisContract = contractFromUsage(usage);
    gratisContract.setStartDate(clock.startOfCurrentMonth().plusHours(1));
    gratisContract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    Contract anotherGratisContract = contractFromUsage(usage);
    anotherGratisContract.setStartDate(clock.startOfCurrentMonth().plusHours(1));
    anotherGratisContract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(gratisContract, anotherGratisContract));

    // Make sure product is contract enabled.
    givenContractHasMetricWithGratisEnabled(usage);

    // Contract coverage should return that is gratis because there is an existing contract with
    // gratis for the usage.
    ContractCoverage contractCoverage = controller.getContractCoverage(usage);
    assertTrue(contractCoverage.isGratis());
  }

  @Test
  void testContractsFilteredByDateWhenGettingCoverage() throws Exception {
    BillableUsage usage = defaultUsage();

    // Start of the month, with no end date (VALID)
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    // Start of the month, ending at the end of the month (VALID)
    Contract contract2 = contractFromUsage(usage);
    contract2.setEndDate(clock.endOfMonth(contract2.getStartDate()));
    contract2.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));

    // Future contract, no end date (INVALID - not in usage range)
    Contract contract3 = contractFromUsage(usage);
    contract3.setStartDate(clock.startOfCurrentMonth().plusMonths(1));
    contract3.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(5));

    // Expired contract (INVALID - contract ended before usage date).
    Contract contract4 = contractFromUsage(usage);
    contract4.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    contract4.setEndDate(clock.endOfMonth(contract4.getStartDate().plusMonths(1)));
    contract4.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(10));

    // Make sure product is contract enabled.
    givenContractHasMetricWithContractEnabled(usage);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    ContractCoverage contractCoverage = controller.getContractCoverage(usage);
    assertEquals(125, contractCoverage.getTotal());
  }

  @Test
  void throwsExternalServiceExceptionWhenApiCallFails() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);
    doThrow(ApiException.class)
        .when(contractsApi)
        .getContract(any(), any(), any(), any(), any(), any());
    ExternalServiceException e =
        assertThrows(
            ExternalServiceException.class,
            () -> {
              controller.getContractCoverage(usage);
            });
    assertEquals(ErrorCode.CONTRACTS_SERVICE_ERROR, e.getCode());
    assertEquals(
        String.format("Could not look up contract info for usage! %s", usage), e.getMessage());
  }

  @Test
  void throwsContractMissingExceptionWhenNoContractsFound() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractHasMetricWithContractEnabled(usage);
    when(contractsApi.getContract(any(), any(), any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    ContractMissingException e =
        assertThrows(
            ContractMissingException.class,
            () -> {
              controller.getContractCoverage(usage);
            });
    assertEquals(String.format("No contract info found for usage! %s", usage), e.getMessage());
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
        .endDate(OffsetDateTime.now())
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
