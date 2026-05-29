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
package org.candlepin.subscriptions.tally.admin;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import io.micrometer.core.annotation.Timed;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for updating is_primary column based on PAYG eligibility. */
@Slf4j
@Service
@AllArgsConstructor
public class IsPrimaryUpdateService {

  private final TallySnapshotRepository snapshotRepository;
  private final HostTallyBucketRepository bucketRepository;

  /**
   * Set is_primary=true for appropriate rows based on product's PAYG eligibility (asynchronous).
   *
   * @param orgId Organization ID (optional, null processes all orgs)
   * @param productId Product ID (required)
   * @param startDate Start date (required, inclusive)
   * @param endDate End date (required, exclusive)
   */
  @Async("updatePrimaryTaskExecutor")
  @Timed("rhsm-subscriptions.snapshots.is-primary-update.async")
  public void updateIsPrimaryAsync(
      String orgId, String productId, OffsetDateTime startDate, OffsetDateTime endDate) {
    try {
      updateIsPrimary(orgId, productId, startDate, endDate);
    } catch (Exception e) {
      log.error(
          "Failed to update is_primary asynchronously for org={}, product={}, dates=[{}, {}]: {}",
          orgId != null ? orgId : "ALL",
          productId,
          startDate,
          endDate,
          e.getMessage(),
          e);
    }
  }

  /**
   * Set is_primary=true for appropriate rows based on product's PAYG eligibility (synchronous).
   *
   * @param orgId Organization ID (optional, null processes all orgs)
   * @param productId Product ID (required)
   * @param startDate Start date (required, inclusive)
   * @param endDate End date (required, exclusive)
   * @return Number of rows updated
   */
  @Transactional
  @Timed("rhsm-subscriptions.snapshots.is-primary-update.sync")
  public int updateIsPrimarySync(
      String orgId, String productId, OffsetDateTime startDate, OffsetDateTime endDate) {
    return updateIsPrimary(orgId, productId, startDate, endDate);
  }

  private int updateIsPrimary(
      String orgId, String productId, OffsetDateTime startDate, OffsetDateTime endDate) {
    log.info(
        "Updating is_primary for org={}, product={}, dates=[{}, {}]",
        orgId != null ? orgId : "ALL",
        productId,
        startDate,
        endDate);

    boolean isPayg =
        SubscriptionDefinition.lookupSubscriptionByTag(productId)
            .map(SubscriptionDefinition::isPaygEligible)
            .orElse(false);

    int rowsUpdated =
        isPayg
            ? snapshotRepository.setIsPrimaryForPayg(
                orgId,
                productId,
                startDate,
                endDate,
                ServiceLevel._ANY,
                Usage._ANY,
                BillingProvider._ANY)
            : snapshotRepository.setIsPrimaryForNonPayg(
                orgId,
                productId,
                startDate,
                endDate,
                ServiceLevel._ANY,
                Usage._ANY,
                BillingProvider._ANY);

    log.info(
        "Updated is_primary=true for {} rows (org={}, product={}, payg={}, dates=[{}, {}])",
        rowsUpdated,
        orgId != null ? orgId : "ALL",
        productId,
        isPayg,
        startDate,
        endDate);

    return rowsUpdated;
  }

  @Async("updatePrimaryTaskExecutor")
  @Timed("rhsm-subscriptions.host-tally-buckets.is-primary-update.async")
  public void updateHostTallyBucketsIsPrimaryAsync(String orgId, String productId) {
    try {
      updateHostTallyBucketsIsPrimary(orgId, productId);
    } catch (Exception e) {
      log.error(
          "Failed to update is_primary asynchronously for org={}, product={}: {}",
          orgId != null ? orgId : "ALL",
          productId,
          e.getMessage(),
          e);
    }
  }

  @Transactional
  @Timed("rhsm-subscriptions.host-tally-buckets.is-primary-update.sync")
  public int updateHostTallyBucketsIsPrimarySync(String orgId, String productId) {
    return updateHostTallyBucketsIsPrimary(orgId, productId);
  }

  private int updateHostTallyBucketsIsPrimary(String orgId, String productId) {
    log.info(
        "Updating is_primary for org={}, product={}", orgId != null ? orgId : "ALL", productId);

    boolean isPayg =
        SubscriptionDefinition.lookupSubscriptionByTag(productId)
            .map(SubscriptionDefinition::isPaygEligible)
            .orElse(false);

    int rowsUpdated =
        isPayg
            ? bucketRepository.setIsPrimaryForPayg(
                orgId, productId, ServiceLevel._ANY, Usage._ANY, BillingProvider._ANY)
            : bucketRepository.setIsPrimaryForNonPayg(
                orgId, productId, ServiceLevel._ANY, Usage._ANY, BillingProvider._ANY);

    log.info(
        "Updated is_primary=true for {} rows (org={}, product={}, payg={})",
        rowsUpdated,
        orgId != null ? orgId : "ALL",
        productId,
        isPayg);

    return rowsUpdated;
  }
}
