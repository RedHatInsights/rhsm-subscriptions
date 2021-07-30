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

import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import lombok.ToString;

/**
 * A data projection around SubscriptionCapacity, Subscription, and Offering necessary to give us a
 * view of the data to be returned in the Subscription Table API.
 */
public class SubscriptionCapacityViewOld extends SubscriptionCapacity {

  @ToString.Exclude
  @MapsId("subscription_id")
  @OneToOne(fetch = FetchType.EAGER)
  private Subscription subscription;

  @ToString.Exclude
  @MapsId("sku")
  @ManyToOne(fetch = FetchType.EAGER)
  private Offering offering;

  public Subscription getSubscription() {
    return subscription;
  }

  public void setSubscription(Subscription subscription) {
    this.subscription = subscription;
  }

  public Offering getOffering() {
    return offering;
  }

  public void setOffering(Offering offering) {
    this.offering = offering;
  }
}
