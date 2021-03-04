/*
 * Copyright (c) 2021 Red Hat, Inc.
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
 * Subscription entities represent data from a Candlepin Pool
 */
@Entity
@IdClass(Subscription.SubscriptionCompoundId.class)
@Table(name = "subscription")
public class Subscription {

    @Id
    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "sku")
    private String sku;

    @Column(name = "owner_id")
    private String  ownerId;

    @Column(name = "quantity")
    private long quantity;

    @Id
    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "end_date")
    private OffsetDateTime endDate;

    @Column(name = "marketplace_subscription_id")
    private String marketplaceSubscriptionId;

    @Column(name = "account_number")
    private String accountNumber;

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(OffsetDateTime startDate) {
        this.startDate = startDate;
    }

    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(OffsetDateTime endDate) {
        this.endDate = endDate;
    }

    public String getMarketplaceSubscriptionId() {
        return marketplaceSubscriptionId;
    }

    public void setMarketplaceSubscriptionId(String marketplaceSubscriptionId) {
        this.marketplaceSubscriptionId = marketplaceSubscriptionId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Subscription that = (Subscription) o;
        return getQuantity() == that.getQuantity() && Objects.equals(getOwnerId(), that.getOwnerId()) &&
            Objects.equals(getSubscriptionId(), that.getSubscriptionId()) &&
            Objects.equals(getSku(), that.getSku()) &&
            Objects.equals(getStartDate(), that.getStartDate()) &&
            Objects.equals(getEndDate(), that.getEndDate()) &&
            Objects.equals(getAccountNumber(), that.getAccountNumber()) &&
            Objects.equals(getMarketplaceSubscriptionId(), this.getMarketplaceSubscriptionId());
    }

    @Override
    public int hashCode() {
        return Objects
            .hash(getOwnerId(), getSubscriptionId(), getSku(), getQuantity(), getStartDate(),
                getEndDate(), getAccountNumber(), getMarketplaceSubscriptionId());
    }

    /**
     * Composite ID class for Subscription entities.
     */
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

        public String getSubscriptionId() {
            return subscriptionId;
        }

        public void setSubscriptionId(String subscriptionId) {
            this.subscriptionId = subscriptionId;
        }

        public OffsetDateTime getStartDate() {
            return startDate;
        }

        public void setStartDate(OffsetDateTime startDate) {
            this.startDate = startDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SubscriptionCompoundId that = (SubscriptionCompoundId) o;
            return Objects.equals(getSubscriptionId(), that.getSubscriptionId()) &&
                Objects.equals(getStartDate(), that.getStartDate());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getSubscriptionId(), getStartDate());
        }
    }
}
