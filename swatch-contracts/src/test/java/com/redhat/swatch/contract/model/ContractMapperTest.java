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
import static org.mockito.Mockito.when;

import com.redhat.swatch.contract.openapi.model.Dimension;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.openapi.model.PartnerEntitlementContractCloudIdentifiers;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.OfferingRepository;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The fact that we are using @ExtendWith(MockitoExtension.class) prevents this test from launching
 * the entire application. Use @QuarkusTest to launch the entire application with the test if
 * required.
 */
@QuarkusTest
class ContractMapperTest {

  @Inject ContractMapper mapper;
  @InjectMock OfferingRepository offeringRepository;

  @Test
  void testMapContractEntityToSubscriptionEntity() {
    var offering = new OfferingEntity();
    offering.setSku("MCT123");
    when(offeringRepository.findById("MCT123")).thenReturn(offering);

    var subscription = new SubscriptionEntity();
    var metric = new ContractMetricEntity();
    metric.setMetricId("Cores");
    metric.setValue(42.0);

    var contract = new ContractEntity();
    contract.setSku("MCT123");
    contract.setMetrics(Set.of(metric));
    contract.setStartDate(OffsetDateTime.parse("2000-01-01T00:00Z"));
    contract.setEndDate(OffsetDateTime.parse("2020-01-01T00:00Z"));
    contract.setSubscriptionNumber("subscriptionNumber");
    contract.setBillingProvider("aws");
    contract.setBillingAccountId("12345678");
    contract.setOrgId("org123");
    contract.setProductId("rosa");

    mapper.mapContractEntityToSubscriptionEntity(subscription, contract);
    assertEquals(contract.getSubscriptionNumber(), subscription.getSubscriptionNumber());
    assertEquals(1, subscription.getSubscriptionMeasurements().size());
    assertEquals(contract.getSku(), subscription.getOffering().getSku());
    assertEquals(contract.getStartDate(), subscription.getStartDate());
    assertEquals(contract.getEndDate(), subscription.getEndDate());
    assertEquals(contract.getBillingProvider(), subscription.getBillingProvider().getValue());
    assertEquals(contract.getBillingAccountId(), subscription.getBillingAccountId());
    assertEquals(contract.getOrgId(), subscription.getOrgId());
    assertEquals(1, subscription.getSubscriptionProductIds().size());
    var productId = subscription.getSubscriptionProductIds().stream().findFirst().orElseThrow();
    assertEquals(contract.getProductId(), productId.getProductId());
    var measurement = subscription.getSubscriptionMeasurements().get(0);
    assertEquals(metric.getMetricId(), measurement.getMetricId());
    assertEquals(metric.getValue(), measurement.getValue());
    assertEquals("PHYSICAL", measurement.getMeasurementType());
  }

  @Test
  void testUmbContractToEntityWithDimensions() {
    String metricId = "vcpu_hours";

    PartnerEntitlementContract partnerContractFromUmb = new PartnerEntitlementContract();

    PartnerEntitlementContractCloudIdentifiers cloudIdentifiers =
        new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers
        .azureResourceId("azure_resource_id_placeholder")
        .azureOfferId("azure_offer_id_placeholder")
        .planId("vcpu-hours")
        .azureTenantId("azure_tenant_id_placeholder")
        .partner("azure_marketplace");

    var dimension = new Dimension();

    dimension.dimensionName(metricId);
    dimension.dimensionValue("0");
    List<Dimension> dimensions = List.of(dimension);

    partnerContractFromUmb
        .action("contract-updated")
        .currentDimensions(dimensions)
        .cloudIdentifiers(cloudIdentifiers);

    var entity = mapper.partnerContractToContractEntity(partnerContractFromUmb);

    var expectedMetricEntity = ContractMetricEntity.builder().metricId(metricId).value(0.0).build();

    assertEquals(Set.of(expectedMetricEntity), entity.getMetrics());
  }
}
