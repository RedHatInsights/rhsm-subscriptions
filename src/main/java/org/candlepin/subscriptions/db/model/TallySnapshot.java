/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import java.util.Map;
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
import javax.persistence.MapKeyColumn;
import javax.persistence.MapKeyEnumerated;
import javax.persistence.Table;


/**
 * Model object to represent pieces of tally data.
 */
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

    @Column(name = "sla")
    private ServiceLevel serviceLevel = ServiceLevel.ANY;

    @Column(name = "usage")
    private Usage usage = Usage.ANY;

    @Enumerated(EnumType.STRING)
    @Column(name = "granularity")
    private Granularity granularity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "hardware_measurements",
        joinColumns = @JoinColumn(name = "snapshot_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "measurement_type")
    private Map<HardwareMeasurementType, HardwareMeasurement> hardwareMeasurements =
        new EnumMap<>(HardwareMeasurementType.class);

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OffsetDateTime getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(OffsetDateTime snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    public void setGranularity(Granularity granularity) {
        this.granularity = granularity;
    }

    public HardwareMeasurement getHardwareMeasurement(HardwareMeasurementType type) {
        return hardwareMeasurements.get(type);
    }

    public void setHardwareMeasurement(HardwareMeasurementType type, HardwareMeasurement measurement) {
        hardwareMeasurements.put(type, measurement);
    }

    public ServiceLevel getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(ServiceLevel serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public org.candlepin.subscriptions.utilization.api.model.TallySnapshot asApiSnapshot() {
        org.candlepin.subscriptions.utilization.api.model.TallySnapshot snapshot =
            new org.candlepin.subscriptions.utilization.api.model.TallySnapshot();

        snapshot.setDate(this.getSnapshotDate());

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

        HardwareMeasurement hypervisor = this.hardwareMeasurements.get(HardwareMeasurementType.VIRTUAL);
        if (hypervisor != null) {
            snapshot.setHypervisorCores(hypervisor.getCores());
            snapshot.setHypervisorSockets(hypervisor.getSockets());
            snapshot.setHypervisorInstanceCount(hypervisor.getInstanceCount());
        }

        // Tally up all the cloud providers that we support. We count/store them separately in the DB
        // so that we can report on each provider if required in the future.
        Integer cloudInstances = 0;
        Integer cloudCores = 0;
        Integer cloudSockets = 0;
        HardwareMeasurement cloudigradeMeasurement =
            this.hardwareMeasurements.get(HardwareMeasurementType.AWS_CLOUDIGRADE);
        for (HardwareMeasurementType type : HardwareMeasurementType.getCloudProviderTypes()) {
            HardwareMeasurement measurement = this.hardwareMeasurements.get(type);
            if (cloudigradeMeasurement != null && type == HardwareMeasurementType.AWS) {
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

        snapshot.setHasData(id != null);
        return snapshot;
    }

    @Override
    public String toString() {
        return "TallySnapshot{" +
            "id=" + id +
            ", snapshotDate=" + snapshotDate +
            ", granularity=" + granularity +
            ", accountNumber='" + accountNumber + '\'' +
            ", productId='" + productId + '\'' +
            ", serviceLevel='" + serviceLevel + '\'' +
            ", usage='" + usage + '\'' + '}';
    }
}
