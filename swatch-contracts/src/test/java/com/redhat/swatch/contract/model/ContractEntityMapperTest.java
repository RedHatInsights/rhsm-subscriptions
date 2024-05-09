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

import com.redhat.swatch.clients.rh.partner.gateway.api.model.DimensionV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerEntitlementV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PartnerIdentityV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.PurchaseV1;
import com.redhat.swatch.clients.rh.partner.gateway.api.model.SaasContractV1;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The fact that we are using @ExtendWith(MockitoExtension.class) prevents this test from launching
 * the entire application. Use @QuarkusTest to launch the entire application with the test if
 * required.
 */
@QuarkusTest
class ContractEntityMapperTest {

  @Inject ContractEntityMapper mapper;

  @Test
  void testEntitlementToEntityWithDimensions() {
    String metricId = "vcpu_hours";

    var contract = new SaasContractV1();
    var entitlement = new PartnerEntitlementV1();
    entitlement.setPurchase(new PurchaseV1());
    entitlement.getPurchase().addContractsItem(contract);
    entitlement.getPurchase().azureResourceId("azure_resource_id_placeholder");
    entitlement.setPartnerIdentities(new PartnerIdentityV1());
    entitlement.getPartnerIdentities().azureCustomerId("azure_customer_id_placeholder");
    entitlement.sourcePartner(PartnerEntitlementV1.SourcePartnerEnum.AZURE_MARKETPLACE);
    contract.planId("vcpu-hours");

    contract.addDimensionsItem(new DimensionV1().name(metricId).value("0"));

    var entity = mapper.mapEntitlementToContractEntity(entitlement);

    var expectedMetricEntity = ContractMetricEntity.builder().metricId(metricId).value(0.0).build();
    assertEquals(Set.of(expectedMetricEntity), entity.getMetrics());
  }
}
