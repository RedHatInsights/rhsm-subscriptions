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
import com.redhat.swatch.configuration.util.MetricIdUtils;
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
import jakarta.persistence.MapKeyClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

  @Column(name = "org_id")
  private String orgId;

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

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "tally_measurements", joinColumns = @JoinColumn(name = "snapshot_id"))
  @Column(name = "value")
  @MapKeyClass(TallyMeasurementKey.class)
  @Builder.Default
  private Map<TallyMeasurementKey, Double> tallyMeasurements = new HashMap<>();

  public int getMeasurementAsInteger(HardwareMeasurementType type, MetricId uom) {
    return Optional.ofNullable(getMeasurement(type, uom)).map(Double::intValue).orElse(0);
  }

  public Double getMeasurement(HardwareMeasurementType type, MetricId metricId) {
    TallyMeasurementKey key = new TallyMeasurementKey(type, metricId.getValue());
    return getTallyMeasurements().get(key);
  }

  public void setMeasurement(HardwareMeasurementType type, MetricId metricId, Double value) {
    TallyMeasurementKey key = new TallyMeasurementKey(type, metricId.getValue());
    tallyMeasurements.put(key, value);
  }

  public org.candlepin.subscriptions.utilization.api.model.TallySnapshot asApiSnapshot() {
    org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot =
        new org.candlepin.subscriptions.utilization.api.model.TallySnapshot();

    snapshot.setDate(this.getSnapshotDate());
    snapshot.setCores(
        this.getMeasurementAsInteger(HardwareMeasurementType.TOTAL, MetricIdUtils.getCores()));
    snapshot.setSockets(
        this.getMeasurementAsInteger(HardwareMeasurementType.TOTAL, MetricIdUtils.getSockets()));
    snapshot.setInstanceCount(
        this.getMeasurementAsInteger(
            HardwareMeasurementType.TOTAL, MetricIdUtils.getInstanceHours()));

    snapshot.setPhysicalCores(
        this.getMeasurementAsInteger(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getCores()));
    snapshot.setPhysicalSockets(
        this.getMeasurementAsInteger(HardwareMeasurementType.PHYSICAL, MetricIdUtils.getSockets()));
    snapshot.setPhysicalInstanceCount(
        this.getMeasurementAsInteger(
            HardwareMeasurementType.PHYSICAL, MetricIdUtils.getInstanceHours()));

    // Sum "HYPERVISOR" and "VIRTUAL" records in the short term
    int totalVirtualCores = 0;
    int totalVirtualSockets = 0;
    int totalVirtualInstanceCount = 0;

    totalVirtualCores +=
        Optional.ofNullable(
                this.getMeasurement(HardwareMeasurementType.HYPERVISOR, MetricIdUtils.getCores()))
            .orElse(0.0);
    totalVirtualSockets +=
        Optional.ofNullable(
                this.getMeasurement(HardwareMeasurementType.HYPERVISOR, MetricIdUtils.getSockets()))
            .orElse(0.0);
    totalVirtualInstanceCount +=
        Optional.ofNullable(
                this.getMeasurement(
                    HardwareMeasurementType.HYPERVISOR, MetricIdUtils.getInstanceHours()))
            .orElse(0.0);

    totalVirtualCores +=
        Optional.ofNullable(
                this.getMeasurement(HardwareMeasurementType.VIRTUAL, MetricIdUtils.getCores()))
            .orElse(0.0);
    totalVirtualSockets +=
        Optional.ofNullable(
                this.getMeasurement(HardwareMeasurementType.VIRTUAL, MetricIdUtils.getSockets()))
            .orElse(0.0);
    totalVirtualInstanceCount +=
        Optional.ofNullable(
                this.getMeasurement(
                    HardwareMeasurementType.VIRTUAL, MetricIdUtils.getInstanceHours()))
            .orElse(0.0);

    snapshot.setHypervisorCores(totalVirtualCores);
    snapshot.setHypervisorSockets(totalVirtualSockets);
    snapshot.setHypervisorInstanceCount(totalVirtualInstanceCount);

    // Tally up all the cloud providers that we support. We count/store them separately in the DB
    // so that we can report on each provider if required in the future.
    int cloudInstances = 0;
    int cloudCores = 0;
    int cloudSockets = 0;
    for (HardwareMeasurementType type : HardwareMeasurementType.getCloudProviderTypes()) {
      Double measurement = getMeasurement(type, MetricIdUtils.getSockets());
      if (measurement != null) {
        cloudInstances +=
            Optional.ofNullable(getMeasurement(type, MetricIdUtils.getInstanceHours()))
                .map(Double::intValue)
                .orElse(0);
        cloudCores +=
            Optional.ofNullable(getMeasurement(type, MetricIdUtils.getCores()))
                .map(Double::intValue)
                .orElse(0);
        cloudSockets +=
            Optional.ofNullable(getMeasurement(type, MetricIdUtils.getSockets()))
                .map(Double::intValue)
                .orElse(0);
      }
    }
    snapshot.setCloudInstanceCount(cloudInstances);
    snapshot.setCloudCores(cloudCores);
    snapshot.setCloudSockets(cloudSockets);

    snapshot.setCoreHours(
        tallyMeasurements.get(
            new TallyMeasurementKey(
                HardwareMeasurementType.TOTAL, MetricIdUtils.getCores().getValue())));
    snapshot.setInstanceHours(
        tallyMeasurements.get(
            new TallyMeasurementKey(
                HardwareMeasurementType.TOTAL, MetricIdUtils.getInstanceHours().getValue())));

    snapshot.setHasData(id != null);
    return snapshot;
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
        && Objects.equals(orgId, that.orgId)
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
        orgId,
        serviceLevel,
        usage,
        granularity,
        billingProvider,
        billingAccountId);
  }
}
