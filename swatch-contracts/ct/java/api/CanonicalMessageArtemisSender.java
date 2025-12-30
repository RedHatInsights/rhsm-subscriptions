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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import com.redhat.swatch.contract.product.umb.Identifiers;
import com.redhat.swatch.contract.product.umb.Payload;
import com.redhat.swatch.contract.product.umb.Reference;
import com.redhat.swatch.contract.product.umb.SubscriptionProduct;
import com.redhat.swatch.contract.product.umb.SubscriptionProductStatus;
import com.redhat.swatch.contract.product.umb.SubscriptionStatus;
import com.redhat.swatch.contract.product.umb.Sync;
import com.redhat.swatch.contract.product.umb.UmbSubscription;
import domain.Subscription;
import io.restassured.http.ContentType;

/**
 * Builder for creating and sending CanonicalMessage messages via Artemis. Provides a fluent API for
 * constructing subscriptions messages from test domain objects.
 */
public class CanonicalMessageArtemisSender {

  public static final String SUBSCRIPTION_CHANNEL = "VirtualTopic.canonical.subscription";
  private final ContractsArtemisService artemisService;
  private final XmlMapper mapper = CanonicalMessage.createMapper();

  protected CanonicalMessageArtemisSender(ContractsArtemisService artemisService) {
    this.artemisService = artemisService;
    this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Build and send a CanonicalMessage message from a Subscription domain object.
   *
   * @param subscription the subscription test data
   */
  public void send(Subscription subscription) {
    sendMessage(fromSubscription(subscription, "Active"));
  }

  /**
   * Build and send a CanonicalMessage for a terminated subscription. This sets the product status
   * to "Terminated" which signals the system to update the subscription's end date to the
   * termination date.
   *
   * @param subscription the subscription test data with termination end date
   */
  public void sendTerminated(Subscription subscription) {
    sendMessage(fromSubscription(subscription, "Terminated"));
  }

  /**
   * Send a CanonicalMessage via Artemis.
   *
   * @param message the CanonicalMessage to send
   */
  private void sendMessage(CanonicalMessage message) {
    try {
      artemisService.send(
          SUBSCRIPTION_CHANNEL, mapper.writeValueAsString(message), ContentType.XML.toString());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize CanonicalMessage", e);
    }
  }

  /**
   * Build a CanonicalMessage object from a Subscription domain object.
   *
   * @param subscription the subscription test data
   * @param productState the state for the product (e.g., "Active", "Terminated")
   * @return CanonicalMessage object
   */
  private CanonicalMessage fromSubscription(Subscription subscription, String productState) {
    Identifiers identifiers = buildIdentifiers(subscription);
    SubscriptionStatus status = buildSubscriptionStatus(subscription);
    SubscriptionProduct[] products = buildProducts(subscription, productState);

    UmbSubscription umbSubscription =
        UmbSubscription.builder()
            .identifiers(identifiers)
            .status(status)
            .quantity(subscription.getQuantity())
            .effectiveStartDate(toLocalDateTime(subscription.getStartDate()))
            .effectiveEndDate(toLocalDateTime(subscription.getEndDate()))
            .products(products)
            .build();

    Sync sync = Sync.builder().subscription(umbSubscription).build();
    Payload payload = Payload.builder().sync(sync).build();
    return CanonicalMessage.builder().payload(payload).build();
  }

  /**
   * Build identifiers including subscription number and customer ID.
   *
   * @param subscription the subscription test data
   * @return Identifiers object
   */
  private Identifiers buildIdentifiers(Subscription subscription) {
    Reference[] identifierRefs =
        new Reference[] {
          Reference.builder()
              .system("SUBSCRIPTION")
              .entityName("Subscription")
              .qualifier("number")
              .value(subscription.getSubscriptionNumber())
              .build()
        };

    Reference[] references =
        new Reference[] {
          Reference.builder()
              .system("WEB")
              .entityName("Customer")
              .qualifier("id")
              .value(subscription.getOrgId() + "_ICUST")
              .build()
        };

    // Add EBS account number if billing account is present
    if (subscription.getBillingAccountId() != null) {
      Reference ebsRef =
          Reference.builder()
              .system("EBS")
              .entityName("Account")
              .qualifier("number")
              .value(subscription.getBillingAccountId())
              .build();
      Reference[] newRefs = new Reference[references.length + 1];
      System.arraycopy(references, 0, newRefs, 0, references.length);
      newRefs[references.length] = ebsRef;
      references = newRefs;
    }

    return Identifiers.builder().ids(identifierRefs).references(references).build();
  }

  /**
   * Build subscription status.
   *
   * @param subscription the subscription test data
   * @return SubscriptionStatus object
   */
  private SubscriptionStatus buildSubscriptionStatus(Subscription subscription) {
    return SubscriptionStatus.builder()
        .state("Active")
        .startDate(toLocalDateTime(subscription.getStartDate()))
        .build();
  }

  /**
   * Build products array with nested product structure.
   *
   * @param subscription the subscription test data
   * @param productState the state for the product (e.g., "Active", "Terminated")
   * @return SubscriptionProduct array
   */
  private SubscriptionProduct[] buildProducts(Subscription subscription, String productState) {
    // For terminated subscriptions, use the end date as the status start date
    java.time.LocalDateTime productStatusDate =
        "Terminated".equals(productState) && subscription.getEndDate() != null
            ? toLocalDateTime(subscription.getEndDate())
            : toLocalDateTime(subscription.getStartDate());

    SubscriptionProductStatus[] statuses =
        new SubscriptionProductStatus[] {
          SubscriptionProductStatus.builder()
              .state(productState)
              .startDate(productStatusDate)
              .build()
        };

    SubscriptionProduct innerProduct =
        SubscriptionProduct.builder()
            .sku(subscription.getOffering().getSku())
            .status(statuses)
            .build();

    SubscriptionProduct outerProduct =
        SubscriptionProduct.builder()
            .sku(subscription.getOffering().getSku())
            .status(statuses)
            .product(innerProduct)
            .build();

    return new SubscriptionProduct[] {outerProduct};
  }

  /**
   * Convert OffsetDateTime to LocalDateTime, handling null values.
   *
   * @param dateTime the OffsetDateTime to convert
   * @return LocalDateTime or null
   */
  private java.time.LocalDateTime toLocalDateTime(java.time.OffsetDateTime dateTime) {
    return dateTime != null ? dateTime.toLocalDateTime() : null;
  }
}
