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

import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import java.time.OffsetDateTime;

@Entity
@Immutable
@Subselect(
    "SELECT "
        + "sc.subscription_id,\n"
        + "sc.owner_id, \n"
        + "sc.product_id, \n"
        + "sc.sku, \n"
        + "sc.sla, \n"
        + "sc.usage, \n"
        + "sc.physical_sockets, \n"
        + "sc.virtual_sockets, \n"
        + "sc.physical_cores, \n"
        + "sc.virtual_cores, \n"
        + "sc.end_date, \n"
        + "sc.begin_date, \n"
        + "sc.account_number, \n"
        + "sc.has_unlimited_guest_sockets, \n"
        + "s.quantity, \n"
        + "s.subscription_number, \n"
        + "o.product_name \n"
        + "FROM subscription_capacity sc \n"
        + "JOIN subscription s on sc.subscription_id = s.subscription_id \n"
        + "JOIN offering o on sc.sku = o.sku")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCapacityView {

  @EmbeddedId private SubscriptionCapacityKey key;

  @Column(name = "subscription_number")
  private String subscriptionNumber;

  @Column(name = "quantity")
  private long quantity;

  @Column(name = "sku")
  private String sku;

  @Column(name = "sla")
  private ServiceLevel serviceLevel;

  @Column(name = "usage")
  private Usage usage;

  @Column(name = "physical_sockets")
  private Integer physicalSockets;

  @Column(name = "virtual_sockets")
  private Integer virtualSockets;

  @Column(name = "physical_cores")
  private Integer physicalCores;

  @Column(name = "virtual_cores")
  private Integer virtualCores;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "begin_date")
  private OffsetDateTime beginDate;

  @Column(name = "product_name")
  private String productName;

  @Column(name = "account_number")
  private String accountNumber;

  @Column(name = "has_unlimited_guest_sockets")
  private boolean hasUnlimitedGuestSockets;
}


