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
package org.candlepin.subscriptions.tally;

/**
 * The calculated usage for a given product.
 */
public class ProductUsageCalculation {

    private String account;
    private String owner;
    private String productId;
    private int totalCores;
    private int totalSockets;
    private int instanceCount;

    public ProductUsageCalculation(String account, String productId) {
        this.account = account;
        this.productId = productId;
        this.totalCores = 0;
        this.totalSockets = 0;
        this.instanceCount = 0;
    }

    public String getAccount() {
        return account;
    }


    public String getProductId() {
        return productId;
    }

    public void addCores(int coresToAdd) {
        this.totalCores += coresToAdd;
    }

    public int getTotalCores() {
        return totalCores;
    }

    public int getTotalSockets() {
        return totalSockets;
    }

    public void addSockets(int socketsToAdd) {
        this.totalSockets += socketsToAdd;
    }

    public void addInstance() {
        this.instanceCount++;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return String.format("[Account: %s, Owner: %s, Product: %s, Cores: %s, Sockets: %s, Instances: %s",
            this.account, this.owner, this.productId, this.totalCores, this.totalSockets,
            this.instanceCount);
    }
}
