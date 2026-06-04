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

import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.hasBillingProviderId;
import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.orgIdEquals;
import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.productIdEquals;
import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.whereBillingAccountIdNullSafeEqual;
import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.whereBillingProviderNullSafeEqual;
import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.whereServiceLevelAnyOrEqualTo;
import static com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository.whereUsageAnyOrEqualTo;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository;
import com.redhat.swatch.panache.Specification;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class SubscriptionCapacityService {

  private final SubscriptionCapacityViewRepository capacityRepository;

  @Inject
  public SubscriptionCapacityService(SubscriptionCapacityViewRepository capacityRepository) {
    this.capacityRepository = capacityRepository;
  }

  @Transactional
  public Map<TallySnapshot, List<SubscriptionCapacityView>> getCapacityForTallySummary(
      TallySummary tallySummary) {
    var capacities = new HashMap<TallySnapshot, List<SubscriptionCapacityView>>();
    for (var tallySnapshot : tallySummary.getTallySnapshots()) {
      try (Stream<SubscriptionCapacityView> resultStream =
          streamCapacities(tallySummary, tallySnapshot)) {
        resultStream.forEach(
            capacity ->
                capacities.computeIfAbsent(tallySnapshot, k -> new ArrayList<>()).add(capacity));
      }
    }

    return capacities;
  }

  private Stream<SubscriptionCapacityView> streamCapacities(
      TallySummary tallySummary, TallySnapshot tallySnapshot) {
    Specification<SubscriptionCapacityView> criteria =
        buildSpecificationForSnapshot(tallySummary.getOrgId(), tallySnapshot);
    return capacityRepository.streamBy(criteria);
  }

  private Specification<SubscriptionCapacityView> buildSpecificationForSnapshot(
      String orgId, TallySnapshot snapshot) {
    ProductId productId = ProductId.fromString(snapshot.getProductId());
    BillingProvider billingProvider = mapBillingProvider(snapshot.getBillingProvider());
    ServiceLevel serviceLevel = mapServiceLevel(snapshot.getSla());
    Usage usage = mapUsage(snapshot.getUsage());

    Specification<SubscriptionCapacityView> spec = Specification.where(orgIdEquals(orgId));
    spec = spec.and(productIdEquals(productId));
    if (productId.isOnDemand()) {
      spec = spec.and(hasBillingProviderId());
    }
    spec = spec.and(whereServiceLevelAnyOrEqualTo(serviceLevel));
    spec = spec.and(whereUsageAnyOrEqualTo(usage));
    if (productId.isPayg()) {
      spec = spec.and(whereBillingProviderNullSafeEqual(billingProvider));
      spec = spec.and(whereBillingAccountIdNullSafeEqual(snapshot.getBillingAccountId()));
    }
    return spec;
  }

  private ServiceLevel mapServiceLevel(TallySnapshot.Sla sla) {
    if (sla == null) {
      return ServiceLevel._ANY;
    }
    return ServiceLevel.fromString(sla.toString());
  }

  private Usage mapUsage(TallySnapshot.Usage usage) {
    if (usage == null) {
      return Usage._ANY;
    }
    return Usage.fromString(usage.toString());
  }

  private BillingProvider mapBillingProvider(TallySnapshot.BillingProvider billingProvider) {
    if (billingProvider == null) {
      return null;
    }

    try {
      return BillingProvider.valueOf(billingProvider.name());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
