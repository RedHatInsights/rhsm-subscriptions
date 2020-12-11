/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

/**
 * Represents a reported Host from inventory. This entity stores normalized facts for a
 * Host returned from HBI.
 */
@Entity
@Table(name = "hosts")
public class Host implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull
    @Column(name = "inventory_id", nullable = false)
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

    private Integer cores;

    private Integer sockets;

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
        mappedBy = "key.host",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<HostTallyBucket> buckets;

    @Column(name = "is_unmapped_guest")
    private boolean isUnmappedGuest;

    @Column(name = "is_hypervisor")
    private boolean isHypervisor;

    @Column(name = "cloud_provider")
    private String cloudProvider;

    public Host() {

    }

    public Host(String inventoryId, String insightsId, String accountNumber, String orgId, String subManId) {
        this.inventoryId = inventoryId;
        this.insightsId = insightsId;
        this.accountNumber = accountNumber;
        this.orgId = orgId;
        this.subscriptionManagerId = subManId;
    }

    public Host(InventoryHostFacts inventoryHostFacts, NormalizedFacts normalizedFacts) {
        this.inventoryId = inventoryHostFacts.getInventoryId().toString();
        this.insightsId = inventoryHostFacts.getInsightsId();
        this.accountNumber = inventoryHostFacts.getAccount();
        this.orgId = inventoryHostFacts.getOrgId();
        this.displayName = inventoryHostFacts.getDisplayName();
        this.subscriptionManagerId = inventoryHostFacts.getSubscriptionManagerId();
        this.guest = normalizedFacts.isVirtual();
        this.hypervisorUuid = normalizedFacts.getHypervisorUuid();
        this.cores = normalizedFacts.getCores();
        this.sockets = normalizedFacts.getSockets();
        this.isHypervisor = normalizedFacts.isHypervisor();
        this.isUnmappedGuest = normalizedFacts.isVirtual() && normalizedFacts.isHypervisorUnknown();
        this.cloudProvider = normalizedFacts.getCloudProviderType() == null ?
            null : normalizedFacts.getCloudProviderType().name();

        this.lastSeen = inventoryHostFacts.getModifiedOn();
        this.hardwareType = normalizedFacts.getHardwareType();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(String inventoryId) {
        this.inventoryId = inventoryId;
    }

    public String getInsightsId() {
        return insightsId;
    }

    public void setInsightsId(String insightsId) {
        this.insightsId = insightsId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getSubscriptionManagerId() {
        return subscriptionManagerId;
    }

    public void setSubscriptionManagerId(String subscriptionManagerId) {
        this.subscriptionManagerId = subscriptionManagerId;
    }

    public Integer getCores() {
        return cores;
    }

    public void setCores(Integer cores) {
        this.cores = cores;
    }

    public Integer getSockets() {
        return sockets;
    }

    public void setSockets(Integer sockets) {
        this.sockets = sockets;
    }

    public Boolean getGuest() {
        return guest;
    }

    public void setGuest(Boolean guest) {
        this.guest = guest;
    }

    public String getHypervisorUuid() {
        return hypervisorUuid;
    }

    public void setHypervisorUuid(String hypervisorUuid) {
        this.hypervisorUuid = hypervisorUuid;
    }

    public HostHardwareType getHardwareType() {
        return hardwareType;
    }

    public void setHardwareType(HostHardwareType hardwareType) {
        this.hardwareType = hardwareType;
    }

    public Integer getNumOfGuests() {
        return numOfGuests;
    }

    public void setNumOfGuests(Integer numOfGuests) {
        this.numOfGuests = numOfGuests;
    }

    public OffsetDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(OffsetDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public List<HostTallyBucket> getBuckets() {
        if (this.buckets == null) {
            this.buckets = new ArrayList<>();
        }

        return buckets;
    }

    public void setBuckets(List<HostTallyBucket> buckets) {
        this.buckets = buckets;
    }

    public HostTallyBucket addBucket(String productId, ServiceLevel sla, Usage usage, Boolean asHypervisor,
        int sockets, int cores, HardwareMeasurementType measurementType) {

        HostTallyBucket bucket = new HostTallyBucket(this, productId, sla, usage, asHypervisor, cores,
            sockets, measurementType);
        addBucket(bucket);
        return bucket;
    }

    public void addBucket(HostTallyBucket bucket) {
        bucket.getKey().setHost(this);
        getBuckets().add(bucket);
    }

    public void removeBucket(HostTallyBucket bucket) {
        getBuckets().remove(bucket);
    }

    public boolean isUnmappedGuest() {
        return isUnmappedGuest;
    }

    public void setUnmappedGuest(boolean unmappedGuest) {
        isUnmappedGuest = unmappedGuest;
    }

    public boolean isHypervisor() {
        return isHypervisor;
    }

    public void setHypervisor(boolean hypervisor) {
        isHypervisor = hypervisor;
    }

    public String getCloudProvider() {
        return cloudProvider;
    }

    public void setCloudProvider(String cloudProvider) {
        this.cloudProvider = cloudProvider;
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

}
