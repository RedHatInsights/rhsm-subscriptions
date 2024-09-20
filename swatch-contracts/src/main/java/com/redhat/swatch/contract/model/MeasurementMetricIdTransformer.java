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

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.contract.exception.ContractsException;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.ContractMetricEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;

@Slf4j
@ApplicationScoped
public class MeasurementMetricIdTransformer {

  public static final String MEASUREMENT_TYPE_DEFAULT = "PHYSICAL";

  /**
   * Maps all incoming metrics from cloud provider-specific formats/UOMs into the swatch UOM value.
   *
   * <p>For example, 100 four_vcpu_hours (AWS dimension) will be transformed into 400 CORES.
   *
   * @param contract entity w/ measurements in the cloud-provider units
   */
  @Named("subscriptionMeasurementsFromContract")
  public Map<SubscriptionMeasurementKey, Double> mapContractMetricsToSubscriptionMeasurements(
      ContractEntity contract) {
    if (contract.getOffering() == null) {
      log.warn(
          "Offering is not set for contract {}. Skipping the translation of contract metrics",
          contract);
      return Map.of();
    }

    if (contract.getBillingProvider() == null) {
      // in absence of billing provider, we map all the contract metrics as it is.
      return contract.getMetrics().stream()
          .collect(
              Collectors.toMap(
                  m -> new SubscriptionMeasurementKey(m.getMetricId(), MEASUREMENT_TYPE_DEFAULT),
                  ContractMetricEntity::getValue));
    }

    return translateContractMetricToSubscriptionMeasurements(contract);
  }

  /**
   * Resolves all conflicting metrics from cloud provider-specific formats.
   *
   * <p>For example, 100 four_vcpu_hours (AWS dimension) will be the metricId for contract metrics
   * While the transformed unit will be stored as a SWATCH UOM into 400 CORES in the subscriptions'
   * metrics measurements.
   *
   * @param contract contract w/ measurements in the cloud-provider-specific dimensions
   */
  public void resolveConflictingMetrics(ContractEntity contract) {
    if (contract.getOffering() == null) {
      // do nothing if the sku was missing from the contract.
      return;
    }

    // resolve contract measurements with the correct metrics from sync service
    // this will keep subscriptions and contract metrics consistent with its dimension to SWATCH UOM
    log.debug(
        "Resolving conflicting metrics between subscription & contract for {}",
        contract.getOrgId());
    try {
      for (String productTag : contract.getOffering().getProductTags()) {
        var metrics =
            Variant.findByTag(productTag).orElseThrow().getSubscription().getMetrics().stream()
                .map(m -> getBillingProviderMetricIdentifier(contract.getBillingProvider(), m))
                .collect(Collectors.toSet());
        contract
            .getMetrics()
            .removeIf(
                metric -> {
                  if (!metrics.contains(metric.getMetricId())) {
                    log.warn(
                        "Removing unsupported metric '{}' from contract using the product '{}'. "
                            + "List of supported metrics in the product are: {}",
                        metric.getMetricId(),
                        productTag,
                        metrics);

                    return true;
                  }

                  return false;
                });
      }
    } catch (ProcessingException | NoSuchElementException e) {
      log.error("Error resolving dimensions for contract metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }
  }

  private Map<SubscriptionMeasurementKey, Double> translateContractMetricToSubscriptionMeasurements(
      ContractEntity contract) {
    Map<SubscriptionMeasurementKey, Double> subscriptionMetrics = new HashMap<>();
    try {
      for (String tag : contract.getOffering().getProductTags()) {
        var supportedMetrics = Variant.findByTag(tag).orElseThrow().getSubscription().getMetrics();
        for (var supportedMetric : supportedMetrics) {
          var contractMetric =
              contract.getMetric(
                  getBillingProviderMetricIdentifier(
                      contract.getBillingProvider(), supportedMetric));
          if (contractMetric != null) {
            double billingFactor =
                Optional.ofNullable(supportedMetric.getBillingFactor()).orElse(1.0);
            double metricValue = contractMetric.getValue() / billingFactor;
            subscriptionMetrics.put(
                new SubscriptionMeasurementKey(supportedMetric.getId(), MEASUREMENT_TYPE_DEFAULT),
                metricValue);
          }
        }
      }
    } catch (ProcessingException | NoSuchElementException e) {
      log.error("Error looking up dimension for metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }

    return subscriptionMetrics;
  }

  private String getBillingProviderMetricIdentifier(String billingProvider, Metric metric) {
    return switch (billingProvider) {
      case "aws" -> metric.getAwsDimension();
      case "azure" -> metric.getAzureDimension();
      case "red hat" -> metric.getRhmMetricId();
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported billing provider: %s", billingProvider));
    };
  }
}
