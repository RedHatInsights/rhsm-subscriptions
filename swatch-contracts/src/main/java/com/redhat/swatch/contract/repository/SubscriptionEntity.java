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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.ObjectUtils;

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

  @Builder.Default
  @ElementCollection
  @CollectionTable(
      name = "subscription_measurements",
      joinColumns = {
        @JoinColumn(name = "subscription_id", referencedColumnName = "subscription_id"),
        @JoinColumn(name = "start_date", referencedColumnName = "start_date")
      })
  @Column(name = "value")
  @ToString.Exclude
  private Map<SubscriptionMeasurementKey, Double> subscriptionMeasurements = new HashMap<>();

  public Double getSubscriptionMeasurement(String metricId, String measurementType) {
    return subscriptionMeasurements.get(new SubscriptionMeasurementKey(metricId, measurementType));
  }

  public void addSubscriptionMeasurement(String metricId, String measurementType, Double value) {
    subscriptionMeasurements.put(new SubscriptionMeasurementKey(metricId, measurementType), value);
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
  @ToString
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
      if (!(o instanceof SubscriptionCompoundId subscription)) {
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

  public boolean quantityHasChanged(long newQuantity) {
    return this.getQuantity() != newQuantity;
  }

  public void endSubscription() {
    endDate = OffsetDateTime.now();
  }

  public static Specification<SubscriptionEntity> forContract(ContractEntity contract) {
    return (root, query, builder) ->
        builder.and(
            builder.equal(
                root.get(SubscriptionEntity_.subscriptionNumber), contract.getSubscriptionNumber()),
            builder.equal(root.get(SubscriptionEntity_.startDate), contract.getStartDate()));
  }

  public static Specification<SubscriptionEntity> hasUnlimitedUsage() {
    return (root, query, builder) -> {
      var offeringRoot = root.get(SubscriptionEntity_.offering);
      return builder.equal(offeringRoot.get(OfferingEntity_.hasUnlimitedUsage), true);
    };
  }

  private static Specification<SubscriptionEntity> hasBillingProviderId() {
    return (root, query, builder) ->
        builder.and(
            builder.isNotNull(root.get(SubscriptionEntity_.billingProviderId)),
            builder.notEqual(root.get(SubscriptionEntity_.billingProviderId), ""));
  }

  private static Specification<SubscriptionEntity> productTagEquals(String productTag) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(SubscriptionEntity_.offering);
      return builder.isMember(productTag, offeringRoot.get(OfferingEntity_.productTags));
    };
  }

  private static Specification<SubscriptionEntity> orgIdEquals(String orgId) {
    return (root, query, builder) -> builder.equal(root.get(SubscriptionEntity_.orgId), orgId);
  }

  /**
   * This method looks for subscriptions that are active between the two dates given. The logic is
   * not intuitive: SubscriptionEntity_begin &lt;= report_end && (SubscriptionEntity_end &gt;=
   * report_begin OR SubscriptionEntity_end IS NULL).
   *
   * <p>There are four points that need to be considered: the subscription begin date (Sb), the
   * subscription end date (Se), the report begin date (Rb), and the report end date (Re). Those
   * dates can be in five different relationships.
   *
   * <ol>
   *   <li>Sb Se Rb Re (a subscription that expires before the report period even starts
   *   <li>Sb Rb Se Re (a subscription that has started before the report period and ends during it.
   *   <li>Rb Sb Se Re (a subscription that falls entirely within the report period)
   *   <li>Rb Sb Re Se (a subscription that starts inside the report period but continues past the
   *       end of the period)
   *   <li>Rb Re Sb Se (a subscription that does not start until after the period has already ended)
   * </ol>
   *
   * <p>We want this method to return subscriptions that are active within the report period. That
   * means cases 2, 3, and 4. Here are the relationships for those cases:
   *
   * <ol>
   *   <li>Sb &lt; Rb, Sb &lt; Re, Se &gt; Rb, Se &lt; Re
   *   <li>Sb &gt; Rb, Sb &lt; Re, Se &gt; Rb, Se &lt; Re
   *   <li>Sb &gt; Rb, Sb &lt; Re, Se &gt; Rb, Se &gt; Re
   * </ol>
   *
   * <p>Looking at those inequalities, we can see that the two invariant relationships are Sb &lt;
   * Re and Se &gt; Rb. Then we add the "or equal to" to the inequalities to capture edge cases.
   *
   * @param reportStart the date the reporting period starts
   * @param reportEnd the date the reporting period ends
   * @return A Specification that determines if a subscription is active during the given period.
   */
  private static Specification<SubscriptionEntity> subscriptionIsActiveBetween(
      OffsetDateTime reportStart, OffsetDateTime reportEnd) {
    return (root, query, builder) ->
        predicateForSubscriptionIsActiveBetween(root, builder, reportStart, reportEnd);
  }

  private static Predicate predicateForSubscriptionIsActiveBetween(
      Path<SubscriptionEntity> path,
      CriteriaBuilder builder,
      OffsetDateTime reportStart,
      OffsetDateTime reportEnd) {
    var predicates = new ArrayList<Predicate>();
    if (Objects.nonNull(reportEnd)) {
      predicates.add(builder.lessThanOrEqualTo(path.get(SubscriptionEntity_.startDate), reportEnd));
    }
    if (Objects.nonNull(reportStart)) {
      predicates.add(
          builder.or(
              builder.isNull(path.get(SubscriptionEntity_.endDate)),
              builder.greaterThanOrEqualTo(path.get(SubscriptionEntity_.endDate), reportStart)));
    }
    return builder.and(predicates.toArray(Predicate[]::new));
  }

  private static Specification<SubscriptionEntity> slaEquals(ServiceLevel sla) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(SubscriptionEntity_.offering);
      return builder.equal(offeringRoot.get(OfferingEntity_.serviceLevel), sla);
    };
  }

  private static Specification<SubscriptionEntity> usageEquals(Usage usage) {
    return (root, query, builder) -> {
      var offeringRoot = root.get(SubscriptionEntity_.offering);
      return builder.equal(offeringRoot.get(OfferingEntity_.usage), usage);
    };
  }

  private static Specification<SubscriptionEntity> billingProviderEquals(
      BillingProvider billingProvider) {
    return (root, query, builder) ->
        builder.equal(root.get(SubscriptionEntity_.billingProvider), billingProvider);
  }

  private static Specification<SubscriptionEntity> billingAccountIdLike(String billingAccountId) {
    return (root, query, builder) ->
        // If multiple ID's exist, match on firstID or firstID;secondID (azureTenantId or
        // azureTenantId;azureSubscriptionId)
        builder.like(root.get(SubscriptionEntity_.billingAccountId), billingAccountId + "%");
  }

  private static Specification<SubscriptionEntity> metricsCriteria(
      HypervisorReportCategory hypervisorReportCategory, String metricId) {
    return (root, query, builder) -> {
      var predicates = new ArrayList<Predicate>();
      var measurementKeyPath = root.join(SubscriptionEntity_.subscriptionMeasurements).key();
      if (hypervisorReportCategory != null) {
        var measurementType =
            switch (hypervisorReportCategory) {
              case NON_HYPERVISOR -> "PHYSICAL";
              case HYPERVISOR -> "HYPERVISOR";
            };
        predicates.add(
            builder.equal(
                measurementKeyPath.get(SubscriptionMeasurementKey_.measurementType),
                measurementType));
      }
      if (metricId != null) {
        predicates.add(
            builder.equal(measurementKeyPath.get(SubscriptionMeasurementKey_.metricId), metricId));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  public static Specification<SubscriptionEntity> buildSearchSpecification(
      DbReportCriteria dbReportCriteria) {
    Specification<SubscriptionEntity> searchCriteria =
        subscriptionIsActiveBetween(dbReportCriteria.getBeginning(), dbReportCriteria.getEnding());
    if (Objects.nonNull(dbReportCriteria.getOrgId())) {
      searchCriteria = searchCriteria.and(orgIdEquals(dbReportCriteria.getOrgId()));
    }
    if (dbReportCriteria.isPayg()) {
      // NOTE: we expect payg subscription records to always populate billingProviderId
      searchCriteria = searchCriteria.and(hasBillingProviderId());
    }
    if (!ObjectUtils.isEmpty(dbReportCriteria.getProductTag())) {
      searchCriteria = searchCriteria.and(productTagEquals(dbReportCriteria.getProductTag()));
    }
    if (Objects.nonNull(dbReportCriteria.getServiceLevel())
        && !dbReportCriteria.getServiceLevel().equals(ServiceLevel._ANY)) {
      searchCriteria = searchCriteria.and(slaEquals(dbReportCriteria.getServiceLevel()));
    }
    if (Objects.nonNull(dbReportCriteria.getUsage())
        && !dbReportCriteria.getUsage().equals(Usage._ANY)) {
      searchCriteria = searchCriteria.and(usageEquals(dbReportCriteria.getUsage()));
    }
    if (Objects.nonNull(dbReportCriteria.getBillingProvider())
        && !dbReportCriteria.getBillingProvider().equals(BillingProvider._ANY)) {
      searchCriteria =
          searchCriteria.and(billingProviderEquals(dbReportCriteria.getBillingProvider()));
    }
    if (Objects.nonNull(dbReportCriteria.getBillingAccountId())
        && !dbReportCriteria.getBillingAccountId().equals("_ANY")) {
      searchCriteria =
          searchCriteria.and(billingAccountIdLike(dbReportCriteria.getBillingAccountId()));
    }
    if (Objects.nonNull(dbReportCriteria.getMetricId())
        || Objects.nonNull(dbReportCriteria.getHypervisorReportCategory())) {
      searchCriteria =
          searchCriteria.and(
              metricsCriteria(
                  dbReportCriteria.getHypervisorReportCategory(), dbReportCriteria.getMetricId()));
    }

    return searchCriteria;
  }
}
