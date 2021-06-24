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

import java.io.Serializable;
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;
import lombok.*;

/** Subscription entities represent data from a Candlepin Pool */
@Entity
@EqualsAndHashCode
@Getter
@Setter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@IdClass(Subscription.SubscriptionCompoundId.class)
@Table(name = "subscription")
public class Subscription {

  @Id
  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(name = "sku")
  private String sku;

  @Column(name = "owner_id")
  private String ownerId;

  @Column(name = "quantity")
  private long quantity;

  @Id
  @Column(name = "start_date")
  private OffsetDateTime startDate;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "marketplace_subscription_id")
  private String marketplaceSubscriptionId;

  @Column(name = "account_number")
  private String accountNumber;

  /** Composite ID class for Subscription entities. */
  @EqualsAndHashCode
  @Getter
  @Setter
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

  // TODO: Add builder to throw exceptions if required fields are missing
}
