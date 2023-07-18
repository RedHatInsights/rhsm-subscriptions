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

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.candlepin.subscriptions.tally.UsageCalculation;

/** An embeddable composite key for a host bucket. */
@Embeddable
@ToString
@NoArgsConstructor
@Getter
@Setter
public class HostBucketKey implements Serializable {
  @Column(name = "host_id")
  private UUID hostId;

  @Column(name = "product_id")
  private String productId;

  private ServiceLevel sla;

  private Usage usage;

  @Column(name = "billing_provider")
  private BillingProvider billingProvider;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Column(name = "as_hypervisor")
  private Boolean asHypervisor;

  public HostBucketKey(
      Host host,
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      Boolean asHypervisor) {
    this.hostId = (host == null) ? null : host.getId();
    this.productId = productId;
    this.sla = sla;
    this.usage = usage;
    this.billingProvider = billingProvider;
    this.billingAccountId = billingAccountId;
    this.asHypervisor = asHypervisor;
  }

  public HostBucketKey(Host host, UsageCalculation.Key key, Boolean asHypervisor) {
    this.hostId = (host == null) ? null : host.getId();
    this.productId = key.getProductId();
    this.sla = key.getSla();
    this.usage = key.getUsage();
    this.billingProvider = key.getBillingProvider();
    this.billingAccountId = key.getBillingAccountId();
    this.asHypervisor = asHypervisor;
  }

  public Boolean getAsHypervisor() {
    return asHypervisor;
  }

  public void setAsHypervisor(Boolean asHypervisor) {
    this.asHypervisor = asHypervisor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof HostBucketKey)) {
      return false;
    }

    HostBucketKey that = (HostBucketKey) o;
    return productId.equals(that.productId)
        && sla == that.sla
        && usage == that.usage
        && billingProvider == that.billingProvider
        && billingAccountId.equals(that.billingAccountId)
        && asHypervisor.equals(that.asHypervisor)
        && Objects.equals(hostId, that.hostId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, sla, asHypervisor);
  }
}
