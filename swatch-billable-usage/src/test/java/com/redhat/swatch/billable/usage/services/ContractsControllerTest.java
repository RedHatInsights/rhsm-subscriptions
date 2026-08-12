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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.exceptions.ContractMissingException;
import com.redhat.swatch.billable.usage.exceptions.ErrorCode;
import com.redhat.swatch.billable.usage.exceptions.ExternalServiceException;
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
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, false);
    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> controller.getValidContracts(usage));
    assertEquals(
        String.format("Product %s is not contract enabled.", usage.getProductId()), e.getMessage());
  }

  @Test
  void testContractApiCalledWithUsageParameters() throws Exception {
    BillableUsage usage = defaultUsage();
    Contract contract = contractFromUsage(usage);
    contract.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));
    givenContractEnabled(usage);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract));

    List<Contract> contracts = controller.getValidContracts(usage);
    assertEquals(1, contracts.size());

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
  void testContractsFilteredByDate() throws Exception {
    BillableUsage usage = defaultUsage();

    Contract validOpenEnded = contractFromUsage(usage);
    validOpenEnded.setEndDate(null);
    validOpenEnded.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    Contract validEndingThisMonth = contractFromUsage(usage);
    validEndingThisMonth.setEndDate(clock.endOfMonth(validEndingThisMonth.getStartDate()));
    validEndingThisMonth.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(25));

    Contract future = contractFromUsage(usage);
    future.setStartDate(clock.startOfCurrentMonth().plusMonths(1));
    future.setEndDate(null);
    future.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(5));

    Contract expired = contractFromUsage(usage);
    expired.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    expired.setEndDate(clock.endOfMonth(expired.getStartDate().plusMonths(1)));
    expired.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(10));

    givenContractEnabled(usage);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(validOpenEnded, validEndingThisMonth, future, expired));

    List<Contract> contracts = controller.getValidContracts(usage);
    assertEquals(2, contracts.size());
    assertEquals(List.of(validOpenEnded, validEndingThisMonth), contracts);
  }

  @Test
  void throwsExternalServiceExceptionWhenApiCallFails() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractEnabled(usage);
    doThrow(ApiException.class)
        .when(contractsApi)
        .getContract(any(), any(), any(), any(), any(), any());
    ExternalServiceException e =
        assertThrows(ExternalServiceException.class, () -> controller.getValidContracts(usage));
    assertEquals(ErrorCode.CONTRACTS_SERVICE_ERROR, e.getCode());
    assertEquals(
        String.format("Could not look up contract info for usage! %s", usage), e.getMessage());
  }

  @Test
  void throwsContractMissingExceptionWhenNoContractsFound() throws Exception {
    BillableUsage usage = defaultUsage();
    givenContractEnabled(usage);
    when(contractsApi.getContract(any(), any(), any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    ContractMissingException e =
        assertThrows(ContractMissingException.class, () -> controller.getValidContracts(usage));
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

  private void givenContractEnabled(BillableUsage usage) {
    createSubscriptionDefinition(usage.getProductId(), CORES, CONTRACT_METRIC_ID, null, true);
  }

  private void createSubscriptionDefinition(
      String tag,
      String metricId,
      String awsDimension,
      String rhmDimension,
      boolean contractEnabled) {
    var variant = Variant.builder().tag(tag).build();
    var awsMetric =
        com.redhat.swatch.configuration.registry.Metric.builder()
            .awsDimension(awsDimension)
            .rhmMetricId(rhmDimension)
            .enableGratisUsage(false)
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
