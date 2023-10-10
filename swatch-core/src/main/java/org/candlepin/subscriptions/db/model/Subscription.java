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
package org.candlepin.subscriptions.db.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.*;

/** Subscription entities represent data from a Candlepin Pool */
@Entity
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@IdClass(Subscription.SubscriptionCompoundId.class)
@Table(name = "subscription")
// The below graph fetches all associations needed during subscription sync to avoid n+1 queries
@NamedEntityGraph(
    name = "graph.SubscriptionSync",
    attributeNodes = {
      @NamedAttributeNode(value = "offering", subgraph = "subgraph.offering"),
      @NamedAttributeNode("subscriptionMeasurements"),
      @NamedAttributeNode("subscriptionProductIds")
    },
    subgraphs = {
      @NamedSubgraph(
          name = "subgraph.offering",
          attributeNodes = {@NamedAttributeNode("childSkus"), @NamedAttributeNode("productIds")})
    })
public class Subscription implements Serializable {

  @Id
  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(name = "subscription_number")
  private String subscriptionNumber;

  @ManyToOne
  @JoinColumn(name = "sku")
  private Offering offering;

  @Column(name = "org_id")
  private String orgId;

  @Column(name = "quantity")
  private long quantity;

  @Id
  @Column(name = "start_date")
  private OffsetDateTime startDate;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "billing_provider_id")
  private String billingProviderId;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Column(name = "billing_provider")
  private BillingProvider billingProvider;

  @ElementCollection
  @CollectionTable(
      name = "subscription_product_ids",
      joinColumns = {
        @JoinColumn(name = "subscription_id", referencedColumnName = "subscription_id"),
        @JoinColumn(name = "start_date", referencedColumnName = "start_date")
      })
  @Column(name = "product_id")
  @Builder.Default
  @ToString.Exclude
  private Set<String> subscriptionProductIds = new HashSet<>();

  @Builder.Default
  @ElementCollection
  @CollectionTable(
      name = "subscription_measurements",
      joinColumns = {
        @JoinColumn(name = "subscription_id", referencedColumnName = "subscription_id"),
        @JoinColumn(name = "start_date", referencedColumnName = "start_date")
      })
  @Column(name = "value")
  @ToString.Exclude
  private Map<SubscriptionMeasurementKey, Double> subscriptionMeasurements = new HashMap<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Subscription sub)) {
      return false;
    }

    return Objects.equals(subscriptionId, sub.getSubscriptionId())
        && Objects.equals(subscriptionNumber, sub.getSubscriptionNumber())
        && Objects.equals(orgId, sub.getOrgId())
        && Objects.equals(quantity, sub.getQuantity())
        && Objects.equals(startDate, sub.getStartDate())
        && Objects.equals(endDate, sub.getEndDate())
        && Objects.equals(billingProviderId, sub.getBillingProviderId())
        && Objects.equals(billingAccountId, sub.getBillingAccountId())
        && Objects.equals(billingProvider, sub.getBillingProvider())
        && Objects.equals(subscriptionProductIds, sub.getSubscriptionProductIds());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        subscriptionId,
        subscriptionNumber,
        orgId,
        quantity,
        startDate,
        endDate,
        billingProviderId,
        billingAccountId,
        billingProvider,
        subscriptionProductIds);
  }

  /** Composite ID class for Subscription entities. */
  @Getter
  @Setter
  @ToString
  @EqualsAndHashCode
  public static class SubscriptionCompoundId implements Serializable {
    private String subscriptionId;
    private OffsetDateTime startDate;

    public SubscriptionCompoundId(String subscriptionId, OffsetDateTime startDate) {
      this.subscriptionId = subscriptionId;
      this.startDate = startDate;
    }

    public SubscriptionCompoundId() {
      // default
    }
  }

  public void endSubscription() {
    endDate = OffsetDateTime.now();
  }

  public boolean quantityHasChanged(long newQuantity) {
    return this.getQuantity() != newQuantity;
  }

  // TODO: https://issues.redhat.com/browse/ENT-4030 //NOSONAR
}
