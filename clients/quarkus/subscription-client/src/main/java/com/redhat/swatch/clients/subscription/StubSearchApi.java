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
package com.redhat.swatch.clients.subscription;

import com.redhat.swatch.clients.subscription.api.model.Subscription;
import com.redhat.swatch.clients.subscription.api.model.SubscriptionProduct;
import com.redhat.swatch.clients.subscription.api.resources.SearchApi;
import jakarta.ws.rs.ProcessingException;
import java.util.List;

public class StubSearchApi implements SearchApi {
  @Override
  public Subscription getSubscriptionById(String id) throws ProcessingException {
    return null;
  }

  @Override
  public List<Subscription> getSubscriptionBySubscriptionNumber(String subscriptionNumber)
      throws ProcessingException {
    if (subscriptionNumber.equals("6438195")) {
      Subscription subscription = new Subscription();
      subscription.setId(6438190);
      subscription.setSubscriptionNumber(subscriptionNumber);
      SubscriptionProduct product = new SubscriptionProduct();
      product.setSku("RH00129F5");
      subscription.setSubscriptionProducts(List.of(product));
      subscription.setWebCustomerId(7746627);
      subscription.setQuantity(10);
      subscription.setEffectiveStartDate(1722849931L);
      subscription.setEffectiveEndDate(1849080331L);
      return List.of(subscription);
    }

    return List.of();
  }

  @Override
  public List<Subscription> searchSubscriptionsByOrgId(
      String orgId, Integer index, Integer pageSize) throws ProcessingException {
    return List.of();
  }
}
