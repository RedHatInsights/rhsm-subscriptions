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
package com.redhat.swatch.contract.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1EntitlementDates;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PurchaseV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.contract.repository.ContractEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The fact that we are using @ExtendWith(MockitoExtension.class) prevents this test from launching
 * the entire application. Use @QuarkusTest to launch the entire application with the test if
 * required.
 */
@QuarkusTest
class ContractEntityMapperTest {

  private static final String V_CPU_HOURS_METRIC = "vcpu_hours";
  private static final String V_CPU_HOURS_PLAN = "vcpu-hours";

  @Inject ContractEntityMapper mapper;

  private PartnerEntitlementV1 entitlement;
  private SaasContractV1 saasContract;

  @Test
  void testEntitlementToEntityWithDimensions() {
    double expectedMetricValue = 0.0;

    givenAzureEntitlement();
    givenContract(expectedMetricValue);

    var entity = whenMapEntitlementToContractEntity();

    assertMetricValueIs(entity, expectedMetricValue);
  }

  @Test
  void testEntitlementToEntityTakesContractStartAndEndDate() {
    givenAzureEntitlement();
    // expired contract
    givenContract(0.0, "2020-01-01T00:00Z", "2021-05-01T00:00Z");
    // active contract
    givenContract(10.0, "2021-05-01T00:00Z", null);

    var entity = whenMapEntitlementToContractEntity();

    // from active contract
    assertMetricValueIs(entity, 10.0);
  }

  @Test
  void testEntitlementToEntityTakesPartnerEntitlementDatesWhenNoContract() {
    givenAzureEntitlement();
    givenEntitlementDates("2022-01-01T00:00Z", "2023-01-01T00:00Z");

    var entity = whenMapEntitlementToContractEntity();

    assertEquals(entitlement.getEntitlementDates().getStartDate(), entity.getStartDate());
    assertEquals(entitlement.getEntitlementDates().getEndDate(), entity.getEndDate());
  }

  @Test
  void testMapEntitlementToContractEntityBillingProviderIdWithoutClientId() {
    givenAzureEntitlement("theAzureResourceId", "theAzureCustomerId", "theAzureOfferId", null);
    givenContractWithPlanId("thePlanId");

    var entity = whenMapEntitlementToContractEntity();

    assertEquals(
        "theAzureResourceId;thePlanId;theAzureOfferId;theAzureCustomerId;",
        entity.getBillingProviderId());
  }

  @Test
  void testMapEntitlementToContractEntityBillingProviderIdWithClientId() {
    givenAzureEntitlement(
        "theAzureResourceId", "theAzureCustomerId", "theAzureOfferId", "theClientId");
    givenContractWithPlanId("thePlanId");

    var entity = whenMapEntitlementToContractEntity();

    assertEquals(
        "theAzureResourceId;thePlanId;theAzureOfferId;theAzureCustomerId;theClientId",
        entity.getBillingProviderId());
  }

  private void givenContractWithPlanId(String planId) {
    var contract = givenContract(0, null, null);
    contract.setPlanId(planId);
  }

  private void givenContract(double metricValue) {
    givenContract(metricValue, null, null);
  }

  private void givenEntitlementDates(String startDate, String endDate) {
    var entitlementDates = new PartnerEntitlementV1EntitlementDates();
    entitlementDates.setStartDate(OffsetDateTime.parse(startDate));
    entitlementDates.setEndDate(OffsetDateTime.parse(endDate));
    entitlement.setEntitlementDates(entitlementDates);
  }

  private SaasContractV1 givenContract(
      double metricValue, @Nullable String startDate, @Nullable String endDate) {
    saasContract = new SaasContractV1();
    saasContract.planId(V_CPU_HOURS_PLAN);
    saasContract.setStartDate(OffsetDateTime.now());
    saasContract.addDimensionsItem(
        new DimensionV1().name(V_CPU_HOURS_METRIC).value(Double.toString(metricValue)));
    Optional.ofNullable(startDate).ifPresent(s -> saasContract.startDate(OffsetDateTime.parse(s)));
    Optional.ofNullable(endDate).ifPresent(s -> saasContract.endDate(OffsetDateTime.parse(s)));
    saasContract.endDate(OffsetDateTime.parse("2021-05-01T00:00Z"));
    entitlement.getPurchase().addContractsItem(saasContract);
    return saasContract;
  }

  private void givenAzureEntitlement(
      String azureResourceId, String azureCustomerId, String vendorProductCode, String clientId) {
    entitlement = new PartnerEntitlementV1();
    entitlement.setPurchase(new PurchaseV1());
    entitlement.getPurchase().setContracts(new ArrayList<>());
    entitlement.getPurchase().azureResourceId(azureResourceId);
    entitlement.getPurchase().setVendorProductCode(vendorProductCode);
    entitlement.setPartnerIdentities(new PartnerIdentityV1());
    entitlement.getPartnerIdentities().azureCustomerId(azureCustomerId);
    entitlement.getPartnerIdentities().clientId(clientId);
    entitlement.sourcePartner(ContractSourcePartnerEnum.AZURE.getCode());
  }

  private void givenAzureEntitlement() {
    givenAzureEntitlement(
        "azure_resource_id_placeholder",
        "azure_customer_id_placeholder",
        "azure_vendor_code",
        "client_id");
  }

  private ContractEntity whenMapEntitlementToContractEntity() {
    return mapper.mapEntitlementToContractEntity(entitlement, saasContract);
  }

  private void assertMetricValueIs(ContractEntity entity, double expectedMetricValue) {
    assertNotNull(entity);
    assertNotNull(entity.getMetrics());
    var metric = entity.getMetric(V_CPU_HOURS_METRIC);
    assertNotNull(metric);
    assertEquals(expectedMetricValue, metric.getValue());
  }
}
