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
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

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
  @Column(name = "account_number", nullable = false)
  private String accountNumber;

  @Column(name = "org_id")
  private String orgId;

  @Column(name = "subscription_manager_id")
  private String subscriptionManagerId;

  /** @deprecated use measurements instead */
  @Deprecated(forRemoval = true)
  private Integer cores;

  /** @deprecated use measurements instead */
  @Deprecated(forRemoval = true)
  private Integer sockets;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "instance_measurements", joinColumns = @JoinColumn(name = "instance_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "uom")
  @Column(name = "value")
  private Map<Measurement.Uom, Double> measurements = new EnumMap<>(Measurement.Uom.class);

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "instance_monthly_totals",
      joinColumns = @JoinColumn(name = "instance_id"))
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

  public Host() {}

  public Host(
      String inventoryId, String insightsId, String accountNumber, String orgId, String subManId) {
    this.instanceType = "HBI_HOST";
    this.inventoryId = inventoryId;
    this.instanceId = inventoryId;
    this.insightsId = insightsId;
    this.accountNumber = accountNumber;
    this.orgId = orgId;
    this.subscriptionManagerId = subManId;
  }

  public Host(InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts) {
    this.instanceType = "HBI_HOST";
    populateFieldsFromHbi(inventoryHostFacts, normalizedFacts);
  }

  public void populateFieldsFromHbi(
      InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts) {

    if (inventoryHostFacts.getInventoryId() != null) {
      this.inventoryId = inventoryHostFacts.getInventoryId().toString();
      // We assume that the instance ID for any given HBI host record is the inventory ID; compare
      // to
      // an OpenShift Cluster from Prometheus data, where we use the cluster ID.
      this.instanceId = inventoryHostFacts.getInventoryId().toString();
    }

    this.insightsId = inventoryHostFacts.getInsightsId();
    this.accountNumber = inventoryHostFacts.getAccount();
    this.orgId = inventoryHostFacts.getOrgId();
    this.displayName = inventoryHostFacts.getDisplayName();
    this.subscriptionManagerId = inventoryHostFacts.getSubscriptionManagerId();
    this.guest = normalizedFacts.isVirtual();
    this.hypervisorUuid = normalizedFacts.getHypervisorUuid();

    if (normalizedFacts.getCores() != null) {
      this.measurements.put(Measurement.Uom.CORES, normalizedFacts.getCores().doubleValue());
    }

    if (normalizedFacts.getSockets() != null) {
      this.measurements.put(Measurement.Uom.SOCKETS, normalizedFacts.getSockets().doubleValue());
    }

    this.isHypervisor = normalizedFacts.isHypervisor();
    this.isUnmappedGuest = normalizedFacts.isVirtual() && normalizedFacts.isHypervisorUnknown();
    this.cloudProvider =
        normalizedFacts.getCloudProviderType() == null
            ? null
            : normalizedFacts.getCloudProviderType().name();

    this.lastSeen = inventoryHostFacts.getModifiedOn();
    this.hardwareType = normalizedFacts.getHardwareType();
  }

  /**
   * @deprecated use getMeasurement(Measurement.Uom.CORES) instead
   * @return effective cores measured on the instance
   */
  @Deprecated(forRemoval = true)
  public Integer getCores() {
    return Optional.ofNullable(measurements.get(Measurement.Uom.CORES))
        .map(Double::intValue)
        .orElse(cores);
  }

  /** @deprecated use setMeasurement(Measurement.Uom.CORES, value) instead */
  @Deprecated(forRemoval = true)
  public void setCores(Integer cores) {
    this.cores = cores;
  }

  /**
   * @deprecated use getMeasurement(Measurement.Uom.SOCKETS) instead
   * @return effective sockets measured on the instance
   */
  @Deprecated(forRemoval = true)
  public Integer getSockets() {
    return Optional.ofNullable(measurements.get(Measurement.Uom.SOCKETS))
        .map(Double::intValue)
        .orElse(sockets);
  }

  /** @deprecated use setMeasurement(Measurement.Uom.SOCKETS, value) instead */
  @Deprecated(forRemoval = true)
  public void setSockets(Integer sockets) {
    this.sockets = sockets;
  }

  public Double getMeasurement(Measurement.Uom uom) {
    return measurements.get(uom);
  }

  public void setMeasurement(Measurement.Uom uom, Double value) {
    measurements.put(uom, value);
  }

  public HostTallyBucket addBucket(
      String productId,
      ServiceLevel sla,
      Usage usage,
      Boolean asHypervisor,
      int sockets,
      int cores,
      HardwareMeasurementType measurementType) {

    HostTallyBucket bucket =
        new HostTallyBucket(
            this, productId, sla, usage, asHypervisor, cores, sockets, measurementType);
    addBucket(bucket);
    return bucket;
  }

  public void addBucket(HostTallyBucket bucket) {
    bucket.setHost(this);
    getBuckets().add(bucket);
  }

  public void removeBucket(HostTallyBucket bucket) {
    getBuckets().remove(bucket);
  }

  public Double getMonthlyTotal(String monthId, Measurement.Uom uom) {
    var key = new InstanceMonthlyTotalKey(monthId, uom);
    return monthlyTotals.get(key);
  }

  public Double getMonthlyTotal(OffsetDateTime reference, Measurement.Uom uom) {
    var key = new InstanceMonthlyTotalKey(reference, uom);
    return monthlyTotals.get(key);
  }

  public void addToMonthlyTotal(String monthId, Measurement.Uom uom, Double value) {
    var key = new InstanceMonthlyTotalKey(monthId, uom);
    Double currentValue = monthlyTotals.getOrDefault(key, 0.0);
    monthlyTotals.put(key, currentValue + value);
  }

  public void addToMonthlyTotal(OffsetDateTime timestamp, Measurement.Uom uom, Double value) {
    var key = new InstanceMonthlyTotalKey(timestamp, uom);
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
    keys.removeIf(key -> Objects.equals(key.getMonth(), monthIdentifier));
  }

  public org.candlepin.subscriptions.utilization.api.model.Host asApiHost() {
    return new org.candlepin.subscriptions.utilization.api.model.Host()
        .cores(cores)
        .sockets(sockets)
        .displayName(displayName)
        .hardwareType(hardwareType.toString())
        .insightsId(insightsId)
        .inventoryId(inventoryId)
        .subscriptionManagerId(subscriptionManagerId)
        .lastSeen(lastSeen)
        .numberOfGuests(numOfGuests)
        .isUnmappedGuest(isUnmappedGuest)
        .isHypervisor(isHypervisor)
        .cloudProvider(cloudProvider);
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
        && Objects.equals(accountNumber, host.accountNumber)
        && Objects.equals(orgId, host.orgId)
        && Objects.equals(subscriptionManagerId, host.subscriptionManagerId)
        && Objects.equals(cores, host.cores)
        && Objects.equals(sockets, host.sockets)
        && Objects.equals(hypervisorUuid, host.hypervisorUuid)
        && hardwareType == host.hardwareType
        && Objects.equals(numOfGuests, host.numOfGuests)
        && Objects.equals(lastSeen, host.lastSeen)
        && Objects.equals(buckets, host.buckets)
        && Objects.equals(cloudProvider, host.cloudProvider)
        && Objects.equals(instanceId, host.instanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        inventoryId,
        insightsId,
        displayName,
        accountNumber,
        orgId,
        subscriptionManagerId,
        cores,
        sockets,
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
        instanceType);
  }

  public org.candlepin.subscriptions.utilization.api.model.Host asTallyHostViewApiHost(
      String monthId) {
    var host = new org.candlepin.subscriptions.utilization.api.model.Host();

    host.inventoryId(getInventoryId());
    host.insightsId(getInsightsId());

    host.hardwareType(
        Objects.requireNonNullElse(getHardwareType(), HostHardwareType.PHYSICAL).toString());
    host.cores(Objects.requireNonNullElse(getMeasurement(Measurement.Uom.CORES), 0.0).intValue());
    host.sockets(
        Objects.requireNonNullElse(getMeasurement(Measurement.Uom.SOCKETS), 0.0).intValue());

    host.displayName(getDisplayName());
    host.subscriptionManagerId(getSubscriptionManagerId());
    host.numberOfGuests(getNumOfGuests());
    host.lastSeen(getLastSeen());
    host.isUnmappedGuest(isUnmappedGuest());
    host.cloudProvider(getCloudProvider());

    // These generally come off of the TallyHostBuckets, but it's different for the
    // OpenShift-metrics
    // and OpenShift-dedicated-metrics products, since they're not using the deprecated unit of
    // measure
    // model.  Note there's no asHypervisor here either.

    host.isHypervisor(isHypervisor());

    HardwareMeasurementType measurementType =
        buckets.stream().findFirst().orElseThrow().getMeasurementType();

    host.measurementType(
        Objects.requireNonNullElse(measurementType, HardwareMeasurementType.PHYSICAL).toString());

    // Core Hours is currently only applicable to the OpenShift-metrics OpenShift-dedicated-metrics
    // ProductIDs, and the UI is only query the host api in one month timeframes.  If the
    // granularity of that API changes in the future, other work will have to be done first to
    // capture relationships between hosts & snapshots to derive coreHours within dynamic timeframes

    host.coreHours(getMonthlyTotal(monthId, Uom.CORES));
    host.instanceHours(getMonthlyTotal(monthId, Uom.INSTANCE_HOURS));

    return host;
  }
}
