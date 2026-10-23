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
package com.redhat.swatch.contract.service;

import com.redhat.swatch.contract.model.MeasurementMetricIdTransformer;
import com.redhat.swatch.contract.model.SubscriptionContractDtoMapper;
import com.redhat.swatch.contract.openapi.model.Contract;
import com.redhat.swatch.contract.openapi.model.Metric;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.panache.Specification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class SubscriptionContractService {

  private final SubscriptionRepository subscriptionRepository;
  private final SubscriptionContractDtoMapper subscriptionContractDtoMapper;
  private final MeasurementMetricIdTransformer measurementMetricIdTransformer;

  /**
   * Build specifications based on provided parameters if not null and use them to query the
   * subscriptions tables.
   *
   * @param orgId the org ID.
   * @param productTag the product tag.
   * @param billingProvider the billing provider.
   * @param billingAccountId the billing account ID.
   * @param vendorProductCode the vendor product code.
   * @param timestamp return only records active at this timestamp.
   * @return List&lt;Contract&gt; the list of contracts.
   */
  @Transactional
  public List<Contract> getContracts(
      String orgId,
      String productTag,
      String billingProvider,
      String billingAccountId,
      String vendorProductCode,
      OffsetDateTime timestamp) {

    // The subscriptions tables also hold non-contract (for example annual) subscriptions, which the
    // contracts tables never contain. Restrict to contract-backed subscriptions so that both modes
    // return the same records.
    Specification<SubscriptionEntity> specification =
        SubscriptionEntity.orgIdEquals(orgId).and(SubscriptionEntity.hasBillingProviderId());

    if (productTag != null) {
      specification = specification.and(SubscriptionEntity.productTagEquals(productTag));
    }
    if (billingProvider != null) {
      specification =
          specification.and(
              SubscriptionEntity.billingProviderEquals(
                  BillingProvider.fromString(billingProvider)));
    }
    if (billingAccountId != null) {
      specification =
          specification.and(SubscriptionEntity.billingAccountIdEquals(billingAccountId));
    }
    if (vendorProductCode != null) {
      specification =
          specification.and(SubscriptionEntity.vendorProductCodeEquals(vendorProductCode));
    }
    if (timestamp != null) {
      specification = specification.and(SubscriptionEntity.activeOn(timestamp));
    }

    return subscriptionRepository.findBySpecification(specification).stream()
        .map(this::toContract)
        .toList();
  }

  private Contract toContract(SubscriptionEntity subscription) {
    Contract contract = subscriptionContractDtoMapper.subscriptionEntityToDto(subscription);
    contract.setMetrics(toContractMetrics(subscription));
    return contract;
  }

  private List<Metric> toContractMetrics(SubscriptionEntity subscription) {
    Map<String, Double> measurements =
        measurementMetricIdTransformer.mapSubscriptionMeasurementsToContractMetrics(subscription);
    // Metric.value is an int32 in the API contract, so the stored double is truncated here the
    // same way the contracts-table mapper truncates ContractMetricEntity.value.
    return measurements.entrySet().stream()
        .map(
            entry ->
                new Metric().metricId(entry.getKey()).value((int) entry.getValue().doubleValue()))
        .toList();
  }
}
