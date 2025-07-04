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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents a reported Host from inventory. This entity stores normalized facts for a Host
 * returned from HBI.
 */
@Setter
@Getter
@Entity
@ToString
@Table(name = "hosts")
public class Host implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  /** The canonical natural identifier for this instance. */
  @Column(name = "instance_id")
  private String instanceId;

  @Column(name = "inventory_id")
  private String inventoryId;

  @Column(name = "insights_id")
  private String insightsId;

  @NotNull
  @Column(name = "display_name", nullable = false)
  private String displayName;

  @NotNull
  @Column(name = "org_id")
  private String orgId;

  @Column(name = "subscription_manager_id")
  private String subscriptionManagerId;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "instance_measurements", joinColumns = @JoinColumn(name = "host_id"))
  @MapKeyColumn(name = "metric_id")
  @Column(name = "value")
  private Map<String, Double> measurements = new HashMap<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "instance_monthly_totals", joinColumns = @JoinColumn(name = "host_id"))
  @Column(name = "value")
  private Map<InstanceMonthlyTotalKey, Double> monthlyTotals = new HashMap<>();

  @Column(name = "is_guest")
  private boolean guest;

  @Column(name = "hypervisor_uuid")
  private String hypervisorUuid;

  @Enumerated(EnumType.STRING)
  @Column(name = "hardware_type")
  private HostHardwareType hardwareType;

  @Column(name = "num_of_guests")
  private Integer numOfGuests;

  @Column(name = "last_seen")
  private OffsetDateTime lastSeen;

  @OneToMany(
      mappedBy = "host",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private Set<HostTallyBucket> buckets = new HashSet<>();

  @Column(name = "is_unmapped_guest")
  private boolean isUnmappedGuest;

  @Column(name = "is_hypervisor")
  private boolean isHypervisor;

  @Column(name = "cloud_provider")
  private String cloudProvider;

  /** The instance type represented by this record. */
  @Column(name = "instance_type")
  private String instanceType;

  /*
   * We have billingProvider and  billingAccountId redundantly here, anticipating that we will want
   * to display a host's billing provider when the billing provider filter is set to _ANY
   */
  @Column(name = "billing_provider")
  private BillingProvider billingProvider;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  /**
   * Used to track the last Event that was applied to the monthly totals. The expected value will be
   * Event.recordDate.
   */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "host_tally_service_type",
      joinColumns = @JoinColumn(name = "host_id", referencedColumnName = "id"))
  @MapKeyColumn(name = "service_type")
  @Column(name = "last_applied_event_record_date")
  private Map<String, OffsetDateTime> lastAppliedEventRecordDateByServiceType = new HashMap<>();

  public Host() {}

  public Host(String inventoryId, String insightsId, String orgId, String subManId) {
    this.instanceType = "HBI_HOST";
    this.inventoryId = inventoryId;
    this.instanceId = inventoryId;
    this.insightsId = insightsId;
    this.orgId = orgId;
    this.subscriptionManagerId = subManId;
  }

  public boolean isMetered() {
    boolean metered = false;
    Set<HostTallyBucket> bucketSet = this.getBuckets();
    if (bucketSet == null || bucketSet.isEmpty()) {
      return false;
    }
    for (HostTallyBucket bucket : bucketSet) {
      if (bucket.getKey() != null) {
        Optional<SubscriptionDefinition> subscriptionDefinition =
            SubscriptionDefinition.lookupSubscriptionByTag(bucket.getKey().getProductId());
        metered =
            metered
                || subscriptionDefinition.isPresent()
                    && subscriptionDefinition.get().isPaygEligible();
      }
    }
    return metered;
  }

  public Double getMeasurement(String metricId) {
    return measurements.get(MetricIdUtils.toUpperCaseFormatted(metricId));
  }

  public void setMeasurement(String metricId, Double value) {
    measurements.put(MetricIdUtils.toUpperCaseFormatted(metricId), value);
  }

  @SuppressWarnings("java:S107")
  public HostTallyBucket addBucket(
      String productId,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      Boolean asHypervisor,
      Integer sockets,
      Integer cores,
      HardwareMeasurementType measurementType) {

    HostTallyBucket bucket =
        new HostTallyBucket(
            this,
            productId,
            sla,
            usage,
            billingProvider,
            billingAccountId,
            asHypervisor,
            cores,
            sockets,
            measurementType);
    addBucket(bucket);
    return bucket;
  }

  public void addBucket(HostTallyBucket bucket) {
    // NOTE: host must be set first, otherwise we'd treat a persistent bucket as not equal to a new
    // bucket having the same key otherwise
    bucket.setHost(this);
    Optional<HostTallyBucket> existingBucket =
        buckets.stream().filter(b -> b.getKey().equals(bucket.getKey())).findFirst();
    // if the bucket key already exists, then we should update the existing values, instead of
    // creating a redundant entry
    if (existingBucket.isPresent()) {
      HostTallyBucket b = existingBucket.get();
      b.setSockets(bucket.getSockets());
      b.setCores(bucket.getCores());
      b.setMeasurementType(bucket.getMeasurementType());
      b.setStale(false);
    } else {
      bucket.setHost(this);
      getBuckets().add(bucket);
    }
  }

  public void removeBucket(HostTallyBucket bucket) {
    getBuckets().remove(bucket);
  }

  public Double getMonthlyTotal(String monthId, MetricId metricId) {
    var key = new InstanceMonthlyTotalKey(monthId, metricId);
    return monthlyTotals.get(key);
  }

  public void addToMonthlyTotal(String monthId, MetricId metricId, Double value) {
    var key = new InstanceMonthlyTotalKey(monthId, metricId);
    Double currentValue = monthlyTotals.getOrDefault(key, 0.0);
    monthlyTotals.put(key, currentValue + value);
  }

  public void addToMonthlyTotal(OffsetDateTime timestamp, MetricId metricId, Double value) {
    var key = new InstanceMonthlyTotalKey(timestamp, metricId);
    Double currentValue = monthlyTotals.getOrDefault(key, 0.0);
    monthlyTotals.put(key, currentValue + value);
  }

  public void clearMonthlyTotals(OffsetDateTime startDateTime, OffsetDateTime endDateTime) {
    for (OffsetDateTime offset = startDateTime;
        !offset.isAfter(endDateTime);
        offset = offset.plusMonths(1)) {
      clearMonthlyTotal(offset);
    }
  }

  public void clearMonthlyTotal(OffsetDateTime timestamp) {
    String monthIdentifier = InstanceMonthlyTotalKey.formatMonthId(timestamp);
    Set<InstanceMonthlyTotalKey> keys = monthlyTotals.keySet();
    // ENT-4622 here we set the value to 0, rather than clear, because clearing can cause delete
    // and re-insert to be issued whenever flushing happens. Attempting to insert equivalent rows
    // in two different concurrent transactions may cause constraint violations.
    keys.stream()
        .filter(key -> Objects.equals(key.getMonth(), monthIdentifier))
        .forEach(key -> monthlyTotals.put(key, 0.0));
  }

  public OffsetDateTime getLastAppliedEventRecordDate(String serviceType) {
    return lastAppliedEventRecordDateByServiceType.get(serviceType);
  }

  public void setLastAppliedEventRecordDate(String serviceType, OffsetDateTime recordDate) {
    lastAppliedEventRecordDateByServiceType.put(serviceType, recordDate);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Host)) {
      return false;
    }
    Host host = (Host) o;
    return guest == host.guest
        && isUnmappedGuest == host.isUnmappedGuest
        && isHypervisor == host.isHypervisor
        && Objects.equals(id, host.id)
        && Objects.equals(inventoryId, host.inventoryId)
        && Objects.equals(insightsId, host.insightsId)
        && Objects.equals(displayName, host.displayName)
        && Objects.equals(orgId, host.orgId)
        && Objects.equals(subscriptionManagerId, host.subscriptionManagerId)
        && Objects.equals(hypervisorUuid, host.hypervisorUuid)
        && hardwareType == host.hardwareType
        && Objects.equals(numOfGuests, host.numOfGuests)
        && Objects.equals(lastSeen, host.lastSeen)
        && Objects.equals(buckets, host.buckets)
        && Objects.equals(cloudProvider, host.cloudProvider)
        && Objects.equals(instanceId, host.instanceId)
        && Objects.equals(billingAccountId, host.billingAccountId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        inventoryId,
        insightsId,
        displayName,
        orgId,
        subscriptionManagerId,
        guest,
        hypervisorUuid,
        hardwareType,
        numOfGuests,
        lastSeen,
        buckets,
        isUnmappedGuest,
        isHypervisor,
        cloudProvider,
        instanceId,
        instanceType,
        billingAccountId);
  }
}
