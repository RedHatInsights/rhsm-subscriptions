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
package com.redhat.swatch.contract.utils;

import com.redhat.swatch.clients.subscription.api.model.ExternalReference;
import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.model.SubscriptionProduct;
import com.redhat.swatch.contract.repository.BillingProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Utility class to assist in pulling nested data out of the Subscription DTO. */
public class SubscriptionDtoUtil {
  public static final String IBMMARKETPLACE = "ibmmarketplace";
  public static final String AWS_MARKETPLACE = "awsMarketplace";

  private SubscriptionDtoUtil() {
    // Utility methods only
  }

  /**
   * The subscription JSON coming from the service includes a list of every product associated with
   * the subscription. In order to find the operative SKU, we need the top-level product which is
   * the one with a null parentSubscriptionProductId.
   *
   * @param subscription Subscription object from SubscriptionService
   * @return the SKU that has a parentSubscriptionProductId of null
   */
  public static String extractSku(Subscription subscription) {
    List<SubscriptionProduct> products = subscription.getSubscriptionProducts();
    Objects.requireNonNull(products, "No subscription products found");
    List<String> skus =
        products.stream()
            .filter(x -> x.getParentSubscriptionProductId() == null)
            .distinct()
            .map(SubscriptionProduct::getSku)
            .toList();

    if (skus.size() == 1) {
      return skus.get(0);
    }
    throw new IllegalStateException(
        "Could not find top level SKU for subscription " + subscription);
  }

  public static String extractBillingProviderId(Subscription subscription) {
    Map<String, ExternalReference> externalRefs = subscription.getExternalReferences();
    String subId = null;
    if (externalRefs != null && !externalRefs.isEmpty()) {
      if (externalRefs.containsKey(IBMMARKETPLACE)) {
        ExternalReference rhMarketplace = externalRefs.get(IBMMARKETPLACE);
        subId = rhMarketplace.getSubscriptionID();
      } else if (externalRefs.containsKey(AWS_MARKETPLACE)) {
        ExternalReference awsMarketPlace = externalRefs.get(AWS_MARKETPLACE);
        subId =
            String.format(
                "%s;%s;%s",
                awsMarketPlace.getProductCode(),
                awsMarketPlace.getCustomerID(),
                awsMarketPlace.getSellerAccount());
      }
    }
    return subId != null && !subId.isEmpty() ? subId : null;
  }

  public static BillingProvider populateBillingProvider(Subscription subscription) {
    if (subscription.getExternalReferences() != null) {
      if (subscription.getExternalReferences().containsKey(IBMMARKETPLACE)) {
        return BillingProvider.RED_HAT;
      } else if (subscription.getExternalReferences().containsKey(AWS_MARKETPLACE)) {
        return BillingProvider.AWS;
      } else {
        return null;
      }
    }
    return null;
  }

  public static String extractBillingAccountId(Subscription subscription) {
    if (subscription.getExternalReferences() != null
        && subscription.getExternalReferences().containsKey(AWS_MARKETPLACE)) {
      return subscription.getExternalReferences().get(AWS_MARKETPLACE).getCustomerAccountID();
    }
    return null;
  }
}
