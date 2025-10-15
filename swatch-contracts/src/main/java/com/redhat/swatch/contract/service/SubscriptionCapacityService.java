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
import java.util.Objects;
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

  /** Retrieves capacity data for a batch of tally summaries. */
  @Transactional
  public Map<TallySnapshot, List<SubscriptionCapacityView>> getCapacityForTallySummaries(
      List<TallySummary> tallyMessages) {
    if (tallyMessages == null || tallyMessages.isEmpty()) {
      return Map.of();
    }

    // Execute query and filter in stream
    try (Stream<SubscriptionCapacityView> resultStream = streamCapacities(tallyMessages)) {
      var capacities = new HashMap<TallySnapshot, List<SubscriptionCapacityView>>();
      resultStream.forEach(
          capacity -> {
            findTallySnapshotsForCapacity(tallyMessages, capacity)
                .forEach(
                    matchedSnapshot -> {
                      List<SubscriptionCapacityView> capacitiesBySnapshot =
                          capacities.get(matchedSnapshot);
                      if (capacitiesBySnapshot == null) {
                        capacitiesBySnapshot = new ArrayList<>();
                      }

                      capacitiesBySnapshot.add(capacity);
                      capacities.put(matchedSnapshot, capacitiesBySnapshot);
                    });
          });
      log.debug("Query returned {} capacity records", capacities.size());

      return capacities;
    }
  }

  private Stream<SubscriptionCapacityView> streamCapacities(List<TallySummary> tallyMessages) {
    // Collect all snapshots with their specifications and org IDs
    List<Specification<SubscriptionCapacityView>> specifications = new ArrayList<>();
    for (var tallySummary : tallyMessages) {
      for (var snapshot : tallySummary.getTallySnapshots()) {
        Specification<SubscriptionCapacityView> spec =
            buildSpecificationForSnapshot(tallySummary.getOrgId(), snapshot);
        specifications.add(spec);
      }
    }

    // Wrap each specification with cb.and() before combining with OR
    Specification<SubscriptionCapacityView> criteria =
        specifications.stream()
            .map(
                spec ->
                    (Specification<SubscriptionCapacityView>)
                        (root, query, cb) -> cb.and(spec.toPredicate(root, query, cb)))
            .reduce(Specification::or)
            .orElse(null);

    // Execute single query with combined specification using repository
    return capacityRepository.streamBy(criteria);
  }

  /** Builds a specification for a single snapshot using existing repository methods. */
  private Specification<SubscriptionCapacityView> buildSpecificationForSnapshot(
      String orgId, TallySnapshot snapshot) {
    ProductId productId = ProductId.fromString(snapshot.getProductId());
    BillingProvider billingProvider = mapBillingProvider(snapshot.getBillingProvider());

    return SubscriptionCapacityViewRepository.buildSearchSpecification(
        orgId,
        productId,
        null, // category
        ServiceLevel._ANY,
        Usage._ANY,
        productId.isPayg() ? billingProvider : BillingProvider._ANY,
        productId.isPayg() ? snapshot.getBillingAccountId() : null,
        null // metricId
        );
  }

  private List<TallySnapshot> findTallySnapshotsForCapacity(
      List<TallySummary> tallyMessages, SubscriptionCapacityView capacity) {
    List<TallySnapshot> matchedSnapshots = new ArrayList<>();
    for (var tallySummary : tallyMessages) {
      for (var snapshot : tallySummary.getTallySnapshots()) {
        if (matchesSnapshot(capacity, tallySummary.getOrgId(), snapshot)) {
          matchedSnapshots.add(snapshot);
        }
      }
    }

    return matchedSnapshots;
  }

  /** Matches capacity to snapshot for result grouping (more precise than just product tag). */
  private boolean matchesSnapshot(
      SubscriptionCapacityView capacity, String orgId, TallySnapshot snapshot) {
    if (!Objects.equals(capacity.getOrgId(), orgId)) {
      return false;
    }

    ProductId productId = ProductId.fromString(snapshot.getProductId());
    if (!Objects.equals(capacity.getProductTag(), productId.getValue())) {
      return false;
    }

    // if product is payg, snapshot needs to match by billing provider and billing account ID too
    if (productId.isPayg()) {
      BillingProvider billingProvider = mapBillingProvider(snapshot.getBillingProvider());
      if (!Objects.equals(capacity.getBillingProvider(), billingProvider)) {
        return false;
      }

      if (!Objects.equals(capacity.getBillingAccountId(), snapshot.getBillingAccountId())) {
        return false;
      }
    }

    return true;
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
