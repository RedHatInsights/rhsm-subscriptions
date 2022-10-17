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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
import javax.persistence.MapKeyClass;
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;

/** Model object to represent pieces of tally data. */
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "tally_snapshots")
public class TallySnapshot implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "snapshot_date")
  private OffsetDateTime snapshotDate;

  @Column(name = "product_id")
  private String productId;

  @Column(name = "owner_id")
  private String ownerId;

  @Column(name = "account_number")
  private String accountNumber;

  @Builder.Default
  @Column(name = "sla")
  private ServiceLevel serviceLevel = ServiceLevel._ANY;

  @Builder.Default
  @Column(name = "usage")
  private Usage usage = Usage._ANY;

  @Builder.Default
  @Column(name = "billing_provider")
  private BillingProvider billingProvider = BillingProvider._ANY;

  @Column(name = "billing_account_id")
  private String billingAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "granularity")
  private Granularity granularity;

  /** NOTE: to be removed w/ SWATCH-626 */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "hardware_measurements", joinColumns = @JoinColumn(name = "snapshot_id"))
  @MapKeyEnumerated(EnumType.STRING)
  @MapKeyColumn(name = "measurement_type")
  @Builder.Default
  private Map<HardwareMeasurementType, HardwareMeasurement> hardwareMeasurements =
      new EnumMap<>(HardwareMeasurementType.class);

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "tally_measurements", joinColumns = @JoinColumn(name = "snapshot_id"))
  @Column(name = "value")
  @MapKeyClass(TallyMeasurementKey.class)
  @Builder.Default
  private Map<TallyMeasurementKey, Double> tallyMeasurements = new HashMap<>();

  public int getMeasurementAsInteger(HardwareMeasurementType type, Measurement.Uom uom) {
    return Optional.ofNullable(getMeasurement(type, uom)).map(Double::intValue).orElse(0);
  }

  public Double getMeasurement(HardwareMeasurementType type, Measurement.Uom uom) {
    TallyMeasurementKey key = new TallyMeasurementKey(type, uom);
    return getTallyMeasurements().get(key);
  }

  public void setMeasurement(HardwareMeasurementType type, Measurement.Uom uom, Double value) {
    TallyMeasurementKey key = new TallyMeasurementKey(type, uom);
    tallyMeasurements.put(key, value);
  }

  public org.candlepin.subscriptions.utilization.api.model.TallySnapshot asApiSnapshot() {
    org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot =
        new org.candlepin.subscriptions.utilization.api.model.TallySnapshot();

    snapshot.setDate(this.getSnapshotDate());
    snapshot.setCores(this.getMeasurementAsInteger(HardwareMeasurementType.TOTAL, Uom.CORES));
    snapshot.setSockets(this.getMeasurementAsInteger(HardwareMeasurementType.TOTAL, Uom.SOCKETS));
    snapshot.setInstanceCount(
        this.getMeasurementAsInteger(HardwareMeasurementType.TOTAL, Uom.INSTANCES));

    snapshot.setPhysicalCores(
        this.getMeasurementAsInteger(HardwareMeasurementType.PHYSICAL, Uom.CORES));
    snapshot.setPhysicalSockets(
        this.getMeasurementAsInteger(HardwareMeasurementType.PHYSICAL, Uom.SOCKETS));
    snapshot.setPhysicalInstanceCount(
        this.getMeasurementAsInteger(HardwareMeasurementType.PHYSICAL, Uom.INSTANCES));

    // Sum "HYPERVISOR" and "VIRTUAL" records in the short term
    int totalVirtualCores = 0;
    int totalVirtualSockets = 0;
    int totalVirtualInstanceCount = 0;

    totalVirtualCores +=
        Optional.ofNullable(this.getMeasurement(HardwareMeasurementType.HYPERVISOR, Uom.CORES))
            .orElse(0.0);
    totalVirtualSockets +=
        Optional.ofNullable(this.getMeasurement(HardwareMeasurementType.HYPERVISOR, Uom.SOCKETS))
            .orElse(0.0);
    totalVirtualInstanceCount +=
        Optional.ofNullable(this.getMeasurement(HardwareMeasurementType.HYPERVISOR, Uom.INSTANCES))
            .orElse(0.0);

    totalVirtualCores +=
        Optional.ofNullable(this.getMeasurement(HardwareMeasurementType.VIRTUAL, Uom.CORES))
            .orElse(0.0);
    totalVirtualSockets +=
        Optional.ofNullable(this.getMeasurement(HardwareMeasurementType.VIRTUAL, Uom.SOCKETS))
            .orElse(0.0);
    totalVirtualInstanceCount +=
        Optional.ofNullable(this.getMeasurement(HardwareMeasurementType.VIRTUAL, Uom.INSTANCES))
            .orElse(0.0);

    snapshot.setHypervisorCores(totalVirtualCores);
    snapshot.setHypervisorSockets(totalVirtualSockets);
    snapshot.setHypervisorInstanceCount(totalVirtualInstanceCount);

    // Tally up all the cloud providers that we support. We count/store them separately in the DB
    // so that we can report on each provider if required in the future.
    int cloudInstances = 0;
    int cloudCores = 0;
    int cloudSockets = 0;
    Double cloudigradeMeasurement =
        getMeasurement(HardwareMeasurementType.AWS_CLOUDIGRADE, Uom.SOCKETS);
    snapshot.setHasCloudigradeData(cloudigradeMeasurement != null);
    for (HardwareMeasurementType type : HardwareMeasurementType.getCloudProviderTypes()) {
      Double measurement = getMeasurement(type, Uom.SOCKETS);
      if (cloudigradeMeasurement != null && type == HardwareMeasurementType.AWS) {
        if (measurement != null) {
          snapshot.setHasCloudigradeMismatch(!Objects.equals(cloudigradeMeasurement, measurement));
        }
        // if cloudigrade data exists, then HBI-derived AWS data is ignored
        continue;
      }
      if (measurement != null) {
        cloudInstances +=
            Optional.ofNullable(getMeasurement(type, Uom.INSTANCES))
                .map(Double::intValue)
                .orElse(0);
        cloudCores +=
            Optional.ofNullable(getMeasurement(type, Uom.CORES)).map(Double::intValue).orElse(0);
        cloudSockets +=
            Optional.ofNullable(getMeasurement(type, Uom.SOCKETS)).map(Double::intValue).orElse(0);
      }
    }
    snapshot.setCloudInstanceCount(cloudInstances);
    snapshot.setCloudCores(cloudCores);
    snapshot.setCloudSockets(cloudSockets);

    snapshot.setCoreHours(
        tallyMeasurements.get(new TallyMeasurementKey(HardwareMeasurementType.TOTAL, Uom.CORES)));
    snapshot.setInstanceHours(
        tallyMeasurements.get(
            new TallyMeasurementKey(HardwareMeasurementType.TOTAL, Uom.INSTANCE_HOURS)));

    // NOTE to be removed by SWATCH-626
    if (snapshotHasNoData(snapshot)) {
      readDataFromHardwareMeasurements(snapshot);
    }

    snapshot.setHasData(id != null);
    return snapshot;
  }

  // NOTE to be removed by SWATCH-626
  private boolean snapshotHasNoData(
      org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot) {
    double currentMeasurements = 0.0;
    currentMeasurements += snapshot.getCores();
    currentMeasurements += snapshot.getSockets();
    currentMeasurements += snapshot.getCloudCores();
    currentMeasurements += snapshot.getCloudSockets();
    currentMeasurements += snapshot.getHypervisorCores();
    currentMeasurements += snapshot.getHypervisorSockets();
    return currentMeasurements == 0.0;
  }

  // NOTE: to be removed by SWATCH-626
  private void readDataFromHardwareMeasurements(
      org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot) {
    HardwareMeasurement total = this.hardwareMeasurements.get(HardwareMeasurementType.TOTAL);
    if (total != null) {
      snapshot.setCores(total.getCores());
      snapshot.setSockets(total.getSockets());
      snapshot.setInstanceCount(total.getInstanceCount());
    }

    HardwareMeasurement physical = this.hardwareMeasurements.get(HardwareMeasurementType.PHYSICAL);
    if (physical != null) {
      snapshot.setPhysicalCores(physical.getCores());
      snapshot.setPhysicalSockets(physical.getSockets());
      snapshot.setPhysicalInstanceCount(physical.getInstanceCount());
    }

    HardwareMeasurement virtual = this.hardwareMeasurements.get(HardwareMeasurementType.VIRTUAL);
    HardwareMeasurement hypervisor =
        this.hardwareMeasurements.get(HardwareMeasurementType.HYPERVISOR);

    // Sum "HYPERVISOR" and "VIRTUAL" records in the short term
    int totalVirtualCores = 0;
    int totalVirtualSockets = 0;
    int totalVirtualInstanceCount = 0;

    if (virtual != null) {
      totalVirtualCores += virtual.getCores();
      totalVirtualSockets += virtual.getSockets();
      totalVirtualInstanceCount += virtual.getInstanceCount();
    }
    if (hypervisor != null) {
      totalVirtualCores += hypervisor.getCores();
      totalVirtualSockets += hypervisor.getSockets();
      totalVirtualInstanceCount += hypervisor.getInstanceCount();
    }
    if (hypervisor != null || virtual != null) {
      snapshot.setHypervisorCores(totalVirtualCores);
      snapshot.setHypervisorSockets(totalVirtualSockets);
      snapshot.setHypervisorInstanceCount(totalVirtualInstanceCount);
    }

    int cloudInstances = 0;
    int cloudCores = 0;
    int cloudSockets = 0;
    HardwareMeasurement cloudigradeMeasurement =
        this.hardwareMeasurements.get(HardwareMeasurementType.AWS_CLOUDIGRADE);
    snapshot.setHasCloudigradeData(cloudigradeMeasurement != null);
    for (HardwareMeasurementType type : HardwareMeasurementType.getCloudProviderTypes()) {
      HardwareMeasurement measurement = this.hardwareMeasurements.get(type);
      if (cloudigradeMeasurement != null && type == HardwareMeasurementType.AWS) {
        if (measurement != null) {
          snapshot.setHasCloudigradeMismatch(!Objects.equals(cloudigradeMeasurement, measurement));
        }
        // if cloudigrade data exists, then HBI-derived AWS data is ignored
        continue;
      }
      if (measurement != null) {
        cloudInstances += measurement.getInstanceCount();
        cloudCores += measurement.getCores();
        cloudSockets += measurement.getSockets();
      }
    }
    snapshot.setCloudInstanceCount(cloudInstances);
    snapshot.setCloudCores(cloudCores);
    snapshot.setCloudSockets(cloudSockets);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TallySnapshot)) {
      return false;
    }
    TallySnapshot that = (TallySnapshot) o;
    return Objects.equals(snapshotDate, that.snapshotDate)
        && Objects.equals(productId, that.productId)
        && Objects.equals(ownerId, that.ownerId)
        && Objects.equals(accountNumber, that.accountNumber)
        && serviceLevel == that.serviceLevel
        && usage == that.usage
        && granularity == that.granularity
        && billingProvider == that.billingProvider
        && Objects.equals(billingAccountId, that.billingAccountId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        snapshotDate,
        productId,
        ownerId,
        accountNumber,
        serviceLevel,
        usage,
        granularity,
        billingProvider,
        billingAccountId);
  }
}
