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
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ProcessingException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class MeasurementMetricIdTransformer {

  /**
   * Maps all incoming metrics from cloud provider-specific formats/UOMs into the swatch UOM value.
   *
   * <p>For example, 100 four_vcpu_hours (AWS dimension) will be transformed into 400 CORES.
   *
   * @param subscription subscription w/ measurements in the cloud-provider units
   */
  public void translateContractMetricIdsToSubscriptionMetricIds(SubscriptionEntity subscription) {
    if (subscription.getOffering() == null) {
      log.warn(
          "Offering is not set for subscription {}. Skipping the translation of contract metrics",
          subscription);
      return;
    }

    try {
      if (subscription.getBillingProvider() == BillingProvider.AWS
          || subscription.getBillingProvider() == BillingProvider.AZURE) {
        for (String tag : subscription.getOffering().getProductTags()) {
          mapMetricsToSubscription(subscription, tag);
        }
      }
    } catch (ProcessingException e) {
      log.error("Error looking up dimension for metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }
  }

  private void mapMetricsToSubscription(SubscriptionEntity subscription, String productTag) {
    var metrics = Variant.getMetricsForTag(productTag).stream().toList();
    // the metricId currently set here is actually the aws/azure Dimension and get translated
    // to the
    // metric uom after calculation
    checkForUnsupportedMetrics(metrics, subscription);
    subscription
        .getSubscriptionMeasurements()
        .forEach(
            measurement ->
                metrics.stream()
                    .filter(
                        metric -> {
                          String marketplaceMetricId =
                              subscription.getBillingProvider() == BillingProvider.AWS
                                  ? metric.getAwsDimension()
                                  : metric.getAzureDimension();
                          return Objects.equals(marketplaceMetricId, measurement.getMetricId());
                        })
                    .findFirst()
                    .ifPresent(
                        metric -> {
                          if (metric.getBillingFactor() != null && measurement.getValue() != null) {
                            measurement.setValue(
                                measurement.getValue() / metric.getBillingFactor());
                          }
                          measurement.setMetricId(metric.getId());
                        }));
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
        var variantMetrics = Variant.getMetricsForTag(productTag).stream();
        Set<String> metrics;
        if (BillingProvider.AWS.getValue().equals(contract.getBillingProvider())) {
          metrics = variantMetrics.map(Metric::getAwsDimension).collect(Collectors.toSet());
        } else if (BillingProvider.AZURE.getValue().equals(contract.getBillingProvider())) {
          metrics = variantMetrics.map(Metric::getAzureDimension).collect(Collectors.toSet());
        } else {
          metrics = Set.of();
        }

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
    } catch (ProcessingException e) {
      log.error("Error resolving dimensions for contract metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }
  }

  private void checkForUnsupportedMetrics(List<Metric> metrics, SubscriptionEntity subscription) {
    if (!subscription.getSubscriptionMeasurements().isEmpty()) {
      // Will check for correct metrics before translating awsDimension to Metrics UOM
      log.debug("Checking for unsupported metricIds");
      Set<String> supportedSet;
      if (subscription.getBillingProvider() == BillingProvider.AWS) {
        supportedSet = metrics.stream().map(Metric::getAwsDimension).collect(Collectors.toSet());
      } else if (subscription.getBillingProvider() == BillingProvider.AZURE) {
        supportedSet = metrics.stream().map(Metric::getAzureDimension).collect(Collectors.toSet());
      } else {
        supportedSet = Set.of();
      }
      subscription
          .getSubscriptionMeasurements()
          .removeIf(m -> !supportedSet.contains(m.getMetricId()));
    }
  }
}
