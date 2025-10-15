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
package utils;

import com.redhat.swatch.subscriptions.test.model.Subscription;

public final class SubscriptionRequestMapper {
  private SubscriptionRequestMapper() {}

  public static Subscription buildSubscriptionRequest(domain.Subscription subscription) {
    if (subscription == null) {
      return null;
    }

    Subscription request = new Subscription();
    request.setId(Integer.valueOf(subscription.getSubscriptionId()));
    request.setSubscriptionNumber(subscription.getSubscriptionNumber());
    request.setWebCustomerId(Integer.valueOf(subscription.getOrgId()));
    if (subscription.getStartDate() != null) {
      request.setEffectiveStartDate(subscription.getStartDate().toInstant().toEpochMilli());
    }
    if (subscription.getEndDate() != null) {
      request.setEffectiveEndDate(subscription.getEndDate().toInstant().toEpochMilli());
    }
    request.setQuantity(1);
    if (subscription.getOffering() != null) {
      com.redhat.swatch.subscriptions.test.model.SubscriptionProduct product =
          new com.redhat.swatch.subscriptions.test.model.SubscriptionProduct();
      product.setSku(subscription.getOffering().getSku());
      request.addSubscriptionProductsItem(product);
    }

    if (subscription.getBillingProvider() != null || subscription.getBillingAccountId() != null) {
      com.redhat.swatch.subscriptions.test.model.ExternalReference externalRef =
          new com.redhat.swatch.subscriptions.test.model.ExternalReference();

      if (subscription.getBillingAccountId() != null) {
        externalRef.setAccountID(subscription.getBillingAccountId());
      }
      if (subscription.getSubscriptionId() != null) {
        externalRef.setSubscriptionID(subscription.getSubscriptionId());
      }

      // Add external reference with billing provider as key
      String billingProviderKey =
          subscription.getBillingProvider() != null
              ? subscription.getBillingProvider().name().toLowerCase()
              : "default";
      request.putExternalReferencesItem(billingProviderKey, externalRef);
    }

    return request;
  }
}
