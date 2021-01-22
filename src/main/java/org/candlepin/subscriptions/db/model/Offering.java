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

import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;

/**
 * Represents a product Offering that can be provided by a Subscription.
 *
 * <p>Offerings are identified by SKU.
 */
@Entity
@Table(name = "offering")
public class Offering implements Serializable {

  /**
   * Unique identifier for the Offering.
   *
   * <p>Because we want only a single instance per SKU, we'll use the SKU as the only primary key.
   *
   * <p>Note that many types of SKUs exist within Red Hat, Offering will be Marketing SKUs only.
   */
  @Id
  @Column(name = "sku")
  private String sku;

  /**
   * Customer-facing name for the offering.
   *
   * <p>E.g. Red Hat Enterprise Linux Server with Smart Management + Satellite, Standard (Physical
   * or Virtual Nodes)
   */
  @Column(name = "product_name")
  private String productName;

  /**
   * Category for the offering.
   *
   * <p>E.g. "Red Hat Enterprise Linux" or "Ansible"
   */
  @Column(name = "product_family")
  private String productFamily;

  /** Internal identifiers for products that compose an Offering. */
  @ElementCollection
  @LazyCollection(LazyCollectionOption.FALSE)
  @CollectionTable(name = "sku_child_sku", joinColumns = @JoinColumn(name = "sku"))
  @Column(name = "child_sku")
  private List<String> childSkus;

  /**
   * Numeric identifiers for Engineering Products provided by the offering.
   *
   * <p>Engineering products define a set of installable content.
   *
   * <p>See
   * https://www.candlepinproject.org/docs/candlepin/how_subscriptions_work.html#engineering-products
   *
   * <p>Sometimes referred to as "provided products".
   */
  @ElementCollection
  @LazyCollection(LazyCollectionOption.FALSE)
  @CollectionTable(name = "sku_oid", joinColumns = @JoinColumn(name = "sku"))
  @Column(name = "oid")
  private List<Integer> productIds;

  /** Effective physical CPU cores capacity per quantity of subscription to this offering. */
  @Column(name = "physical_cores")
  private int physicalCores;

  /** Effective physical CPU sockets capacity per quantity of subscription to this offering. */
  @Column(name = "physical_sockets")
  private int physicalSockets;

  /** Effective virtual CPU cores capacity per quantity of subscription to this offering. */
  @Column(name = "virtual_cores")
  private int virtualCores;

  /** Effective virtual CPU sockets capacity per quantity of subscription to this offering. */
  @Column(name = "virtual_sockets")
  private int virtualSockets;

  /** Syspurpose Role for the offering */
  @Column(name = "role")
  private String role;

  @Column(name = "sla")
  private ServiceLevel serviceLevel;

  /** Syspurpose Usage for the offering */
  @Column(name = "usage")
  private Usage usage;

  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public String getProductName() {
    return productName;
  }

  public void setProductName(String productName) {
    this.productName = productName;
  }

  public String getProductFamily() {
    return productFamily;
  }

  public void setProductFamily(String productFamily) {
    this.productFamily = productFamily;
  }

  public List<String> getChildSkus() {
    return childSkus;
  }

  public void setChildSkus(List<String> childSkus) {
    this.childSkus = childSkus;
  }

  public List<Integer> getProductIds() {
    return productIds;
  }

  public void setProductIds(List<Integer> productIds) {
    this.productIds = productIds;
  }

  public int getPhysicalCores() {
    return physicalCores;
  }

  public void setPhysicalCores(int physicalCores) {
    this.physicalCores = physicalCores;
  }

  public int getPhysicalSockets() {
    return physicalSockets;
  }

  public void setPhysicalSockets(int physicalSockets) {
    this.physicalSockets = physicalSockets;
  }

  public int getVirtualCores() {
    return virtualCores;
  }

  public void setVirtualCores(int virtualCores) {
    this.virtualCores = virtualCores;
  }

  public int getVirtualSockets() {
    return virtualSockets;
  }

  public void setVirtualSockets(int virtualSockets) {
    this.virtualSockets = virtualSockets;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public ServiceLevel getServiceLevel() {
    return serviceLevel;
  }

  public void setServiceLevel(ServiceLevel serviceLevel) {
    this.serviceLevel = serviceLevel;
  }

  public Usage getUsage() {
    return usage;
  }

  public void setUsage(Usage usage) {
    this.usage = usage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Offering offering = (Offering) o;
    return physicalCores == offering.physicalCores
        && physicalSockets == offering.physicalSockets
        && virtualCores == offering.virtualCores
        && virtualSockets == offering.virtualSockets
        && Objects.equals(sku, offering.sku)
        && Objects.equals(productName, offering.productName)
        && Objects.equals(productFamily, offering.productFamily)
        && Objects.equals(childSkus, offering.childSkus)
        && Objects.equals(productIds, offering.productIds)
        && Objects.equals(role, offering.role)
        && serviceLevel == offering.serviceLevel
        && usage == offering.usage;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sku,
        productName,
        productFamily,
        childSkus,
        productIds,
        physicalCores,
        physicalSockets,
        virtualCores,
        virtualSockets,
        role,
        serviceLevel,
        usage);
  }

  @Override
  public String toString() {
    return "Offering{"
        + "sku='"
        + sku
        + '\''
        + ", productName='"
        + productName
        + '\''
        + ", productFamily='"
        + productFamily
        + '\''
        + ", childSkus="
        + childSkus
        + ", productIds="
        + productIds
        + ", physicalCores="
        + physicalCores
        + ", physicalSockets="
        + physicalSockets
        + ", virtualCores="
        + virtualCores
        + ", virtualSockets="
        + virtualSockets
        + ", role='"
        + role
        + '\''
        + ", serviceLevel="
        + serviceLevel
        + ", usage="
        + usage
        + '}';
  }
}
