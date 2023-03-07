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

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "contracts")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
public class ContractEntity extends PanacheEntityBase {

  @Id
  @Column(name = "uuid", nullable = false)
  private UUID uuid;

  @Basic
  @Column(name = "subscription_number", nullable = false)
  private String subscriptionNumber;

  @Basic
  @Column(name = "last_updated", nullable = false)
  private OffsetDateTime lastUpdated;

  @Basic
  @Column(name = "start_date", nullable = false)
  private OffsetDateTime startDate;

  @Basic
  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Basic
  @Column(name = "org_id", nullable = false)
  private String orgId;

  @Basic
  @Column(name = "sku", nullable = false)
  private String sku;

  @Basic
  @Column(name = "billing_provider", nullable = false)
  private String billingProvider;

  @Basic
  @Column(name = "billing_account_id", nullable = false)
  private String billingAccountId;

  @Basic
  @Column(name = "product_id", nullable = false)
  private String productId;

  @OneToMany(
      targetEntity = ContractMetricEntity.class,
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      mappedBy = "contract")
  private Set<ContractMetricEntity> metrics = new HashSet<>();

  public void addMetric(ContractMetricEntity metric) {
    metrics.add(metric);
    metric.setContract(this);
  }

  public void remoteMetric(ContractMetricEntity metric) {
    metrics.remove(metric);
    metric.setContract(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ContractEntity that = (ContractEntity) o;
    return Objects.equals(subscriptionNumber, that.subscriptionNumber)
        && Objects.equals(orgId, that.orgId)
        && Objects.equals(sku, that.sku)
        && Objects.equals(billingProvider, that.billingProvider)
        && Objects.equals(billingAccountId, that.billingAccountId)
        && Objects.equals(productId, that.productId)
        && Objects.equals(metrics, that.metrics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        subscriptionNumber, orgId, sku, billingProvider, billingAccountId, productId, metrics);
  }
}
