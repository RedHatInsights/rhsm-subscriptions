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
package com.redhat.swatch;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@Table(name = "contracts")
public class Contract extends PanacheEntityBase {

  public Contract(Contract contract) {
    this.uuid = UUID.randomUUID();
    this.subscriptionNumber = contract.getSubscriptionNumber();
    this.lastUpdated = OffsetDateTime.now();
    this.startDate = contract.getStartDate();
    this.endDate = contract.getEndDate();
    this.orgId = contract.getOrgId();
    this.sku = contract.getSku();
    this.billingProvider = contract.getBillingProvider();
    this.billingAccountId = contract.getBillingAccountId();
    this.productId = contract.getProductId();

    contract
        .getMetrics()
        .forEach(
            metric -> {
              ContractMetric newMetric = new ContractMetric();
              newMetric.setContractUuid(this.uuid);
              newMetric.setMetricId(metric.getMetricId());
              newMetric.setValue(metric.getValue());
              addMetric(newMetric);
            });
  }

  @Id
  @Column(name = "uuid", nullable = false)
  // @org.hibernate.annotations.Type(type = "pg-uuid")
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
      targetEntity = ContractMetric.class,
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      mappedBy = "contract")
  private List<ContractMetric> metrics = new ArrayList<>();

  public void addMetric(ContractMetric metric) {
    metrics.add(metric);
    metric.setContract(this);
  }

  public void remoteMetric(ContractMetric metric) {
    metrics.remove(metric);
    metric.setContract(null);
  }
}
