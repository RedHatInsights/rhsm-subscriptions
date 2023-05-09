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

  @Override
  public Subscription getSubscriptionById(String id) throws ApiException {
    if ("789".equals(id)) {
      return createAwsBillingProviderData();
    }
    return createData();
  }

  @Override
  public List<Subscription> getSubscriptionBySubscriptionNumber(String subscriptionNumber)
      throws ApiException {
    return List.of(createAwsBillingProviderData());
  }

  @Override
  public List<Subscription> searchSubscriptionsByAccountNumber(
      String accountNumber, Integer index, Integer pageSize) throws ApiException {
    return List.of(createData(), createAwsBillingProviderData());
  }

  @Override
  public List<Subscription> searchSubscriptionsByOrgId(
      String orgId, Integer index, Integer pageSize) throws ApiException {
    return List.of(createData(), createAwsBillingProviderData());
  }

  private Subscription createData() {
    return new Subscription()
        .subscriptionNumber("2253591")
        .webCustomerId(123)
        .oracleAccountNumber(123)
        .quantity(1)
        .effectiveStartDate(OffsetDateTime.parse("2011-01-01T01:02:33Z").toEpochSecond() * 1000L)
        .effectiveEndDate(OffsetDateTime.parse("2031-01-01T01:02:33Z").toEpochSecond() * 1000L)
        .subscriptionProducts(List.of(new SubscriptionProduct().sku("MW01485")));
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
        .oracleAccountNumber(123)
        .subscriptionNumber("4243626")
        .effectiveStartDate(OffsetDateTime.parse("2011-01-01T01:02:33Z").toEpochSecond() * 1000L)
        .effectiveEndDate(OffsetDateTime.parse("2031-01-01T01:02:33Z").toEpochSecond() * 1000L)
        .putExternalReferencesItem("aws", awsRef)
        .subscriptionProducts(List.of(new SubscriptionProduct().sku("MW01882")));
  }
}
