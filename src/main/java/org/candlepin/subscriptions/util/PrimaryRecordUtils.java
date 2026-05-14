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
package org.candlepin.subscriptions.util;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;

/** Utility class for primary record operations. */
@Slf4j
public class PrimaryRecordUtils {

  private static final String ANY = "_ANY";

  private PrimaryRecordUtils() {
    throw new IllegalStateException("Utility class; should never be instantiated!");
  }

  /**
   * Determines if a TallySnapshot record is a primary record based on its product type and
   * attributes.
   *
   * <p>For PAYG products, a snapshot is primary if:
   *
   * <ul>
   *   <li>SLA is not _ANY
   *   <li>Usage is not _ANY
   *   <li>Billing provider is not _ANY
   *   <li>Billing account ID is not null and not _ANY
   * </ul>
   *
   * <p>For traditional (non-PAYG) products, a snapshot is primary if:
   *
   * <ul>
   *   <li>SLA is not _ANY
   *   <li>Usage is not _ANY
   *   <li>Billing provider is _ANY
   *   <li>Billing account ID is _ANY
   * </ul>
   *
   * @param snapshot the TallySnapshot to evaluate
   * @return true if the snapshot is a primary record, false otherwise
   * @throws IllegalArgumentException if snapshot is null or has a null product ID
   * @throws IllegalStateException if the product ID is not found in the subscription configuration
   */
  public static boolean isPrimaryRecord(TallySnapshot snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("TallySnapshot cannot be null");
    }
    if (snapshot.getProductId() == null) {
      throw new IllegalArgumentException("TallySnapshot productId cannot be null");
    }
    return isPrimary(
        snapshot.getProductId(),
        snapshot.getServiceLevel(),
        snapshot.getUsage(),
        snapshot.getBillingProvider(),
        snapshot.getBillingAccountId());
  }

  /**
   * Determines if a HostTallyBucket record is a primary record based on its product type and
   * attributes.
   *
   * <p>For PAYG products, a bucket is primary if:
   *
   * <ul>
   *   <li>SLA is not _ANY
   *   <li>Usage is not _ANY
   *   <li>Billing provider is not _ANY
   *   <li>Billing account ID is not null and not _ANY
   * </ul>
   *
   * <p>For traditional (non-PAYG) products, a bucket is primary if:
   *
   * <ul>
   *   <li>SLA is not _ANY
   *   <li>Usage is not _ANY
   *   <li>Billing provider is _ANY
   *   <li>Billing account ID is _ANY
   * </ul>
   *
   * @param bucket the HostTallyBucket to evaluate
   * @return true if the bucket is a primary record, false otherwise
   * @throws IllegalArgumentException if bucket is null, has a null key, or has a null product ID
   * @throws IllegalStateException if the product ID is not found in the subscription configuration
   */
  public static boolean isPrimaryRecord(HostTallyBucket bucket) {
    if (bucket == null) {
      throw new IllegalArgumentException("HostTallyBucket cannot be null");
    }
    if (bucket.getKey() == null) {
      throw new IllegalArgumentException("HostTallyBucket key cannot be null");
    }
    if (bucket.getKey().getProductId() == null) {
      throw new IllegalArgumentException("HostTallyBucket productId cannot be null");
    }
    return isPrimary(
        bucket.getKey().getProductId(),
        bucket.getKey().getSla(),
        bucket.getKey().getUsage(),
        bucket.getKey().getBillingProvider(),
        bucket.getKey().getBillingAccountId());
  }

  private static boolean isPrimary(
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId) {

    boolean slaIsNotAny = sla != ServiceLevel._ANY;
    boolean usageIsNotAny = usage != Usage._ANY;

    SubscriptionDefinition subscriptionDefinition =
        SubscriptionDefinition.lookupSubscriptionByTag(productId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        productId + " missing in subscription configuration"));

    if (subscriptionDefinition.isPaygEligible()) {
      boolean billingProviderIsNotAny = billingProvider != BillingProvider._ANY;
      boolean billingAccountIdIsNotAny =
          !Objects.isNull(billingAccountId) && !ANY.equals(billingAccountId);
      return slaIsNotAny && usageIsNotAny && billingProviderIsNotAny && billingAccountIdIsNotAny;
    } else {
      boolean billingProviderIsAny = billingProvider == BillingProvider._ANY;
      boolean billingAccountIdIsAny = ANY.equals(billingAccountId);
      return slaIsNotAny && usageIsNotAny && billingProviderIsAny && billingAccountIdIsAny;
    }
  }
}
