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
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

/**
 * Capacity provided by a subscription for a given product.
 */
@IdClass(SubscriptionCapacityKey.class)
@Entity
@Table(name = "subscription_capacity")
public class SubscriptionCapacity implements Serializable {
    @Id
    @Column(name = "product_id")
    private String productId;

    @Id
    @Column(name = "subscription_id")
    private String subscriptionId;

    @Id
    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "physical_sockets")
    private Integer physicalSockets;

    @Column(name = "virtual_sockets")
    private Integer virtualSockets;

    @Column(name = "physical_cores")
    private Integer physicalCores;

    @Column(name = "virtual_cores")
    private Integer virtualCores;

    @Column(name = "has_unlimited_guest_sockets")
    private boolean hasUnlimitedGuestSockets;

    @Column(name = "begin_date")
    private OffsetDateTime beginDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "sku")
    private String sku;

    @Column(name = "sla")
    private String serviceLevel;

    @Column(name = "usage")
    private String usage;

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Integer getPhysicalSockets() {
        return physicalSockets;
    }

    public void setPhysicalSockets(Integer physicalSockets) {
        this.physicalSockets = physicalSockets;
    }

    public Integer getVirtualSockets() {
        return virtualSockets;
    }

    public void setVirtualSockets(Integer virtualSockets) {
        this.virtualSockets = virtualSockets;
    }

    public Integer getPhysicalCores() {
        return physicalCores;
    }

    public void setPhysicalCores(Integer physicalCores) {
        this.physicalCores = physicalCores;
    }

    public Integer getVirtualCores() {
        return virtualCores;
    }

    public void setVirtualCores(Integer virtualCores) {
        this.virtualCores = virtualCores;
    }

    public boolean getHasUnlimitedGuestSockets() {
        return hasUnlimitedGuestSockets;
    }

    public void setHasUnlimitedGuestSockets(boolean hasUnlimitedGuestSockets) {
        this.hasUnlimitedGuestSockets = hasUnlimitedGuestSockets;
    }

    public OffsetDateTime getBeginDate() {
        return beginDate;
    }

    public void setBeginDate(OffsetDateTime beginDate) {
        this.beginDate = beginDate;
    }

    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(OffsetDateTime endDate) {
        this.endDate = endDate;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            getAccountNumber(),
            getBeginDate(),
            getEndDate(),
            getHasUnlimitedGuestSockets(),
            getOwnerId(),
            getPhysicalSockets(),
            getProductId(),
            getSku(),
            getSubscriptionId(),
            getVirtualSockets(),
            getPhysicalCores(),
            getVirtualCores(),
            getServiceLevel(),
            getUsage()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionCapacity)) {
            return false;
        }
        SubscriptionCapacity that = (SubscriptionCapacity) o;
        return getHasUnlimitedGuestSockets() == that.getHasUnlimitedGuestSockets() &&
            Objects.equals(getAccountNumber(), that.getAccountNumber()) &&
            Objects.equals(getProductId(), that.getProductId()) &&
            Objects.equals(getSku(), that.getSku()) &&
            Objects.equals(getSubscriptionId(), that.getSubscriptionId()) &&
            Objects.equals(getOwnerId(), that.getOwnerId()) &&
            Objects.equals(getPhysicalSockets(), that.getPhysicalSockets()) &&
            Objects.equals(getVirtualSockets(), that.getVirtualSockets()) &&
            Objects.equals(getBeginDate(), that.getBeginDate()) &&
            Objects.equals(getEndDate(), that.getEndDate()) &&
            Objects.equals(getPhysicalCores(), that.getPhysicalCores()) &&
            Objects.equals(getVirtualCores(), that.getVirtualCores()) &&
            Objects.equals(getServiceLevel(), that.getServiceLevel()) &&
            Objects.equals(getUsage(), that.getUsage());
    }

    @Override
    public String toString() {
        return String.format("SubscriptionCapacity{accountNumber=%s, sku=%s, productId=%s, " +
            "subscriptionId=%s, ownerId=%s, physicalSockets=%s, virtualSockets=%s, " +
            "hasUnlimitedGuestSockets=%s, physicalCores=%s, virtualCores=%s, serviceLevel=%s, usage=%s, " +
            "beginDate=%s, endDate=%s}",
            accountNumber, sku, productId, subscriptionId, ownerId, physicalSockets, virtualSockets,
            hasUnlimitedGuestSockets, physicalCores, virtualCores, serviceLevel, usage, beginDate, endDate);
    }
}
