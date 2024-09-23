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

import static com.redhat.swatch.contract.model.MeasurementMetricIdTransformer.MEASUREMENT_TYPE_DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementKey;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeasurementMetricIdTransformerTest {
  @InjectMocks MeasurementMetricIdTransformer transformer;

  @Test
  void testMapsAwsDimensionToMetricId() throws RuntimeException {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    contract.addMetric(contractMetric("four_vcpu_hour", 100.0));
    contract.addMetric(contractMetric("control_plane", 0));
    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("rosa"));

    var measurements = transformer.mapContractMetricsToSubscriptionMeasurements(contract);
    assertEquals(
        Set.of("Cores", "Instance-hours"),
        measurements.keySet().stream()
            .map(SubscriptionMeasurementKey::getMetricId)
            .collect(Collectors.toSet()));
    assertEquals(
        400.0,
        measurements.entrySet().stream()
            .filter(e -> e.getKey().getMetricId().startsWith("Cores"))
            .findFirst()
            .orElseThrow()
            .getValue());
  }

  @Test
  void testNoMappingAttemptedForMissingBillingProvider() throws RuntimeException {
    var contract = new ContractEntity();
    contract.addMetric(contractMetric("bar", 1.0));
    contract.addMetric(contractMetric("boo", 2.0));
    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("hello"));

    var measurements = transformer.mapContractMetricsToSubscriptionMeasurements(contract);
    assertEquals(
        1.0, measurements.get(new SubscriptionMeasurementKey("bar", MEASUREMENT_TYPE_DEFAULT)));
    assertEquals(
        2.0, measurements.get(new SubscriptionMeasurementKey("boo", MEASUREMENT_TYPE_DEFAULT)));
  }

  @Test
  void testUnsupportedMetricIdAreRemoved() throws RuntimeException {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    contract.addMetric(contractMetric("control_plane", 100.0));
    contract.addMetric(contractMetric("four_vcpu_hour", 100.0));
    contract.addMetric(contractMetric("Unknown", 100.0));

    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("rosa"));

    var measurements = transformer.mapContractMetricsToSubscriptionMeasurements(contract);
    assertEquals(
        Set.of("Instance-hours", "Cores"),
        measurements.keySet().stream()
            .map(SubscriptionMeasurementKey::getMetricId)
            .collect(Collectors.toSet()));
  }

  @Test
  void testUnsupportedDimensionsAreRemovedFromContracts() throws RuntimeException {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    var instanceHours = new ContractMetricEntity();
    instanceHours.setValue(100.0);
    instanceHours.setMetricId("control_plane");

    var cores = new ContractMetricEntity();
    cores.setMetricId("four_vcpu_hour");
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

    transformer.resolveConflictingMetrics(contract);
    assertEquals(
        Set.of("control_plane", "four_vcpu_hour"),
        contract.getMetrics().stream()
            .map(ContractMetricEntity::getMetricId)
            .collect(Collectors.toSet()));
  }

  private ContractMetricEntity contractMetric(String metricId, double value) {
    return ContractMetricEntity.builder().metricId(metricId).value(value).build();
  }
}
