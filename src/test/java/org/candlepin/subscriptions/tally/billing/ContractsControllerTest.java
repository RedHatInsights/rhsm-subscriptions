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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.redhat.swatch.contracts.api.model.Contract;
import com.redhat.swatch.contracts.api.model.Metric;
import com.redhat.swatch.contracts.api.resources.DefaultApi;
import com.redhat.swatch.contracts.client.ApiException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExternalServiceException;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.BillingProvider;
import org.candlepin.subscriptions.json.BillableUsage.Sla;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.candlepin.subscriptions.json.BillableUsage.Usage;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractsControllerTest {

  @Mock DefaultApi contractsApi;
  @Mock TagProfile tagProfile;
  @Mock ContractsClientProperties contractsClientProperties;
  private ApplicationClock clock;
  private ContractsController controller;

  @BeforeEach
  void setupTest() {
    clock = new FixedClockConfiguration().fixedClock();
    controller = new ContractsController(tagProfile, contractsApi, contractsClientProperties);
  }

  @Test
  void testGetContractControllerReturnsNoContractValueWhenProductNotContractEnabled() {
    BillableUsage usage = defaultUsage();
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(false);
    Optional<Double> contractCoverage = controller.getContractCoverage(usage);
    assertFalse(contractCoverage.isPresent());
  }

  @Test
  void testGetContractCoverageIncludesMetricValuesFromAllContracts() throws ApiException {
    BillableUsage usage = defaultUsage();
    // Set up the mocked contract data
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(100));
    contract1.addMetricsItem(new Metric().metricId(Uom.SOCKETS.value()).value(20));

    Contract contract2 = contractFromUsage(usage);
    contract2.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(40));
    contract2.addMetricsItem(new Metric().metricId(Uom.SOCKETS.value()).value(20));

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getUom().value(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Make sure product is contract enabled.
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(true);

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    Optional<Double> contractCoverage = controller.getContractCoverage(usage);
    assertTrue(contractCoverage.isPresent());
    assertEquals(140, contractCoverage.get());
  }

  @Test
  void testGetContractCoverageIncludesMetricValuesFromAllMetricsOfAContract() throws ApiException {
    BillableUsage usage = defaultUsage();
    // Set up the mocked contract data
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(100));
    contract1.addMetricsItem(new Metric().metricId(Uom.SOCKETS.value()).value(20));
    contract1.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(50));

    Contract contract2 = contractFromUsage(usage);
    contract2.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(25));
    contract2.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(25));
    contract2.addMetricsItem(new Metric().metricId(Uom.SOCKETS.value()).value(20));

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getUom().value(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Make sure product is contract enabled.
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(true);

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    Optional<Double> contractCoverage = controller.getContractCoverage(usage);
    assertTrue(contractCoverage.isPresent());
    assertEquals(200, contractCoverage.get());
  }

  @Test
  void testContractsFilteredByDateWhenGettingCoverage() throws Exception {
    BillableUsage usage = defaultUsage();

    // Start of the month, with no end date (VALID)
    Contract contract1 = contractFromUsage(usage);
    contract1.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(100));

    // Start of the month, ending at the end of the month (VALID)
    Contract contract2 = contractFromUsage(usage);
    contract2.setEndDate(clock.endOfMonth(contract2.getStartDate()));
    contract2.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(25));

    // Future contract, no end date (INVALID - not in usage range)
    Contract contract3 = contractFromUsage(usage);
    contract3.setStartDate(clock.startOfCurrentMonth().plusMonths(1));
    contract3.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(5));

    // Expired contract (INVALID - contract ended before usage date).
    Contract contract4 = contractFromUsage(usage);
    contract4.setStartDate(clock.startOfCurrentMonth().minusMonths(2));
    contract4.setEndDate(clock.endOfMonth(contract4.getStartDate().plusMonths(1)));
    contract4.addMetricsItem(new Metric().metricId(usage.getUom().value()).value(10));

    when(contractsApi.getContract(
            usage.getOrgId(),
            usage.getProductId(),
            usage.getUom().value(),
            usage.getVendorProductCode(),
            usage.getBillingProvider().value(),
            usage.getBillingAccountId(),
            usage.getSnapshotDate()))
        .thenReturn(List.of(contract1, contract2));

    // Make sure product is contract enabled.
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(true);

    // Contract coverage should be the sum of all matching Cores metrics in the contracts.
    Optional<Double> contractCoverage = controller.getContractCoverage(usage);
    assertTrue(contractCoverage.isPresent());
    assertEquals(125, contractCoverage.get());
  }

  @Test
  void throwsExternalServiceExceptionWhenApiCallFails() throws Exception {
    BillableUsage usage = defaultUsage();
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(true);
    doThrow(ApiException.class)
        .when(contractsApi)
        .getContract(any(), any(), any(), any(), any(), any(), any());
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
  void throwsExternalServiceExceptionWhenNoContractsFound() throws Exception {
    BillableUsage usage = defaultUsage();
    when(tagProfile.isTagContractEnabled(usage.getProductId())).thenReturn(true);
    when(contractsApi.getContract(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    ExternalServiceException e =
        assertThrows(
            ExternalServiceException.class,
            () -> {
              controller.getContractCoverage(usage);
            });
    assertEquals(ErrorCode.CONTRACTS_SERVICE_ERROR, e.getCode());
    assertEquals(String.format("No contract info found for usage! %s", usage), e.getMessage());
  }

  private BillableUsage defaultUsage() {
    return new BillableUsage()
        .withOrgId("org123")
        .withProductId("product1")
        .withValue(24.5)
        .withUsage(Usage.PRODUCTION)
        .withAccountNumber("account123")
        .withBillingAccountId("ba123")
        .withSla(Sla.PREMIUM)
        .withBillingProvider(BillingProvider.AWS)
        .withUom(Uom.CORES)
        .withSnapshotDate(clock.now());
  }

  private Contract contractFromUsage(BillableUsage usage) {
    return new Contract()
        .orgId(usage.getOrgId())
        .productId(usage.getProductId())
        .endDate(OffsetDateTime.now())
        .billingAccountId(usage.getBillingAccountId())
        .billingProvider(usage.getBillingProvider().value())
        .startDate(clock.startOfMonth(usage.getSnapshotDate()));
  }
}
