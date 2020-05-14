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
package org.candlepin.subscriptions.capacity;

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProductAttribute;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProvidedProduct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Component that maps Candlepin pools to subscription capacity records.
 */
@Component
public class CandlepinPoolCapacityMapper {

    private static final Logger log = LoggerFactory.getLogger(CandlepinPoolCapacityMapper.class);

    private final CapacityProductExtractor productExtractor;

    CandlepinPoolCapacityMapper(CapacityProductExtractor productExtractor) {
        this.productExtractor = productExtractor;
    }

    /**
     * Transforms candlepin pool records into capacity records.
     *
     * If existing capacity records are passed, then they are updated, otherwise new SubscriptionCapacity
     * instances are created.
     *
     * @param ownerId Candlepin org ID to operate against
     * @param pool Candlepin pool to map to capacity records
     * @param existingCapacityMap map of existing capacity records for this subscription
     * @return list of capacities to save
     */
    public Collection<SubscriptionCapacity> mapPoolToSubscriptionCapacity(String ownerId, CandlepinPool pool,
        Map<SubscriptionCapacityKey, SubscriptionCapacity> existingCapacityMap) {

        List<String> productIds = extractProductIds(pool.getProvidedProducts());
        List<String> derivedProductIds = extractProductIds(pool.getDerivedProvidedProducts());

        Set<String> products = productExtractor.getProducts(productIds);
        Set<String> derivedProducts = productExtractor.getProducts(derivedProductIds);

        HashSet<String> allProducts = new HashSet<>(products);
        allProducts.addAll(derivedProducts);

        Long socketCapacity = getCapacityUnit("sockets", pool);
        Long coresCapacity = getCapacityUnit("cores", pool);

        ServiceLevel sla = getSla(pool);

        return allProducts.stream().map(product -> {
            SubscriptionCapacityKey key = new SubscriptionCapacityKey();
            key.setOwnerId(ownerId);
            key.setSubscriptionId(pool.getSubscriptionId());
            key.setProductId(product);
            SubscriptionCapacity capacity = existingCapacityMap.get(key);
            if (capacity == null) {
                capacity = new SubscriptionCapacity();
            }
            capacity.setKey(key);
            capacity.setAccountNumber(pool.getAccountNumber());
            capacity.setBeginDate(pool.getStartDate());
            capacity.setEndDate(pool.getEndDate());
            capacity.setServiceLevel(sla);
            capacity.setSku(pool.getProductId());

            handleSockets(products, derivedProducts, socketCapacity, product, capacity);
            handleCores(products, derivedProducts, coresCapacity, product, capacity);

            if (capacity.getPhysicalSockets() == null && capacity.getPhysicalCores() == null &&
                capacity.getVirtualCores() == null && capacity.getVirtualSockets() == null &&
                !capacity.getHasUnlimitedGuestSockets()) {

                log.warn("SKU {} appears to not provide any capacity. Bad SKU definition?",
                    pool.getProductId());
            }
            return capacity;
        }).collect(Collectors.toList());
    }

    private void handleSockets(Set<String> products, Set<String> derivedProducts, Long socketCapacity,
        String product, SubscriptionCapacity capacity) {

        if (products.contains(product) && isPositive(socketCapacity)) {
            capacity.setPhysicalSockets(Math.toIntExact(socketCapacity));
        }
        else {
            capacity.setPhysicalSockets(null);
        }
        if (derivedProducts.contains(product) && isPositive(socketCapacity)) {
            capacity.setVirtualSockets(Math.toIntExact(socketCapacity));
        }
        else {
            capacity.setVirtualSockets(null);
        }
    }

    private void handleCores(Set<String> products, Set<String> derivedProducts, Long coresCapacity,
        String product, SubscriptionCapacity capacity) {

        if (products.contains(product) && isPositive(coresCapacity)) {
            capacity.setPhysicalCores(Math.toIntExact(coresCapacity));
        }
        else {
            capacity.setPhysicalCores(null);
        }

        if (derivedProducts.contains(product) && isPositive(coresCapacity)) {
            capacity.setVirtualCores(Math.toIntExact(coresCapacity));
        }
        else {
            capacity.setVirtualCores(null);
        }
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private Long getCapacityUnit(String unitProperty, CandlepinPool pool) {
        Integer units = pool.getProductAttributes().stream()
            .filter(attr -> attr.getName().equals(unitProperty))
            .map(CandlepinProductAttribute::getValue).mapToInt(Integer::parseInt).boxed().findFirst()
            .orElse(null);
        if (units != null) {
            return units * (pool.getQuantity() / getInstanceBasedMultiplier(pool));
        }
        return null;
    }

    private long getInstanceBasedMultiplier(CandlepinPool pool) {
        return pool.getProductAttributes().stream()
            .filter(attr -> attr.getName().equals("instance_multiplier"))
            .map(CandlepinProductAttribute::getValue).mapToInt(Integer::parseInt).boxed().findFirst()
            .orElse(1);
    }

    private ServiceLevel getSla(CandlepinPool pool) {
        Optional<String> sla = pool.getProductAttributes().stream()
            .filter(attr -> attr.getName().equals("support_level")).map(CandlepinProductAttribute::getValue)
            .findFirst();

        if (sla.isPresent()) {
            ServiceLevel slaValue = ServiceLevel.fromString(sla.get());
            if (slaValue == ServiceLevel.UNSPECIFIED) {
                log.warn("Product {} has unsupported service level {}", pool.getProductId(), sla.get());
                return null;
            }
            return slaValue;
        }

        return null;
    }

    private List<String> extractProductIds(Collection<CandlepinProvidedProduct> providedProducts) {
        return providedProducts.stream().map(CandlepinProvidedProduct::getProductId)
            .collect(Collectors.toList());
    }
}
