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
package org.candlepin.subscriptions.tally.billing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.SubscriptionDefinitionRegistry;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.contracts.api.model.Contract;
import com.redhat.swatch.contracts.api.model.Metric;
import com.redhat.swatch.contracts.api.resources.DefaultApi;
import com.redhat.swatch.contracts.client.ApiException;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsControllerTest {
  private static final String CONTRACT_METRIC_ID = "four_vcpu_0";
  private static final String CONTRACT_CONTROL_PLANE_METRIC_ID = "control_plane_0";

  private static SubscriptionDefinitionRegistry originalReference;

  @Mock DefaultApi contractsApi;
  @Mock ContractsClientProperties contractsClientProperties;
  private SubscriptionDefinitionRegistry subscriptionDefinitionRegistry;
  private ApplicationClock clock;
  private ContractsController controller;

  @BeforeAll
  static void setupClass() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    originalReference =
        (SubscriptionDefinitionRegistry) instance.get(SubscriptionDefinitionRegistry.class);
  }

  @AfterAll
  static void tearDown() throws Exception {
    Field instance = SubscriptionDefinitionRegistry.class.getDeclaredField("instance");
    instance.setAccessible(true);
    instance.set(instance, originalReference);
  }

  @BeforeEach
  void setupTest() {
    clock = new TestClockConfiguration().adjustableClock();
    controller = new ContractsController(contractsApi, contractsClientProperties);
    subscriptionDefinitionRegistry = mock(SubscriptionDefinitionRegistry.class);
    setMock(subscriptionDefinitionRegistry);
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
  void testThrowsIllegalStateExceptionWhenProductIsNotContractEnabled() throws Exception {
    BillableUsage usage = defaultUsage();
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, false);
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
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, true);

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
    usage.setBillingProvider(BillingProvider.RED_HAT);

    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(CONTRACT_METRIC_ID).value(100));

    // Make sure product is contract enabled.
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), null, CONTRACT_METRIC_ID, true);

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
            .variants(List.of(variant))
            .metrics(List.of(metric))
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
    createSubscriptionDefinition(usage.getProductId(), usage.getMetricId(), null, "", true);

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
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, true);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    Double contractCoverage = controller.getContractCoverage(usage);
    assertEquals(140, contractCoverage);
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
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, true);

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    Double contractCoverage = controller.getContractCoverage(usage);
    assertEquals(200, contractCoverage);
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
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, true);

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    Double contractCoverage = controller.getContractCoverage(usage);
    assertEquals(125, contractCoverage);
  }

  @Test
  void throwsExternalServiceExceptionWhenApiCallFails() throws Exception {
    BillableUsage usage = defaultUsage();
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, true);
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
    createSubscriptionDefinition(
        usage.getProductId(), usage.getMetricId(), CONTRACT_METRIC_ID, null, true);
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

  @Test
  void testDeleteContractsWithOrg() throws ApiException {
    controller.deleteContractsWithOrg("org1");
    verify(contractsApi).deleteContractsByOrg("org1");
  }

  private BillableUsage defaultUsage() {
    return new BillableUsage()
        .withOrgId("org123")
        .withProductId("RHEL Server")
        .withValue(24.5)
        .withUsage(Usage.PRODUCTION)
        .withBillingAccountId("ba123")
        .withSla(Sla.PREMIUM)
        .withBillingProvider(BillingProvider.AWS)
        .withMetricId("Cores")
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
            .id(metricId)
            .build();
    var subscriptionDefinition =
        SubscriptionDefinition.builder()
            .contractEnabled(contractEnabled)
            .variants(List.of(variant))
            .metrics(List.of(awsMetric))
            .build();
    variant.setSubscription(subscriptionDefinition);
    when(subscriptionDefinitionRegistry.getSubscriptions())
        .thenReturn(List.of(subscriptionDefinition));
  }
}
