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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.test.ExtendWithSubscriptionSearchServiceWireMock;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.candlepin.subscriptions.umb.Identifiers;
import org.candlepin.subscriptions.umb.Reference;
import org.candlepin.subscriptions.umb.SubscriptionProduct;
import org.candlepin.subscriptions.umb.SubscriptionProductStatus;
import org.candlepin.subscriptions.umb.UmbSubscription;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test-inventory"})
public class SubscriptionSyncLifecycleTest
    implements ExtendWithSubscriptionSearchServiceWireMock, ExtendWithSwatchDatabase {
  @Autowired SubscriptionRepository subscriptionRepository;
  @Autowired OfferingRepository offeringRepository;

  @Autowired SubscriptionSyncController subscriptionSyncController;

  private String SUBSCRIPTION_NUMBER = "101112";
  private String SUBSCRIPTION_SEARCH_RESPONSE =
      """
      [
          {
              "id": 123456,
              "renewedId": null,
              "masterEndSystemName": "SUBSCRIPTION",
              "createdEndSystemName": "SUBSCRIPTION",
              "createdByUserName": null,
              "createdDate": 1672556530000,
              "lastUpdateEndSystemName": "SUBSCRIPTION",
              "lastUpdateUserName": null,
              "lastUpdateDate": 1672556530000,
              "externalCreatedDate": null,
              "externalLastUpdateDate": null,
              "installBaseStartDate": 1672549200000,
              "installBaseEndDate": 1704085199000,
              "inactiveDate": null,
              "oracleAccountNumber": "123",
              "oracleMSANumber": null,
              "oracleIBInstanceNumber": null,
              "webCustomerId": "123",
              "registrationNumber": null,
              "installationNumber": null,
              "subscriptionNumber": "13294886",
              "quantity": 10,
              "subscriptionProducts": null,
              "customer": {
              "id": 7237138,
              "name": "Testing",
              "password": null,
              "creditApplicationCompleted": false,
              "customerType": "organization",
              "oracleCustomerNumber": "123",
              "namedAccount": false,
              "createdDate": 1397548100000,
              "updatedDate": 1612591188913
              },
              "effectiveStartDate": 1672549200000,
              "effectiveEndDate": 1703998800000,
              "externalReferences": null,
              "subscriptionProducts":
              [
               {
                "sku": "BASILISK"
               }
             ]
          }
      ]
      """;

  @Test
  void testMeteredOfferingSubscriptionLifecycle() {
    Offering offering = Offering.builder().sku("BASILISK").metered(true).build();
    offeringRepository.save(offering);
    stubSubscriptionSearchApi(SUBSCRIPTION_SEARCH_RESPONSE);
    var subscriptionNumberReference =
        Reference.builder()
            .system("SUBSCRIPTION")
            .entityName("Subscription")
            .qualifier("number")
            .value(SUBSCRIPTION_NUMBER)
            .build();
    var orgReference =
        Reference.builder()
            .system("WEB")
            .entityName("Customer")
            .qualifier("id")
            .value("123_ICUST")
            .build();
    var subscriptionProduct =
        SubscriptionProduct.builder()
            .sku("BASILISK")
            .status(
                new SubscriptionProductStatus[] {
                  SubscriptionProductStatus.builder().state("Active").build()
                })
            .build();
    subscriptionProduct.setProduct(subscriptionProduct);
    UmbSubscription umbSubscription =
        UmbSubscription.builder()
            .identifiers(
                Identifiers.builder()
                    .ids(new Reference[] {subscriptionNumberReference})
                    .references(new Reference[] {orgReference})
                    .build())
            .products(new SubscriptionProduct[] {subscriptionProduct})
            .quantity(1)
            .build();

    // Should not save a metered offering subscription without billing provider
    subscriptionSyncController.saveUmbSubscription(umbSubscription);
    assertEquals(0, subscriptionRepository.count());

    // Subscription saved with billing provider by contract UMB consumer
    var contractStartDate = OffsetDateTime.of(2024, 5, 1, 15, 0, 0, 0, ZoneOffset.UTC);
    Subscription subscription =
        Subscription.builder()
            .subscriptionNumber(SUBSCRIPTION_NUMBER)
            .subscriptionId("12345")
            .billingProvider(BillingProvider.AZURE)
            .billingProviderId("testBillingProviderId")
            .billingAccountId("testBillingAccountId")
            .startDate(contractStartDate)
            .quantity(1)
            .offering(offering)
            .build();
    subscriptionRepository.save(subscription);

    // Subscription UMB save message does not affect start date
    subscriptionSyncController.saveUmbSubscription(umbSubscription);
    assertEquals(1, subscriptionRepository.count());
    var persistedSubscription =
        subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(contractStartDate, persistedSubscription.get(0).getStartDate());
    assertNull(persistedSubscription.get(0).getEndDate());

    // Subscription is terminated by a subscription UMB message
    var terminateDate = LocalDateTime.of(2024, 6, 12, 0, 0);
    umbSubscription.setEffectiveEndDate(terminateDate);
    subscriptionSyncController.saveUmbSubscription(umbSubscription);
    var persistedTerminatedSubscription =
        subscriptionRepository.findBySubscriptionNumber(SUBSCRIPTION_NUMBER);
    assertEquals(
        terminateDate.toLocalDate(),
        persistedTerminatedSubscription.get(0).getEndDate().toLocalDate());
  }

  private static void stubSubscriptionSearchApi(String jsonBody) {
    SUBSCRIPTION_SERVICE_WIRE_MOCK_SERVER.stubFor(
        get(urlPathMatching("/search/criteria;subscription_number.*"))
            .willReturn(
                aResponse().withHeader("Content-Type", "application/json").withBody(jsonBody)));
  }
}
