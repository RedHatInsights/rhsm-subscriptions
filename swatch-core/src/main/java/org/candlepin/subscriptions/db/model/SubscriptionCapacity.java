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
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.*;

/** Capacity provided by a subscription for a given product. */
@Entity
@Table(name = "subscription_capacity")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class SubscriptionCapacity implements Serializable {
  @EmbeddedId private SubscriptionCapacityKey key;

  @Column(name = "account_number")
  private String accountNumber;

  @Column(name = "physical_sockets")
  private Integer physicalSockets;

  @Column(name = "virtual_sockets")
  private Integer virtualSockets;

  @Column(name = "physical_cores")
  private Integer physicalCores;

  @Column(name = "virtual_cores")
  private Integer virtualCores;

  // Lombok would name the getter "isHasUnlimitedGuestSockets"
  @Getter(AccessLevel.NONE)
  @Column(name = "has_unlimited_usage")
  private Boolean hasUnlimitedUsage;

  @Column(name = "begin_date")
  private OffsetDateTime beginDate;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "sku")
  private String sku;

  @Column(name = "sla")
  private ServiceLevel serviceLevel;

  @Column(name = "usage")
  private Usage usage;

  public SubscriptionCapacity() {
    key = new SubscriptionCapacityKey();
  }

  public String getProductId() {
    return key.getProductId();
  }

  public void setProductId(String productId) {
    key.setProductId(productId);
  }

  public String getSubscriptionId() {
    return key.getSubscriptionId();
  }

  public void setSubscriptionId(String subscriptionId) {
    key.setSubscriptionId(subscriptionId);
  }

  public String getOrgId() {
    return key.getOrgId();
  }

  public void setOrgId(String orgId) {
    key.setOrgId(orgId);
  }

  public Boolean getHasUnlimitedUsage() {
    return hasUnlimitedUsage;
  }

  public static SubscriptionCapacity from(
      Subscription subscription, Offering offering, String product) {
    return SubscriptionCapacity.builder()
        .key(
            SubscriptionCapacityKey.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .orgId(subscription.getOrgId())
                .productId(product)
                .build())
        .accountNumber(subscription.getAccountNumber())
        .beginDate(subscription.getStartDate())
        .endDate(subscription.getEndDate())
        .serviceLevel(offering.getServiceLevel())
        .usage(offering.getUsage())
        .sku(offering.getSku())
        .physicalSockets(totalCapacity(offering.getPhysicalSockets(), subscription.getQuantity()))
        .virtualSockets(totalCapacity(offering.getVirtualSockets(), subscription.getQuantity()))
        .virtualCores(totalCapacity(offering.getVirtualCores(), subscription.getQuantity()))
        .physicalCores(totalCapacity(offering.getPhysicalCores(), subscription.getQuantity()))
        .hasUnlimitedUsage(offering.getHasUnlimitedUsage())
        .build();
  }

  private static Integer totalCapacity(Integer capacity, long quantity) {
    return capacity == null ? null : capacity * (int) quantity;
  }
}
