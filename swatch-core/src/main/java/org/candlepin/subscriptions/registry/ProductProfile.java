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
package org.candlepin.subscriptions.registry;

import static org.candlepin.subscriptions.db.model.Granularity.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.springframework.util.StringUtils;

/** Represents information telling capacity and tally how to handle certain products */
@Getter
@Setter
@ToString
@EqualsAndHashCode
public class ProductProfile {
  private static final ProductProfile DEFAULT_PROFILE =
      new ProductProfile("DEFAULT", Collections.emptySet(), DAILY);

  public static ProductProfile getDefault() {
    return DEFAULT_PROFILE;
  }

  private String name;
  private Set<SubscriptionWatchProduct> products;
  private Set<SyspurposeRole> syspurposeRoles;
  private Set<MarketplaceMetric> marketplaceMetrics;
  private Set<SwatchProductByOfferingProductName> swatchProductsByOfferingProductName;
  private Granularity finestGranularity;
  private boolean burstable = false;
  private String serviceType;
  private ServiceLevel defaultSla;
  private Usage defaultUsage;
  private String prometheusMetricName;
  private String prometheusCounterName;
  private Map<String, String> architectureSwatchProductIdMap;
  private Map<String, Set<String>> swatchProductsByRoles;
  private Map<String, Set<String>> swatchProductsByEngProducts;
  private Map<ProductUom, String> metricByProductAndUom;
  private Map<String, Set<String>> rolesBySwatchProduct;
  private Map<String, String> swatchProductIdsByOfferingProductName;

  public ProductProfile() {
    // Default used for YAML deserialization
    this.syspurposeRoles = new HashSet<>();
    this.marketplaceMetrics = new HashSet<>();
    this.swatchProductsByRoles = new HashMap<>();

    this.swatchProductsByEngProducts = new HashMap<>();
    this.metricByProductAndUom = new HashMap<>();
    this.swatchProductIdsByOfferingProductName = new HashMap<>();
  }

  public ProductProfile(
      String name, Set<SubscriptionWatchProduct> products, Granularity granularity) {
    this();
    this.name = name;
    this.finestGranularity = granularity;
    // Setter required to populate swatch products by role.
    setProducts(products);
  }

  public void setProducts(Set<SubscriptionWatchProduct> products) {
    this.products = products;
    this.swatchProductsByEngProducts =
        this.products.stream()
            .collect(
                Collectors.toMap(
                    SubscriptionWatchProduct::getEngProductId,
                    SubscriptionWatchProduct::getSwatchProductIds));
  }

  public void setSyspurposeRoles(Set<SyspurposeRole> syspurposeRoles) {
    this.syspurposeRoles = syspurposeRoles;
    this.swatchProductsByRoles =
        syspurposeRoles.stream()
            .collect(
                Collectors.toMap(SyspurposeRole::getName, SyspurposeRole::getSwatchProductIds));

    this.rolesBySwatchProduct = new HashMap<>();
    // Loop across all the SwatchProductIds in the syspurpose list. Add each swatchProductId to the
    // map
    // and append the associated role to an associated set (creating the set if necessary).
    for (SyspurposeRole syspurposeRole : syspurposeRoles) {
      syspurposeRole
          .getSwatchProductIds()
          .forEach(
              key ->
                  this.rolesBySwatchProduct
                      .computeIfAbsent(key, k -> new HashSet<>())
                      .add(syspurposeRole.getName()));
    }
  }

  public void setMarketplaceMetrics(Set<MarketplaceMetric> marketplaceMetrics) {
    this.marketplaceMetrics = marketplaceMetrics;

    marketplaceMetrics.forEach(
        marketplaceMetric ->
            marketplaceMetric
                .getSwatchProductIds()
                .forEach(
                    swatchProductId ->
                        this.metricByProductAndUom.put(
                            new ProductUom(swatchProductId, marketplaceMetric.getUom()),
                            marketplaceMetric.getMetricId())));
  }

  public void setSwatchProductsByOfferingProductNames(
      Set<SwatchProductByOfferingProductName> swatchProductsByOfferingProductName) {
    this.swatchProductsByOfferingProductName = swatchProductsByOfferingProductName;

    this.swatchProductIdsByOfferingProductName =
        swatchProductsByOfferingProductName.stream()
            .collect(
                Collectors.toMap(
                    SwatchProductByOfferingProductName::getName,
                    SwatchProductByOfferingProductName::getSwatchProductId));
  }

  public boolean supportsEngProduct(String product) {
    return products.stream().anyMatch(x -> product.equals(x.getEngProductId()));
  }

  public boolean prometheusEnabled() {
    return StringUtils.hasText(prometheusCounterName) || StringUtils.hasText(prometheusMetricName);
  }

  public boolean supportsGranularity(Granularity granularity) {
    return granularity.compareTo(finestGranularity) < 1;
  }

  public String metricByProductAndUom(String swatchProductId, TallyMeasurement.Uom uom) {
    return metricByProductAndUom.get(
        new ProductUom(swatchProductId, fromTallyMeasurementUom(uom).toString()));
  }

  public String metricByProductAndUom(String swatchProductId, Measurement.Uom uom) {
    return metricByProductAndUom.get(new ProductUom(swatchProductId, uom.toString()));
  }

  public String getSwatchProductIdByOfferingProductName(String offeringProductname) {
    return this.swatchProductIdsByOfferingProductName.get(offeringProductname);
  }

  public Measurement.Uom fromTallyMeasurementUom(TallyMeasurement.Uom uom) {
    switch (uom) {
      case SOCKETS:
        return Measurement.Uom.SOCKETS;
      case CORES:
        return Measurement.Uom.CORES;
      default:
        throw new IllegalArgumentException(
            "Unable to convert UOM value "
                + uom.toString()
                + " from TallyMeasurement.Uom to Measurement.Uom");
    }
  }
}
