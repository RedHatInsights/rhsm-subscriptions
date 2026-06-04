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

    boolean slaIsNotAny = snapshot.getServiceLevel() != ServiceLevel._ANY;
    boolean usageIsNotAny = snapshot.getUsage() != Usage._ANY;

    // Look up the subscription definition to determine if it's PAYG eligible
    SubscriptionDefinition subscriptionDefinition =
        SubscriptionDefinition.lookupSubscriptionByTag(snapshot.getProductId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        snapshot.getProductId() + " missing in subscription configuration"));

    if (subscriptionDefinition.isPaygEligible()) {
      // For PAYG products, all four fields must be non-_ANY
      boolean billingProviderIsNotAny = snapshot.getBillingProvider() != BillingProvider._ANY;
      boolean billingAccountIdIsNotAny =
          !Objects.isNull(snapshot.getBillingAccountId())
              && !ANY.equals(snapshot.getBillingAccountId());
      return slaIsNotAny && usageIsNotAny && billingProviderIsNotAny && billingAccountIdIsNotAny;
    } else {
      // For traditional products, SLA and usage must be non-_ANY, and billing fields must be _ANY
      boolean billingProviderIsAny = snapshot.getBillingProvider() == BillingProvider._ANY;
      boolean billingAccountIdIsAny = ANY.equals(snapshot.getBillingAccountId());

      // Log warning if billing fields are not _ANY for traditional products
      if (!billingProviderIsAny || !billingAccountIdIsAny) {
        log.warn(
            "Traditional product snapshot has unexpected billing field values. "
                + "productId={}, billingProvider={}, billingAccountId={}. "
                + "Expected both to be _ANY.",
            snapshot.getProductId(),
            snapshot.getBillingProvider(),
            snapshot.getBillingAccountId());
      }

      return slaIsNotAny && usageIsNotAny && billingProviderIsAny && billingAccountIdIsAny;
    }
  }
}
