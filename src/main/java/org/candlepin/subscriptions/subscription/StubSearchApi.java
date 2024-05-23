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
package org.candlepin.subscriptions.subscription;

import java.time.OffsetDateTime;
import java.util.List;
import org.candlepin.subscriptions.subscription.api.model.ExternalReference;
import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.model.SubscriptionProduct;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;

/** Stub version of the SearchApi for the Subscription service for local testing. */
public class StubSearchApi extends SearchApi {

  public static final String START_DATE = "2011-01-01T01:02:33Z";
  public static final String END_DATE = "2031-01-01T01:02:33Z";

  @Override
  public Subscription getSubscriptionById(String id) {
    if ("789".equals(id)) {
      return createAwsBillingProviderData();
    } else if ("790".equals(id)) {
      return createDataForOrgId(790);
    }
    return createDefaultData();
  }

  @Override
  public List<Subscription> getSubscriptionBySubscriptionNumber(String subscriptionNumber) {
    return List.of(createAwsBillingProviderData());
  }

  @Override
  public List<Subscription> searchSubscriptionsByOrgId(
      String orgId, Integer index, Integer pageSize) {
    return List.of(
        createDefaultData(),
        createAwsBillingProviderData(),
        createRhelData(),
        createDataForOrgId(790));
  }

  private Subscription createDefaultData() {
    return createData(123, "MW01485");
  }

  private Subscription createDataForOrgId(int orgId) {
    return createData(orgId, "SKU00" + orgId);
  }

  private Subscription createData(int orgId, String sku) {
    return new Subscription()
        .id(orgId)
        .subscriptionNumber("" + orgId)
        .webCustomerId(orgId)
        .quantity(1)
        .effectiveStartDate(OffsetDateTime.parse(START_DATE).toEpochSecond() * 1000L)
        .effectiveEndDate(OffsetDateTime.parse(END_DATE).toEpochSecond() * 1000L)
        .subscriptionProducts(List.of(new SubscriptionProduct().sku(sku)));
  }

  private Subscription createRhelData() {
    return new Subscription()
        .id(235255)
        .subscriptionNumber("2253594")
        .webCustomerId(123)
        .quantity(1)
        .effectiveStartDate(OffsetDateTime.parse(START_DATE).toEpochSecond() * 1000L)
        .effectiveEndDate(OffsetDateTime.parse(END_DATE).toEpochSecond() * 1000L)
        .subscriptionProducts(List.of(new SubscriptionProduct().sku("RH3413336")));
  }

  private Subscription createAwsBillingProviderData() {
    ExternalReference awsRef = new ExternalReference();
    awsRef.setCustomerID("customer123");
    awsRef.setProductCode("testProductCode123");
    awsRef.setSellerAccount("awsSellerAccountId");
    awsRef.setCustomerAccountID("1234567891234");
    return new Subscription()
        .id(235252)
        .quantity(1)
        .webCustomerId(123)
        .subscriptionNumber("4243626")
        .effectiveStartDate(OffsetDateTime.parse(START_DATE).toEpochSecond() * 1000L)
        .effectiveEndDate(OffsetDateTime.parse(END_DATE).toEpochSecond() * 1000L)
        .putExternalReferencesItem("awsMarketplace", awsRef)
        .subscriptionProducts(List.of(new SubscriptionProduct().sku("MW01882")));
  }
}
