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
package org.candlepin.subscriptions.marketplace;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.subscription.SubscriptionService;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Class responsible for communicating with the Marketplace API and fetching the subscription ID.
 */
@Component
public class MarketplaceSubscriptionCollector {

  private static final String IBMMARKETPLACE = "ibmmarketplace";
  private final SubscriptionService subscriptionService;

  @Autowired
  public MarketplaceSubscriptionCollector(SubscriptionService subscriptionService) {
    this.subscriptionService = subscriptionService;
  }

  /**
   * Given an org ID, query the IT Subscription Service and return subscriptions that have an ibm
   * marketplace external reference as part of its payload
   *
   * @param orgId - org ID used to look up subscriptions.
   * @return subscriptions
   */
  public List<Subscription> requestSubscriptions(String orgId) {
    if (!StringUtils.hasText(orgId)) {
      throw new IllegalArgumentException("The orgId parameter can not be null or empty.");
    }

    var subscriptions = subscriptionService.getSubscriptionsByOrgId(orgId);
    return filterNonApplicableSubscriptions(subscriptions);
  }

  protected List<Subscription> filterNonApplicableSubscriptions(List<Subscription> subscriptions) {
    return subscriptions.stream()
        .filter(sub -> !Objects.isNull(sub.getExternalReferences()))
        .filter(sub -> !Objects.isNull(sub.getExternalReferences().get(IBMMARKETPLACE)))
        .collect(Collectors.toList());
  }
}
