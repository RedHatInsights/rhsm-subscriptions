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
import java.util.Set;
import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.LazyCollection;
import org.hibernate.annotations.LazyCollectionOption;

/**
 * Represents a product Offering that can be provided by a Subscription.
 *
 * <p>Offerings are identified by SKU.
 */
@Entity
@EqualsAndHashCode
@Getter
@Setter
@ToString
@Table(name = "offering")
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
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
  private Set<String> childSkus;

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
  private Set<Integer> productIds;

  /** Effective physical CPU cores capacity per quantity of subscription to this offering. */
  @Column(name = "physical_cores")
  private Integer physicalCores;

  /** Effective physical CPU sockets capacity per quantity of subscription to this offering. */
  @Column(name = "physical_sockets")
  private Integer physicalSockets;

  /** Effective virtual CPU cores capacity per quantity of subscription to this offering. */
  @Column(name = "virtual_cores")
  private Integer virtualCores;

  /** Effective virtual CPU sockets capacity per quantity of subscription to this offering. */
  @Column(name = "virtual_sockets")
  private Integer virtualSockets;

  /** Syspurpose Role for the offering */
  @Column(name = "role")
  private String role;

  @Column(name = "sla")
  private ServiceLevel serviceLevel;

  /** Syspurpose Usage for the offering */
  @Column(name = "usage")
  private Usage usage;
}
