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
 * The calculated usage for a product.
 */
public class ProductUsageCalculation {
    private String productId;
    private int totalCores;
    private int totalSockets;
    private int totalInstanceCount;

    // Physical
    private int totalPhysicalCores;
    private int totalPhysicalSockets;
    private int totalPhysicalInstanceCount;

    public ProductUsageCalculation(String productId) {
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }

    public int getTotalCores() {
        return totalCores;
    }

    public int getTotalSockets() {
        return totalSockets;
    }

    public int getTotalInstanceCount() {
        return totalInstanceCount;
    }

    public int getTotalPhysicalCores() {
        return totalPhysicalCores;
    }

    public int getTotalPhysicalSockets() {
        return totalPhysicalSockets;
    }

    public int getTotalPhysicalInstanceCount() {
        return totalPhysicalInstanceCount;
    }

    public void addPhysical(int cores, int sockets, int instances) {
        totalPhysicalCores += cores;
        totalPhysicalSockets += sockets;
        totalPhysicalInstanceCount += instances;
        addToTotals(cores, sockets, instances);
    }

    public void addUncategorized(int cores, int sockets, int instances) {
        addToTotals(cores, sockets, instances);
    }

    private void addToTotals(int cores, int sockets, int instances) {
        totalCores += cores;
        totalSockets += sockets;
        totalInstanceCount += instances;
    }

    @Override
    public String toString() {
        return String.format(
            "[Product: %s, Cores: %s, Sockets: %s, Instances: %s, Physical Cores: %s, Physical Sockets: %s," +
            " Physical Instances: %s]", productId, totalCores, totalSockets, totalInstanceCount,
            totalPhysicalCores, totalPhysicalSockets, totalPhysicalInstanceCount);
    }
}
