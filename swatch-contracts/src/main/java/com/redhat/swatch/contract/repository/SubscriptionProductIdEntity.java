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
package com.redhat.swatch.contract.repository;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Table(name = "subscription_product_ids")
public class SubscriptionProductIdEntity implements Serializable {

  @Id
  @ManyToOne
  @JoinColumn(name = "subscription_id", referencedColumnName = "subscription_id")
  @JoinColumn(name = "start_date", referencedColumnName = "start_date")
  private SubscriptionEntity subscription;

  @Id
  @Column(name = "product_id")
  private String productId;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubscriptionProductIdEntity productEntity)) {
      return false;
    }

    return Objects.equals(productId, productEntity.getProductId())
        && Objects.equals(
            subscription.getSubscriptionId(), productEntity.getSubscription().getSubscriptionId())
        && Objects.equals(
            subscription.getStartDate(), productEntity.getSubscription().getStartDate());
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, subscription.getSubscriptionId(), subscription.getStartDate());
  }
}
