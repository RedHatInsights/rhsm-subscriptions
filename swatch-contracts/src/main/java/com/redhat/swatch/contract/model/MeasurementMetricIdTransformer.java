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

import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.ApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi;
import com.redhat.swatch.contract.exception.ContractsException;
import com.redhat.swatch.contract.exception.ErrorCode;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionProductIdEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;
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
        var metrics = internalSubscriptionsApi.getTagMetrics(tag);
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
    } catch (ApiException e) {
      log.error("Error looking up dimension for metrics", e);
      throw new ContractsException(ErrorCode.UNHANDLED_EXCEPTION, e.getMessage());
    }
  }
}
