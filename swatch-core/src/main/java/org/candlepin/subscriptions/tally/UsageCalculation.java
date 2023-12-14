/*
 * Copyright Red Hat, Inc.
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

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;

/**
 * The calculated usage for a key where key is (productId, sla, usage, billingProvider, and
 * billingAccountId).
 */
public class UsageCalculation {

  private final Key key;

  /**
   * Natural key for a given calculation.
   *
   * <p>Note that already data is scoped to an account, so account is not included in the key.
   */
  @Getter
  @EqualsAndHashCode
  @AllArgsConstructor
  @ToString
  public static class Key {
    @NonNull private final String productId;
    @NonNull private final ServiceLevel sla;
    @NonNull private final Usage usage;
    @NonNull private final BillingProvider billingProvider;
    @NonNull private final String billingAccountId;

    public static Key fromTallySnapshot(TallySnapshot snapshot) {
      return new Key(
          snapshot.getProductId(),
          snapshot.getServiceLevel(),
          snapshot.getUsage(),
          snapshot.getBillingProvider(),
          snapshot.getBillingAccountId());
    }
  }

  /** Provides metric totals associated with each hardware type associated with a calculation. */
  public static class Totals {
    private final Map<MetricId, BigDecimal> measurements = new HashMap<>();

    public String toString() {
      String entries =
          measurements.entrySet().stream()
              .map(e -> String.format("%s: %s", e.getKey(), e.getValue()))
              .collect(Collectors.joining(", "));
      String uomMeasurements = String.format("[%s]", entries);
      return String.format("[uom_measurements: %s]", uomMeasurements);
    }

    public Map<MetricId, Double> getMeasurements() {
      return measurements.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
    }

    public Double getMeasurement(MetricId uom) {
      return Optional.ofNullable(measurements.get(uom)).map(BigDecimal::doubleValue).orElse(null);
    }

    public void increment(MetricId uom, Double amount) {
      BigDecimal value = measurements.getOrDefault(uom, BigDecimal.valueOf(0));
      measurements.put(uom, value.add(BigDecimal.valueOf(amount)));
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

  public BillingProvider getBillingProvider() {
    return key.billingProvider;
  }

  public String getBillingAccountId() {
    return key.billingAccountId;
  }

  public Totals getTotals(HardwareMeasurementType type) {
    return mappedTotals.get(type);
  }

  public void add(HardwareMeasurementType type, MetricId metricId, Double value) {
    increment(type, metricId, value);
    if (type != HardwareMeasurementType.TOTAL) {
      addToTotal(metricId, value);
    }
  }

  public void add(HardwareMeasurementType type, int cores, int sockets, int instances) {
    add(type, (double) cores, (double) sockets, (double) instances);
  }

  public void add(HardwareMeasurementType type, Double cores, Double sockets, Double instances) {
    add(type, MetricIdUtils.getCores(), cores);
    add(type, MetricIdUtils.getSockets(), sockets);
    add(type, MetricIdUtils.getInstanceHours(), instances);
  }

  public void addPhysical(int cores, int sockets, int instances) {
    add(HardwareMeasurementType.PHYSICAL, cores, sockets, instances);
  }

  public void addHypervisor(int cores, int sockets, int instances) {
    add(HardwareMeasurementType.HYPERVISOR, cores, sockets, instances);
  }

  public void addUnmappedGuest(int cores, int sockets, int instances) {
    addVirtual(cores, sockets, instances);
  }

  public void addVirtual(int cores, int sockets, int instances) {
    add(HardwareMeasurementType.VIRTUAL, cores, sockets, instances);
  }

  public void addToTotal(int cores, int sockets, int instances) {
    add(HardwareMeasurementType.TOTAL, cores, sockets, instances);
  }

  public void addToTotal(MetricId uom, Double value) {
    increment(HardwareMeasurementType.TOTAL, uom, value);
  }

  public void addCloudProvider(
      HardwareMeasurementType cloudType, int cores, int sockets, int instances) {
    if (!HardwareMeasurementType.isSupportedCloudProvider(cloudType.name())) {
      throw new IllegalArgumentException(
          String.format("%s is not a supported cloud provider type.", cloudType));
    }
    add(cloudType, cores, sockets, instances);
  }

  private void increment(HardwareMeasurementType type, MetricId uom, Double value) {
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
    builder.append(
        String.format(
            "[Product: %s, sla: %s, usage: %s, billingProvider: %s, billingAccountId: %s",
            key.productId, key.sla, key.usage, key.billingProvider, key.billingAccountId));
    for (Entry<HardwareMeasurementType, Totals> entry : mappedTotals.entrySet()) {
      builder.append(String.format(", %s: %s", entry.getKey(), entry.getValue()));
    }
    builder.append("]");
    return builder.toString();
  }
}
