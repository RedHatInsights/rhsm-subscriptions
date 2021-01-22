/*
 * Copyright (c) 2021 Red Hat, Inc.
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
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;

/** An embeddable composite key for a host bucket. */
@Embeddable
public class HostBucketKey implements Serializable {

  @Column(name = "product_id")
  private String productId;

  private ServiceLevel sla;

  private Usage usage;

  @Column(name = "as_hypervisor")
  private Boolean asHypervisor;

  @ManyToOne(fetch = FetchType.LAZY)
  private Host host;

  public HostBucketKey() {}

  public HostBucketKey(
      Host host, String productId, ServiceLevel sla, Usage usage, Boolean asHypervisor) {
    this.host = host;
    this.productId = productId;
    this.sla = sla;
    this.usage = usage;
    this.asHypervisor = asHypervisor;
  }

  public String getProductId() {
    return productId;
  }

  public void setProductId(String productId) {
    this.productId = productId;
  }

  public ServiceLevel getSla() {
    return sla;
  }

  public void setSla(ServiceLevel sla) {
    this.sla = sla;
  }

  public Usage getUsage() {
    return usage;
  }

  public void setUsage(Usage usage) {
    this.usage = usage;
  }

  public Boolean getAsHypervisor() {
    return asHypervisor;
  }

  public void setAsHypervisor(Boolean asHypervisor) {
    this.asHypervisor = asHypervisor;
  }

  public Host getHost() {
    return host;
  }

  public void setHost(Host host) {
    this.host = host;
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
        && asHypervisor.equals(that.asHypervisor)
        && host.equals(that.host);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, sla, asHypervisor, host);
  }
}
