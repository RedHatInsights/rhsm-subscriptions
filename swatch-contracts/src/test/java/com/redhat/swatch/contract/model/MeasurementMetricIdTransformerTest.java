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
  void testUnsupportedDimensionsForBillingProviderAreRemovedFromContracts()
      throws RuntimeException {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AZURE.getValue());
    contract.addMetric(contractMetric("vCPU_hours", 100));

    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("rhel-for-x86-els-payg"));

    transformer.resolveConflictingMetrics(contract);
    assertEquals(
        Set.of(),
        contract.getMetrics().stream()
            .map(ContractMetricEntity::getMetricId)
            .collect(Collectors.toSet()),
        "All metrics should be removed when billing provider is unsupported for the product tag");
  }

  @Test
  void testUnsupportedMetricsAreRemovedFromContractsWhenProductTagNotFound()
      throws RuntimeException {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    contract.addMetric(contractMetric("MCT4545", 100));
    contract.setOffering(new OfferingEntity());

    transformer.resolveConflictingMetrics(contract);
    assertEquals(
        Set.of(),
        contract.getMetrics().stream()
            .map(ContractMetricEntity::getMetricId)
            .collect(Collectors.toSet()),
        "All metrics should be removed when product tags are empty");
    assertEquals(
        0,
        contract.getMetrics().size(),
        "All metrics should be removed when product tags are empty");
  }

  @Test
  void testMetricsRemovedWhenProductTagsAreNull() {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AZURE.getValue());
    contract.addMetric(contractMetric("vcpu_hours", 100));
    contract.addMetric(contractMetric("memory_gb", 64));
    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(null); // Explicit null

    transformer.resolveConflictingMetrics(contract);

    assertEquals(
        0,
        contract.getMetrics().size(),
        "All metrics should be removed when product tags are null");
  }

  @Test
  void testMetricsRemovedWhenProductTagsAreEmpty() {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    contract.addMetric(contractMetric("four_vcpu_hour", 200));
    contract.addMetric(contractMetric("storage_gb", 500));
    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of());

    transformer.resolveConflictingMetrics(contract);

    assertEquals(
        0,
        contract.getMetrics().size(),
        "All metrics should be removed when product tags are empty");
  }

  @Test
  void testAllUnsupportedMetricsAreRemoved() {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("rosa"));
    // Add only unsupported metrics
    contract.addMetric(contractMetric("invalid_metric_1", 10));
    contract.addMetric(contractMetric("invalid_metric_2", 20));

    transformer.resolveConflictingMetrics(contract);

    assertEquals(0, contract.getMetrics().size(), "All unsupported metrics should be removed");
  }

  @Test
  void testMixedSupportedAndUnsupportedMetrics() {
    var contract = new ContractEntity();
    contract.setBillingProvider(BillingProvider.AWS.getValue());
    contract.setOffering(new OfferingEntity());
    contract.getOffering().setProductTags(Set.of("rosa"));
    // Mix of supported and unsupported metrics
    contract.addMetric(contractMetric("four_vcpu_hour", 100));
    contract.addMetric(contractMetric("unsupported_metric", 50));
    contract.addMetric(contractMetric("control_plane", 1));
    contract.addMetric(contractMetric("another_invalid", 25));

    transformer.resolveConflictingMetrics(contract);

    assertEquals(
        Set.of("four_vcpu_hour", "control_plane"),
        contract.getMetrics().stream()
            .map(ContractMetricEntity::getMetricId)
            .collect(Collectors.toSet()),
        "Only supported metrics should remain");
  }

  private ContractMetricEntity contractMetric(String metricId, double value) {
    return ContractMetricEntity.builder().metricId(metricId).value(value).build();
  }
}
