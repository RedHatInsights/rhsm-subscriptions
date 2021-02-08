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
package org.candlepin.subscriptions.tally;

import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The calculated usage for a key where key is (productId, sla).
 */
public class UsageCalculation {
    private static final Logger log = LoggerFactory.getLogger(UsageCalculation.class);

    private final Key key;

    /**
     * Natural key for a given calculation.
     *
     * Note that already data is scoped to an account, so account is not included in the key.
     */
    public static class Key {
        private final String productId;

        private final ServiceLevel sla;
        private final Usage usage;

        public Key(String productId, ServiceLevel sla, Usage usage) {
            this.productId = productId;
            this.sla = sla;
            this.usage = usage;
        }

        public String getProductId() {
            return productId;
        }

        public ServiceLevel getSla() {
            return sla;
        }

        public Usage getUsage() {
            return usage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key that = (Key) o;
            return Objects.equals(productId, that.productId) &&
                Objects.equals(sla, that.sla) &&
                Objects.equals(usage, that.usage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, sla, usage);
        }

        public static Key fromTallySnapshot(TallySnapshot snapshot) {
            return new Key(snapshot.getProductId(), snapshot.getServiceLevel(), snapshot.getUsage());
        }

        @Override
        public String toString() {
            return "Key{" +
                "productId='" + productId + '\'' +
                ", sla=" + sla +
                ", usage=" + usage + '}';
        }
    }

    /**
     * Provides metric totals associated with each hardware type associated with a calculation.
     */
    public static class Totals {
        /**
         * @deprecated use measurements instead
         */
        @Deprecated(forRemoval = true)
        private int cores;

        /**
         * @deprecated use measurements instead
         */
        @Deprecated(forRemoval = true)
        private int sockets;

        /**
         * @deprecated use measurements instead
         */
        @Deprecated(forRemoval = true)
        private int instances;
        private final Map<Measurement.Uom, Double> measurements = new EnumMap<>(Measurement.Uom.class);

        public Totals() {
            cores = 0;
            sockets = 0;
            instances = 0;
        }

        public String toString() {
            String entries = measurements.entrySet().stream()
                .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
            String uomMeasurements = String.format("[%s]", entries);
            return String.format("[uom_measurements: %s, cores: %s, sockets: %s, instances: %s]",
                uomMeasurements, cores, sockets, instances);
        }

        /**
         * @deprecated use getMeasurement instead
         *
         * @return running cores measurement
         */
        @Deprecated(forRemoval = true)
        public int getCores() {
            return cores;
        }

        /**
         * @deprecated use getMeasurement instead
         *
         * @return running sockets measurement
         */
        @Deprecated(forRemoval = true)
        public int getSockets() {
            return sockets;
        }

        /**
         * @deprecated use getMeasurement instead
         *
         * @return running instances measurement
         */
        @Deprecated(forRemoval = true)
        public int getInstances() {
            return instances;
        }

        public Map<Measurement.Uom, Double> getMeasurements() {
            return measurements;
        }

        public Double getMeasurement(Measurement.Uom uom) {
            return measurements.get(uom);
        }

        public void increment(Measurement.Uom uom, Double amount) {
            Double existingValue = getMeasurement(uom);
            double value = existingValue == null ? 0.0 : existingValue;
            double newValue = value + amount;
            measurements.put(uom, newValue);
        }
    }

    private final Map<HardwareMeasurementType, Totals> mappedTotals;

    public UsageCalculation(Key key) {
        this.key = key;
        this.mappedTotals = new EnumMap<>(HardwareMeasurementType.class);
    }

    public String getProductId() {
        return key.productId;
    }

    public ServiceLevel getSla() {
        return key.sla;
    }

    public Usage getUsage() {
        return key.usage;
    }

    public Totals getTotals(HardwareMeasurementType type) {
        return mappedTotals.get(type);
    }

    public void add(HardwareMeasurementType type, Measurement.Uom uom, Double value) {
        increment(type, uom, value);
        addToTotal(uom, value);
    }

    /**
     * @deprecated use add instead
     */
    @Deprecated(forRemoval = true)
    public void addPhysical(int cores, int sockets, int instances) {
        increment(HardwareMeasurementType.PHYSICAL, cores, sockets, instances);
        addToTotal(cores, sockets, instances);
    }

    /**
     * @deprecated use add instead
     */
    @Deprecated(forRemoval = true)
    public void addHypervisor(int cores, int sockets, int instances) {
        increment(HardwareMeasurementType.VIRTUAL, cores, sockets, instances);
        addToTotal(cores, sockets, instances);
    }

    /**
     * @deprecated use addToTotal(Measurement.Uom, Double value) instead
     */
    @Deprecated(forRemoval = true)
    public void addToTotal(int cores, int sockets, int instances) {
        increment(HardwareMeasurementType.TOTAL, cores, sockets, instances);
    }

    public void addToTotal(Measurement.Uom uom, Double value) {
        increment(HardwareMeasurementType.TOTAL, uom, value);
    }

    /**
     * @deprecated use add instead
     */
    @Deprecated(forRemoval = true)
    public void addCloudProvider(HardwareMeasurementType cloudType, int cores, int sockets, int instances) {
        if (!HardwareMeasurementType.isSupportedCloudProvider(cloudType.name())) {
            throw new IllegalArgumentException(String.format("%s is not a supported cloud provider type.",
                cloudType));
        }

        increment(cloudType, cores, sockets, instances);
        addToTotal(cores, sockets, instances);
    }

    /**
     * @deprecated use add instead
     */
    @Deprecated(forRemoval = true)
    public void addCloudigrade(HardwareMeasurementType cloudType, int count) {
        increment(cloudType, 0, count, count);
        Totals awsTotals = getTotals(HardwareMeasurementType.AWS);
        Totals grandTotal = getTotals(HardwareMeasurementType.TOTAL);
        if (awsTotals != null) {
            if (awsTotals.instances != count) {
                log.warn("AWS totals differ by source; HBI: {} vs. cloudigrade: {}", awsTotals.instances,
                    count);
            }
            grandTotal.instances -= awsTotals.instances;
            grandTotal.sockets -= awsTotals.sockets;
            grandTotal.cores -= awsTotals.cores;
        }
        addToTotal(0, count, count);
    }

    /**
     * @deprecated use increment(HardwareMeasurementType, Measurement.Uom, Double) instead
     */
    @Deprecated(forRemoval = true)
    private void increment(HardwareMeasurementType type, int cores, int sockets, int instances) {
        Totals total = getOrDefault(type);
        total.cores += cores;
        total.sockets += sockets;
        total.instances += instances;
    }

    private void increment(HardwareMeasurementType type, Measurement.Uom uom, Double value) {
        Totals total = getOrDefault(type);
        total.increment(uom, value);
    }

    private Totals getOrDefault(HardwareMeasurementType type) {
        this.mappedTotals.putIfAbsent(type, new Totals());
        return this.mappedTotals.get(type);
    }

    public boolean hasMeasurements() {
        return !this.mappedTotals.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("[Product: %s, sla: %s, usage: %s", key.productId, key.sla, key.usage));
        for (Entry<HardwareMeasurementType, Totals> entry : mappedTotals.entrySet()) {
            builder.append(String.format(", %s: %s", entry.getKey(), entry.getValue()));
        }
        builder.append("]");
        return builder.toString();
    }

}
