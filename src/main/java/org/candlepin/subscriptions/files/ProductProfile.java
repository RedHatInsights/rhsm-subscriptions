/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.files;

import static org.candlepin.subscriptions.db.model.Granularity.*;

import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents information telling capacity and tally how to handle certain products
 */
public class ProductProfile {
    private static final ProductProfile DEFAULT_PROFILE = new ProductProfile("DEFAULT",
        Collections.emptySet(), DAILY);

    public static ProductProfile getDefault() {
        return DEFAULT_PROFILE;
    }

    private String name;
    private Set<SubscriptionWatchProduct> products;
    private Set<SyspurposeRole> syspurposeRoles;
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


    public ProductProfile() {
        // Default used for YAML deserialization
        this.syspurposeRoles = new HashSet<>();
        this.swatchProductsByRoles = new HashMap<>();
        this.swatchProductsByEngProducts = new HashMap<>();
    }

    public ProductProfile(String name, Set<SubscriptionWatchProduct> products, Granularity granularity) {
        this();
        this.name = name;
        this.finestGranularity = granularity;
        // Setter required to populate swatch products by role.
        setProducts(products);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<SubscriptionWatchProduct> getProducts() {
        return products;
    }

    public void setProducts(Set<SubscriptionWatchProduct> products) {
        this.products = products;
        this.swatchProductsByEngProducts = this.products.stream()
            .collect(Collectors.toMap(SubscriptionWatchProduct::getEngProductId,
            SubscriptionWatchProduct::getSwatchProductIds));
    }

    public Set<SyspurposeRole> getSyspurposeRoles() {
        return syspurposeRoles;
    }

    public void setSyspurposeRoles(Set<SyspurposeRole> syspurposeRoles) {
        this.syspurposeRoles = syspurposeRoles;
        this.swatchProductsByRoles = this.syspurposeRoles.stream()
            .collect(Collectors.toMap(SyspurposeRole::getName, SyspurposeRole::getSwatchProductIds));
    }

    public Granularity getFinestGranularity() {
        return finestGranularity;
    }

    public void setFinestGranularity(Granularity finestGranularity) {
        this.finestGranularity = finestGranularity;
    }

    public Map<String, String> getArchitectureSwatchProductIdMap() {
        return architectureSwatchProductIdMap;
    }

    public void setArchitectureSwatchProductIdMap(Map<String, String> architectureSwatchProductIdMap) {
        this.architectureSwatchProductIdMap = architectureSwatchProductIdMap;
    }

    public boolean isBurstable() {
        return burstable;
    }

    public void setBurstable(boolean burstable) {
        this.burstable = burstable;
    }

    public String getPrometheusMetricName() {
        return prometheusMetricName;
    }

    public void setPrometheusMetricName(String prometheusMetricName) {
        this.prometheusMetricName = prometheusMetricName;
    }

    public String getPrometheusCounterName() {
        return prometheusCounterName;
    }

    public void setPrometheusCounterName(String prometheusCounterName) {
        this.prometheusCounterName = prometheusCounterName;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public ServiceLevel getDefaultSla() {
        return defaultSla;
    }

    public void setDefaultSla(ServiceLevel defaultSla) {
        this.defaultSla = defaultSla;
    }

    public Usage getDefaultUsage() {
        return defaultUsage;
    }

    public void setDefaultUsage(Usage defaultUsage) {
        this.defaultUsage = defaultUsage;
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

    public Map<String, Set<String>> getSwatchProductsByRoles() {
        return this.swatchProductsByRoles;
    }

    public Map<String, Set<String>> getSwatchProductsByEngProducts() {
        return this.swatchProductsByEngProducts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ProductProfile that = (ProductProfile) o;
        return burstable == that.burstable && Objects.equals(name, that.name) &&
            Objects.equals(products, that.products) && finestGranularity == that.finestGranularity &&
            Objects.equals(serviceType, that.serviceType) &&
            defaultSla == that.defaultSla &&
            defaultUsage == that.defaultUsage &&
            Objects.equals(prometheusMetricName, that.prometheusMetricName) &&
            Objects.equals(prometheusCounterName, that.prometheusCounterName) &&
            Objects.equals(architectureSwatchProductIdMap, that.architectureSwatchProductIdMap) &&
            Objects.equals(swatchProductsByRoles, that.swatchProductsByRoles) &&
            Objects.equals(swatchProductsByEngProducts, that.swatchProductsByEngProducts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            name,
            products,
            finestGranularity,
            serviceType,
            defaultSla,
            defaultUsage,
            burstable,
            prometheusMetricName,
            prometheusCounterName,
            architectureSwatchProductIdMap,
            swatchProductsByRoles,
            swatchProductsByEngProducts
        );
    }
}
