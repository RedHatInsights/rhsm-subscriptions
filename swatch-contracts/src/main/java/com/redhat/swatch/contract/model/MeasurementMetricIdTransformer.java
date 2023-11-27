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

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.Metric;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.contract.exception.ContractsException;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.ContractEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementEntity;
import com.redhat.swatch.contract.repository.SubscriptionProductIdEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Slf4j
@ApplicationScoped
public class MeasurementMetricIdTransformer {
  @RestClient @Inject InternalSubscriptionsApi internalSubscriptionsApi;

  /**
   * Maps all incoming metrics from cloud provider-specific formats/UOMs into the swatch UOM value.
   *
   * <p>For example, 100 four_vcpu_hours (AWS dimension) will be transformed into 400 CORES.
   *
   * @param subscription subscription w/ measurements in the cloud-provider units
   */
  public void translateContractMetricIdsToSubscriptionMetricIds(SubscriptionEntity subscription) {
    try {
      if (subscription.getBillingProvider() == BillingProvider.AWS) {
        var tag =
            subscription.getSubscriptionProductIds().stream()
                .findFirst()
                .map(SubscriptionProductIdEntity::getProductId)
                .orElseThrow();
        var metrics = internalSubscriptionsApi.getMetrics(tag);
        // the metricId currently set here is actually the aws Dimension and get translated to the
        // metric uom after calculation
        checkForUnsupportedMetrics(metrics, subscription);
        subscription
            .getSubscriptionMeasurements()
            .forEach(
                measurement ->
                    metrics.stream()
                        .filter(
                            metric ->
                                Objects.equals(metric.getAwsDimension(), measurement.getMetricId()))
                        .findFirst()
                        .ifPresent(
                            metric -> {
                              if (metric.getBillingFactor() != null
                                  && measurement.getValue() != null) {
                                measurement.setValue(
                                    measurement.getValue() / metric.getBillingFactor());
                              }
                              measurement.setMetricId(metric.getUom());
                            }));
      }
    } catch (ProcessingException | ApiException e) {
      log.error("Error looking up dimension for metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }
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
    // resolve contract measurements with the correct metrics from sync service
    // this will keep subscriptions and contract metrics consistent with its dimension to SWATCH UOM
    log.debug(
        "Resolving conflicting metrics between subscription & contract  for {}",
        contract.getOrgId());
    try {
      var metrics =
          internalSubscriptionsApi.getMetrics(contract.getProductId()).stream()
              .map(Metric::getAwsDimension)
              .collect(Collectors.toSet());
      contract.getMetrics().removeIf(metric -> !metrics.contains(metric.getMetricId()));

    } catch (ProcessingException | ApiException e) {
      log.error("Error resolving dimensions for contract metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }
  }

  private void checkForUnsupportedMetrics(List<Metric> metrics, SubscriptionEntity subscription) {
    if (!subscription.getSubscriptionMeasurements().isEmpty()) {
      // Will check for correct metrics before translating awsDimension to Metrics UOM
      log.debug("Checking for unsupported metricIds");
      var supportedSet = metrics.stream().map(Metric::getAwsDimension).collect(Collectors.toSet());
      List<SubscriptionMeasurementEntity> supportedMeasurements = new ArrayList<>();
      // compare set of metric.awsDimension to measurements.metricId (pre Transformation state)
      for (SubscriptionMeasurementEntity sub : subscription.getSubscriptionMeasurements()) {
        if (supportedSet.contains(sub.getMetricId())) {
          supportedMeasurements.add(sub);
        }
      }
      subscription.setSubscriptionMeasurements(supportedMeasurements);
    }
  }
}
