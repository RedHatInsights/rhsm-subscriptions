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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.Metric;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementMetricIdTransformerTest {
  @Mock InternalSubscriptionsApi internalSubscriptionsApi;
  @InjectMocks MeasurementMetricIdTransformer transformer;

  @Test
  void testMapsAwsDimensionToMetricId() throws ApiException, RuntimeException {
    var subscription = new SubscriptionEntity();
    subscription.setBillingProvider(BillingProvider.AWS);
    var measurement1 = new SubscriptionMeasurementEntity();
    measurement1.setMetricId("bar");
    var measurement2 = new SubscriptionMeasurementEntity();
    measurement2.setMetricId("foo");
    measurement2.setValue(100.0);
    subscription.addSubscriptionMeasurement(measurement1);
    subscription.addSubscriptionMeasurement(measurement2);
    subscription.setOffering(new OfferingEntity());
    subscription.getOffering().setProductTags(Set.of("hello"));

    when(internalSubscriptionsApi.getMetrics("hello"))
        .thenReturn(
            List.of(
                new Metric().uom("foo1").awsDimension("foo").billingFactor(0.25),
                new Metric().uom("bar2").awsDimension("bar").billingFactor(1.0)));
    transformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    assertEquals(
        List.of("bar2", "foo1"),
        subscription.getSubscriptionMeasurements().stream()
            .map(SubscriptionMeasurementEntity::getMetricId)
            .collect(Collectors.toList()));
    assertEquals(
        400.0,
        subscription.getSubscriptionMeasurements().stream()
            .filter(m -> m.getMetricId().startsWith("foo"))
            .findFirst()
            .orElseThrow()
            .getValue());
  }

  @Test
  void testNoMappingAttemptedForMissingBillingProvider() throws RuntimeException {
    var subscription = new SubscriptionEntity();
    var measurement1 = new SubscriptionMeasurementEntity();
    measurement1.setMetricId("bar");
    var measurement2 = new SubscriptionMeasurementEntity();
    measurement2.setMetricId("foo");
    subscription.addSubscriptionMeasurement(measurement1);
    subscription.addSubscriptionMeasurement(measurement2);
    subscription.setOffering(new OfferingEntity());
    subscription.getOffering().setProductTags(Set.of("hello"));

    transformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    verifyNoInteractions(internalSubscriptionsApi);
  }

  @Test
  void testUnsupportedMetricIdAreRemoved() throws ApiException, RuntimeException {
    var subscription = new SubscriptionEntity();
    subscription.setBillingProvider(BillingProvider.AWS);
    var instanceHours = new SubscriptionMeasurementEntity();
    instanceHours.setValue(100.0);
    instanceHours.setMetricId("control_plane_0");

    var cores = new SubscriptionMeasurementEntity();
    cores.setMetricId("four_vcpu_0");
    cores.setValue(100.0);

    var unknown = new SubscriptionMeasurementEntity();
    unknown.setMetricId("Unknown");
    unknown.setValue(100.0);

    subscription.addSubscriptionMeasurement(instanceHours);
    subscription.addSubscriptionMeasurement(unknown);
    subscription.addSubscriptionMeasurement(cores);

    subscription.setOffering(new OfferingEntity());
    subscription.getOffering().setProductTags(Set.of("rosa"));

    when(internalSubscriptionsApi.getMetrics("rosa"))
        .thenReturn(
            List.of(
                new Metric().uom("Instance-hours").awsDimension("control_plane_0"),
                new Metric().uom("Cores").awsDimension("four_vcpu_0").billingFactor(0.25)));
    transformer.translateContractMetricIdsToSubscriptionMetricIds(subscription);
    assertEquals(
        List.of("Instance-hours", "Cores"),
        subscription.getSubscriptionMeasurements().stream()
            .map(SubscriptionMeasurementEntity::getMetricId)
            .collect(Collectors.toList()));
  }

  @Test
  void testUnsupportedDimensionsAreRemovedFromContracts() throws ApiException, RuntimeException {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    var instanceHours = new ContractMetricEntity();
    instanceHours.setValue(100.0);
    instanceHours.setMetricId("control_plane_0");

    var cores = new ContractMetricEntity();
    cores.setMetricId("four_vcpu_0");
    cores.setValue(100.0);

    var unknown = new ContractMetricEntity();
    unknown.setMetricId("Unknown");
    unknown.setValue(100.0);

    var wrongDimension = new ContractMetricEntity();
    wrongDimension.setMetricId("storage_gb");
    wrongDimension.setValue(100);

    contract.addMetric(instanceHours);
    contract.addMetric(cores);
    contract.addMetric(wrongDimension);
    contract.addMetric(unknown);

    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("rosa"));

    when(internalSubscriptionsApi.getMetrics("rosa"))
        .thenReturn(
            List.of(
                new Metric().uom("Instance-hours").awsDimension("control_plane_0"),
                new Metric().uom("Cores").awsDimension("four_vcpu_0").billingFactor(0.25)));
    transformer.resolveConflictingMetrics(contract);
    assertEquals(
        Set.of("control_plane_0", "four_vcpu_0"),
        contract.getMetrics().stream()
            .map(ContractMetricEntity::getMetricId)
            .collect(Collectors.toSet()));
  }
}
