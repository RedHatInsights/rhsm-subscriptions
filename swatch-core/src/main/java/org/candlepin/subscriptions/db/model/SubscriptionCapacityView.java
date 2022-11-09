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

import java.time.OffsetDateTime;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

@Entity
@Immutable
// TODO: Join should be moved to Specification https://issues.redhat.com/browse/ENT-4205 //NOSONAR
@Subselect(
    "SELECT "
        + "sc.subscription_id,\n"
        + "sc.org_id, \n"
        + "sc.product_id, \n"
        + "sc.sku, \n"
        + "sc.sla, \n"
        + "sc.usage, \n"
        + "sc.sockets, \n"
        + "sc.hypervisor_sockets, \n"
        + "sc.cores, \n"
        + "sc.hypervisor_cores, \n"
        + "sc.end_date, \n"
        + "sc.begin_date, \n"
        + "sc.account_number, \n"
        + "sc.has_unlimited_usage, \n"
        + "s.quantity, \n"
        + "s.subscription_number, \n"
        + "o.description as product_name \n"
        + "FROM subscription_capacity sc \n"
        + "JOIN subscription s on sc.subscription_id = s.subscription_id \n"
        + "AND s.end_date > CURRENT_TIMESTAMP \n"
        + "JOIN offering o on sc.sku = o.sku")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
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

  @Column(name = "sockets")
  private Integer sockets;

  @Column(name = "hypervisor_sockets")
  private Integer hypervisorSockets;

  @Column(name = "cores")
  private Integer cores;

  @Column(name = "hypervisor_cores")
  private Integer hypervisorCores;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "begin_date")
  private OffsetDateTime beginDate;

  @Column(name = "product_name")
  private String productName;

  @Column(name = "account_number")
  private String accountNumber;

  @Column(name = "has_unlimited_usage")
  private Boolean hasUnlimitedUsage;

  public Integer getSockets() {
    return Objects.isNull(sockets) ? 0 : sockets;
  }

  public Integer getCores() {
    return Objects.isNull(cores) ? 0 : cores;
  }

  public Integer getHypervisorCores() {
    return Objects.isNull(hypervisorCores) ? 0 : hypervisorCores;
  }

  public Integer getHypervisorSockets() {
    return Objects.isNull(hypervisorSockets) ? 0 : hypervisorSockets;
  }
}
