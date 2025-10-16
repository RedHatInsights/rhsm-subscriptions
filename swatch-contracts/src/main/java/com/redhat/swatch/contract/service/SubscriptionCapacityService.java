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
import java.util.List;
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

  /**
   * Retrieves capacity data for a batch of tally summaries using optimized Specification queries.
   * Reuses the existing buildSearchSpecification logic from SubscriptionCapacityViewRepository with
   * simple mappers to convert DTO enums to database enums.
   *
   * @param tallyMessages the batch of tally summary messages
   * @return list of all matching capacity data for the batch
   */
  public List<SubscriptionCapacityView> getCapacityForTallySummaries(
      List<TallySummary> tallyMessages) {
    if (tallyMessages == null || tallyMessages.isEmpty()) {
      return List.of();
    }

    log.debug("Building combined specification for {} tally messages", tallyMessages.size());

    // Build combined specification using OR logic for all tally snapshots
    Specification<SubscriptionCapacityView> combinedSpecification =
        tallyMessages.stream()
            .flatMap(
                tallySummary ->
                    tallySummary.getTallySnapshots().stream()
                        .map(
                            snapshot ->
                                buildSpecificationForSnapshot(tallySummary.getOrgId(), snapshot)))
            .filter(Objects::nonNull)
            .reduce(Specification::or)
            .orElse(Specification.where(null));

    // Execute single query with combined specification
    try (Stream<SubscriptionCapacityView> resultStream =
        capacityRepository.streamBy(combinedSpecification)) {
      List<SubscriptionCapacityView> results = resultStream.toList();
      log.debug("Batch capacity query returned {} records", results.size());
      return results;
    }
  }

  /**
   * Builds a specification for a single tally snapshot using the existing
   * SubscriptionCapacityViewRepository.buildSearchSpecification method. Uses simple mappers to
   * convert DTO enums to database enums.
   *
   * @param orgId the organization ID
   * @param snapshot the tally snapshot
   * @return specification for this snapshot
   */
  private Specification<SubscriptionCapacityView> buildSpecificationForSnapshot(
      String orgId, TallySnapshot snapshot) {
    try {
      ProductId productId = ProductId.fromString(snapshot.getProductId());

      // Reuse the exact same buildSearchSpecification logic as SubscriptionTableControllerV2
      return SubscriptionCapacityViewRepository.buildSearchSpecification(
          orgId,
          productId,
          null, // category - will be filtered in runtime when mapping results
          mapServiceLevel(snapshot.getSla()),
          mapUsage(snapshot.getUsage()),
          mapBillingProvider(snapshot.getBillingProvider()),
          snapshot.getBillingAccountId(),
          null // metricId - will be filtered in runtime when mapping results
          );
    } catch (Exception e) {
      log.warn(
          "Failed to build specification for orgId={}, snapshot={}: {}",
          orgId,
          snapshot.getProductId(),
          e.getMessage());
      return null; // Skip invalid snapshots
    }
  }

  private ServiceLevel mapServiceLevel(TallySnapshot.Sla sla) {
    if (sla == null) {
      return null;
    }
    try {
      return ServiceLevel.valueOf(sla.name());
    } catch (IllegalArgumentException e) {
      log.debug("Unknown service level in DTO: {}", sla);
      return null;
    }
  }

  private Usage mapUsage(TallySnapshot.Usage usage) {
    if (usage == null) {
      return null;
    }
    try {
      return Usage.valueOf(usage.name());
    } catch (IllegalArgumentException e) {
      log.debug("Unknown usage in DTO: {}", usage);
      return null;
    }
  }

  private BillingProvider mapBillingProvider(TallySnapshot.BillingProvider billingProvider) {
    if (billingProvider == null) {
      return null;
    }
    try {
      return BillingProvider.valueOf(billingProvider.name());
    } catch (IllegalArgumentException e) {
      log.debug("Unknown billing provider in DTO: {}", billingProvider);
      return null;
    }
  }
}
