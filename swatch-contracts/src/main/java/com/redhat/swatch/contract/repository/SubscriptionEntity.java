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

import com.redhat.swatch.panache.Specification;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** SubscriptionEntity entities represent data from a Candlepin Pool */
@Entity
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@IdClass(SubscriptionEntity.SubscriptionCompoundId.class)
@Table(name = "subscription")
public class SubscriptionEntity implements Serializable {

  @Id
  @Column(name = "subscription_id")
  private String subscriptionId;

  @Column(name = "subscription_number")
  private String subscriptionNumber;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "sku")
  private OfferingEntity offering;

  @Column(name = "org_id")
  private String orgId;

  @Column(name = "quantity")
  private long quantity;

  @Id
  @Column(name = "start_date")
  private OffsetDateTime startDate;

  @Column(name = "end_date")
  private OffsetDateTime endDate;

  @Column(name = "billing_provider_id")
  private String billingProviderId;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Column(name = "billing_provider")
  private BillingProvider billingProvider;

  @OneToMany(
      mappedBy = "subscription",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @Builder.Default
  @ToString.Exclude // Excluded to prevent fetching a lazy-loaded collection
  private Set<SubscriptionMeasurementEntity> subscriptionMeasurements = new HashSet<>();

  public Optional<SubscriptionMeasurementEntity> getSubscriptionMeasurement(
      String metricId, String measurementType) {
    return subscriptionMeasurements.stream()
        .filter(
            m -> m.getMeasurementType().equals(measurementType) && m.getMetricId().equals(metricId))
        .findFirst();
  }

  public void removeMeasurement(SubscriptionMeasurementEntity measurement) {
    subscriptionMeasurements.remove(measurement);
    measurement.setSubscription(null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubscriptionEntity sub)) {
      return false;
    }

    return Objects.equals(subscriptionId, sub.getSubscriptionId())
        && Objects.equals(subscriptionNumber, sub.getSubscriptionNumber())
        && Objects.equals(orgId, sub.getOrgId())
        && Objects.equals(quantity, sub.getQuantity())
        && Objects.equals(startDate, sub.getStartDate())
        && Objects.equals(endDate, sub.getEndDate())
        && Objects.equals(billingProviderId, sub.getBillingProviderId())
        && Objects.equals(billingAccountId, sub.getBillingAccountId())
        && Objects.equals(billingProvider, sub.getBillingProvider())
        && Objects.equals(offering, sub.getOffering());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        subscriptionId,
        subscriptionNumber,
        orgId,
        quantity,
        startDate,
        endDate,
        billingProviderId,
        billingAccountId,
        billingProvider,
        offering);
  }

  /** Composite ID class for SubscriptionEntity entities. */
  @Getter
  @Setter
  public static class SubscriptionCompoundId implements Serializable {
    private String subscriptionId;
    private OffsetDateTime startDate;

    public SubscriptionCompoundId(String subscriptionId, OffsetDateTime startDate) {
      this.subscriptionId = subscriptionId;
      this.startDate = startDate;
    }

    public SubscriptionCompoundId() {
      // default
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof SubscriptionEntity subscription)) {
        return false;
      }

      return Objects.equals(subscriptionId, subscription.getSubscriptionId())
          && Objects.equals(startDate, subscription.getStartDate());
    }

    @Override
    public int hashCode() {
      return Objects.hash(subscriptionId, startDate);
    }
  }

  public void addSubscriptionMeasurement(SubscriptionMeasurementEntity sm) {
    sm.setSubscription(this);
    subscriptionMeasurements.add(sm);
  }

  public static Specification<SubscriptionEntity> forContract(ContractEntity contract) {
    return (root, query, builder) ->
        builder.and(
            builder.equal(
                root.get(SubscriptionEntity_.subscriptionNumber), contract.getSubscriptionNumber()),
            builder.equal(root.get(SubscriptionEntity_.startDate), contract.getStartDate()));
  }
}
