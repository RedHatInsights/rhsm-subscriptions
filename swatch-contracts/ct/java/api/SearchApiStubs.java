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
package api;

import domain.Subscription;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Facade for stubbing Subscription API endpoints. */
public class SearchApiStubs {

  private final ContractsWiremockService wiremockService;

  protected SearchApiStubs(ContractsWiremockService wiremockService) {
    this.wiremockService = wiremockService;
  }

  public void stubGetSubscriptionBySubscriptionNumber(Subscription... subscriptions) {
    for (var subscription : subscriptions) {
      stubGetSubscriptionBySubscriptionNumber(subscription);
    }
  }

  /**
   * Stub the getSubscriptionBySubscriptionNumber endpoint to return a subscription for a given
   * subscription number.
   *
   * @param subscription the subscription to stub
   */
  public void stubGetSubscriptionBySubscriptionNumber(Subscription subscription) {
    var responseBody = mapToApiModel(subscription);

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    String.format(
                        "/mock/subscriptionApi/search/criteria;subscription_number=%s/options;products=ALL;showExternalReferences=true/",
                        subscription.getSubscriptionNumber())),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    List.of(responseBody)),
                "priority",
                9,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Stub the searchSubscriptionsByOrgId endpoint to return subscriptions for a given orgId. This
   * creates a generic stub that matches any index/pageSize parameters.
   *
   * @param subscriptions the subscriptions to stub
   */
  public void stubSearchSubscriptionsByOrgId(String orgId, Subscription... subscriptions) {
    var responseBody = Stream.of(subscriptions).map(this::mapToApiModel).toList();

    wiremockService
        .given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "GET",
                    "urlPathPattern",
                    String.format(
                        "/mock/subscription/search/criteria;web_customer_id=%s/options;products=ALL;showExternalReferences=true;firstResultIndex=.*;maxResults=.*/",
                        orgId)),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    responseBody),
                "priority",
                9,
                "metadata",
                wiremockService.getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  /**
   * Map the domain Subscription model to the API Subscription model. Only maps fields that are used
   * by SubscriptionService and SubscriptionSyncService.
   *
   * @param subscription the domain subscription
   * @return the API subscription model
   */
  private Map<String, Object> mapToApiModel(Subscription subscription) {
    var apiSubscription = new HashMap<String, Object>();
    apiSubscription.put("id", subscription.getSubscriptionId());
    apiSubscription.put("subscriptionNumber", subscription.getSubscriptionNumber());
    apiSubscription.put("quantity", subscription.getQuantity());
    apiSubscription.put("webCustomerId", subscription.getOrgId());

    // Date fields - convert from OffsetDateTime to epoch milliseconds
    if (subscription.getStartDate() != null) {
      apiSubscription.put(
          "effectiveStartDate", subscription.getStartDate().toInstant().toEpochMilli());
    }
    if (subscription.getEndDate() != null) {
      apiSubscription.put("effectiveEndDate", subscription.getEndDate().toInstant().toEpochMilli());
    }

    // SubscriptionProducts - used to extract SKU
    var subscriptionProducts = new ArrayList<Map<String, Object>>();
    if (subscription.getOffering() != null && subscription.getOffering().getSku() != null) {
      var product = new HashMap<String, Object>();
      product.put("sku", subscription.getOffering().getSku());
      product.put("parentSubscriptionProductId", null); // Top-level product has null parent
      subscriptionProducts.add(product);
    }
    apiSubscription.put("subscriptionProducts", subscriptionProducts);

    // ExternalReferences - used for billing provider info (AWS/Azure marketplace)
    if (subscription.getBillingProvider() != null) {
      var externalReferences = new HashMap<String, Object>();

      if (subscription.getBillingProvider() == domain.BillingProvider.AWS) {
        var awsRef = new HashMap<String, Object>();
        awsRef.put("customerAccountID", subscription.getBillingAccountId());
        if (subscription.getBillingProviderId() != null) {
          String[] keys = subscription.getBillingProviderId().split(";");
          awsRef.put("productCode", keys[0]);
          awsRef.put("customerID", keys[1]);
          awsRef.put("sellerAccount", keys[2]);
        }
        externalReferences.put("awsMarketplace", awsRef);
      }

      // Note that for azure subscriptions, the Search API does not provide external references
      // See ADR: 0003-azure-subscription-sync-limitation.md for further information.

      apiSubscription.put("externalReferences", externalReferences);
    }

    return apiSubscription;
  }
}
