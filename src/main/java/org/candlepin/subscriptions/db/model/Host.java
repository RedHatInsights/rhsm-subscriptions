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
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;

/**
 * Represents a reported Host from inventory. This entity stores normalized facts for a
 * Host returned from HBI.
 */
@Entity
@Table(name = "hosts")
public class Host implements Serializable {

    @Id
    @Column(name = "insights_id")
    private String insightsId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "org_id")
    private String orgId;

    @Column(name = "subscription_manager_id")
    private String subscriptionManagerId;

    private Integer cores;

    private Integer sockets;

    @Column(name = "is_guest")
    private Boolean guest;

    @Column(name = "hypervisor_uuid")
    private String hypervisorUuid;

    @Column(name = "hardware_type")
    private HardwareMeasurementType hardwareMeasurementType;

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

    public Host() {

    }

    public Host(String insightsId, String accountNumber, String orgId) {
        this.insightsId = insightsId;
        this.accountNumber = accountNumber;
        this.orgId = orgId;
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

    public HardwareMeasurementType getHardwareMeasurementType() {
        return hardwareMeasurementType;
    }

    public void setHardwareMeasurementType(HardwareMeasurementType hardwareType) {
        this.hardwareMeasurementType = hardwareType;
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
        return buckets;
    }

    public void setBuckets(List<HostTallyBucket> buckets) {
        this.buckets = buckets;
    }

    public HostTallyBucket addBucket(String productId, ServiceLevel sla, Boolean asHypervisor) {
        if (this.buckets == null) {
            this.buckets = new ArrayList<>();
        }

        HostTallyBucket bucket = new HostTallyBucket(this, productId, sla, asHypervisor);
        this.buckets.add(bucket);
        return bucket;
    }

    public void removeBucket(HostTallyBucket bucket) {
        this.buckets.remove(bucket);
    }

}
