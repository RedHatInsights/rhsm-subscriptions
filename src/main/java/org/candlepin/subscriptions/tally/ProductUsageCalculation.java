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

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The calculated usage for a product.
 */
public class ProductUsageCalculation {

    /**
     * Provides metric totals associated with each hardware type associated with a calculation.
     */
    public class Totals {
        private int cores;
        private int sockets;
        private int instances;

        public Totals() {
            cores = 0;
            sockets = 0;
            instances = 0;
        }

        public String toString() {
            return String.format("[cores: %s, sockets: %s, instances: %s]", cores, sockets, instances);
        }

        public int getCores() {
            return cores;
        }

        public int getSockets() {
            return sockets;
        }

        public int getInstances() {
            return instances;
        }
    }

    private String productId;

    private Map<HardwareMeasurementType, Totals> mappedTotals;

    public ProductUsageCalculation(String productId) {
        this.productId = productId;
        this.mappedTotals = new EnumMap<>(HardwareMeasurementType.class);
    }

    public String getProductId() {
        return productId;
    }

    public Totals getTotals(HardwareMeasurementType type) {
        return mappedTotals.get(type);
    }

    public void addPhysical(int cores, int sockets, int instances) {
        increment(HardwareMeasurementType.PHYSICAL, cores, sockets, instances);
        addToTotal(cores, sockets, instances);
    }

    public void addHypervisor(int cores, int sockets, int instances) {
        increment(HardwareMeasurementType.HYPERVISOR, cores, sockets, instances);
        addToTotal(cores, sockets, instances);
    }

    public void addToTotal(int cores, int sockets, int instances) {
        increment(HardwareMeasurementType.TOTAL, cores, sockets, instances);
    }

    public void addCloudProvider(HardwareMeasurementType cloudType, int cores, int sockets, int instances) {
        if (!HardwareMeasurementType.getCloudProviderTypes().contains(cloudType)) {
            throw new IllegalArgumentException(String.format("%s is not a cloud provider type.", cloudType));
        }

        increment(cloudType, cores, sockets, instances);
        addToTotal(cores, sockets, instances);
    }

    private void increment(HardwareMeasurementType type, int cores, int sockets, int instances) {
        Totals total = getOrDefault(type);
        total.cores += cores;
        total.sockets += sockets;
        total.instances += instances;
    }

    private Totals getOrDefault(HardwareMeasurementType type) {
        this.mappedTotals.putIfAbsent(type, new Totals());
        return this.mappedTotals.get(type);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("[Product: %s", productId));
        for (Entry<HardwareMeasurementType, Totals> entry : mappedTotals.entrySet()) {
            builder.append(String.format(", %s: %s", entry.getKey(), entry.getValue()));
        }
        builder.append("]");
        return builder.toString();
    }

}
