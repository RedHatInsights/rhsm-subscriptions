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
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NotNull
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
  @Column(name = "subscription_number")
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

  @NotNull
  @Basic
  @Column(name = "org_id", nullable = false)
  private String orgId;

  @NotNull
  @Basic
  @Column(name = "sku", nullable = false)
  private String sku;

  @NotNull
  @Basic
  @Column(name = "billing_provider", nullable = false)
  private String billingProvider;

  @Basic
  @Column(name = "billing_provider_id")
  private String billingProviderId;

  @NotNull
  @Basic
  @Column(name = "billing_account_id", nullable = false)
  private String billingAccountId;

  @NotNull
  @Basic
  @Column(name = "product_id", nullable = false)
  private String productId;

  @Basic
  @Column(name = "vendor_product_code", nullable = false)
  private String vendorProductCode;

  @NotEmpty
  @NotNull
  @Builder.Default
  @OneToMany(
      targetEntity = ContractMetricEntity.class,
      cascade = CascadeType.ALL,
      fetch = FetchType.LAZY,
      mappedBy = "contract")
  private Set<ContractMetricEntity> metrics = new HashSet<>();

  public void addMetrics(Set<ContractMetricEntity> metrics) {
    metrics.forEach(this::addMetric);
  }

  public void addMetric(ContractMetricEntity metric) {
    metrics.add(metric);
    metric.setContract(this);
    metric.setContractUuid(uuid);
  }

  public void removeMetric(ContractMetricEntity metric) {
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
        && Objects.equals(metrics, that.metrics)
        && Objects.equals(vendorProductCode, that.vendorProductCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        subscriptionNumber,
        orgId,
        sku,
        billingProvider,
        billingAccountId,
        productId,
        metrics,
        vendorProductCode);
  }

  public String getAzureResourceId() {
    if (!billingProvider.startsWith("azure") || billingProviderId == null) {
      return null;
    }
    return billingProviderId.split(";")[0];
  }

  public static Specification<ContractEntity> orgIdEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(ContractEntity_.orgId), orgId);
  }

  public static Specification<ContractEntity> productIdEquals(String productId) {
    return (root, query, builder) -> builder.equal(root.get(ContractEntity_.productId), productId);
  }

  public static Specification<ContractEntity> metricIdEquals(String metricId) {
    return (root, query, builder) ->
        builder.equal(
            root.join(ContractEntity_.metrics).get(ContractMetricEntity_.metricId), metricId);
  }

  public static Specification<ContractEntity> billingProviderEquals(String billingProvider) {
    return (root, query, builder) ->
        builder.equal(root.get(ContractEntity_.billingProvider), billingProvider);
  }

  public static Specification<ContractEntity> billingAccountIdEquals(String billingAccountId) {
    return (root, query, builder) ->
        builder.equal(root.get(ContractEntity_.billingAccountId), billingAccountId);
  }

  public static Specification<ContractEntity> subscriptionNumberEquals(String subscriptionNumber) {
    return (root, query, builder) ->
        builder.equal(root.get(ContractEntity_.subscriptionNumber), subscriptionNumber);
  }

  public static Specification<ContractEntity> vendorProductCodeEquals(String vendorProductCode) {
    return (root, query, builder) ->
        builder.equal(root.get(ContractEntity_.vendorProductCode), vendorProductCode);
  }

  public static Specification<ContractEntity> activeOn(OffsetDateTime timestamp) {
    return (root, query, builder) ->
        builder.and(
            builder.lessThanOrEqualTo(root.get(ContractEntity_.startDate), timestamp),
            builder.or(
                builder.isNull(root.get(ContractEntity_.endDate)),
                builder.greaterThan(root.get(ContractEntity_.endDate), timestamp)));
  }

  public static Specification<ContractEntity> activeDuringTimeRange(ContractEntity contract) {
    return (root, query, builder) -> {
      if (contract.getEndDate() != null) {
        return builder.and(
            builder.greaterThanOrEqualTo(
                root.get(ContractEntity_.startDate), contract.getStartDate()),
            builder.lessThanOrEqualTo(root.get(ContractEntity_.startDate), contract.getEndDate()));
      } else {
        return builder.greaterThanOrEqualTo(
            root.get(ContractEntity_.startDate), contract.getStartDate());
      }
    };
  }

  public static Specification<ContractEntity> azureResourceIdEquals(String azureResourceId) {
    return (root, query, builder) ->
        builder.like(root.get(ContractEntity_.billingProviderId), azureResourceId + "%");
  }

  public static Specification<ContractEntity> billingProviderIdEquals(String billingProviderId) {
    return (root, query, builder) ->
        builder.equal(root.get(ContractEntity_.billingProviderId), billingProviderId);
  }
}
