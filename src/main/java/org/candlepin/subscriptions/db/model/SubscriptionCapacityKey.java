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
import java.util.Objects;

import javax.persistence.Column;

/**
 * Primary key for record of capacity provided by a subscription for a given product.
 */
public class SubscriptionCapacityKey implements Serializable {

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "product_id")
    private String productId;

    @Column(name = "subscription_id")
    private String subscriptionId;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountNumber, productId, subscriptionId);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SubscriptionCapacityKey)) {
            return false;
        }
        SubscriptionCapacityKey other = (SubscriptionCapacityKey) obj;
        return Objects.equals(getAccountNumber(), other.getAccountNumber()) &&
            Objects.equals(getProductId(), other.getProductId()) &&
            Objects.equals(getSubscriptionId(), other.getSubscriptionId());
    }
}
