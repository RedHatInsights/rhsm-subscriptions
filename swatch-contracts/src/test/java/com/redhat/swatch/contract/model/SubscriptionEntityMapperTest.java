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

import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SubscriptionEntityMapperTest {

  @Inject SubscriptionEntityMapper mapper;

  @Test
  void testMapContractEntityToSubscriptionEntity() {
    var expectedSku = "MCT123";
    var offering = new OfferingEntity();
    offering.setSku(expectedSku);
    offering.setProductTags(Set.of("rosa"));

    var subscription = new SubscriptionEntity();
    var metric = new ContractMetricEntity();
    metric.setMetricId("Cores");
    metric.setValue(42.0);

    var contract = new ContractEntity();
    contract.setOffering(offering);
    contract.setMetrics(Set.of(metric));
    contract.setStartDate(OffsetDateTime.parse("2000-01-01T00:00Z"));
    contract.setEndDate(OffsetDateTime.parse("2020-01-01T00:00Z"));
    contract.setSubscriptionNumber("subscriptionNumber");
    contract.setBillingProvider("aws");
    contract.setBillingAccountId("12345678");
    contract.setOrgId("org123");

    mapper.mapSubscriptionEntityFromContractEntity(subscription, contract);
    assertEquals(contract.getSubscriptionNumber(), subscription.getSubscriptionNumber());
    assertEquals(1, subscription.getSubscriptionMeasurements().size());
    assertEquals(contract.getOffering(), subscription.getOffering());
    assertEquals(contract.getEndDate(), subscription.getEndDate());
    assertEquals(contract.getBillingProvider(), subscription.getBillingProvider().getValue());
    assertEquals(contract.getBillingAccountId(), subscription.getBillingAccountId());
    assertEquals(contract.getOrgId(), subscription.getOrgId());
    var measurement = subscription.getSubscriptionMeasurements().get(0);
    assertEquals(metric.getMetricId(), measurement.getMetricId());
    assertEquals(metric.getValue(), measurement.getValue());
    assertEquals("PHYSICAL", measurement.getMeasurementType());
  }
}
