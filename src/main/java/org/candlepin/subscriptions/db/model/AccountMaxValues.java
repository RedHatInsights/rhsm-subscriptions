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

import org.candlepin.subscriptions.tally.ProductUsageCalculation;

/**
 * A class that hold the max values for a particular account. This object is primarily used when
 * querying the max values from the database.
 */
public class AccountMaxValues {

    private String accountNumber;
    private String ownerId;
    private Integer maxCores;
    private Integer maxSockets;
    private Integer maxInstances;

    public AccountMaxValues(String accountNumber, String ownerId, Integer maxCores, Integer maxSockets,
        Integer maxInstances) {
        this.accountNumber = accountNumber;
        this.ownerId = ownerId;
        this.maxCores = maxCores;
        this.maxSockets = maxSockets;
        this.maxInstances = maxInstances;
    }

    public AccountMaxValues(ProductUsageCalculation calc) {
        this(calc.getAccount(), calc.getOwner(), calc.getTotalCores(), calc.getTotalSockets(),
            calc.getInstanceCount());
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Integer getMaxCores() {
        return maxCores;
    }

    public void setMaxCores(Integer maxCores) {
        this.maxCores = maxCores;
    }

    public Integer getMaxSockets() {
        return maxSockets;
    }

    public void setMaxSockets(Integer maxSockets) {
        this.maxSockets = maxSockets;
    }

    public Integer getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(Integer maxInstances) {
        this.maxInstances = maxInstances;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
}
