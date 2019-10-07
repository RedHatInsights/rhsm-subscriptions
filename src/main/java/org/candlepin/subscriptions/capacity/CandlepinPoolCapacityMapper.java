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

import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.utilization.api.model.CandlepinPool;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProductAttribute;
import org.candlepin.subscriptions.utilization.api.model.CandlepinProvidedProduct;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Component that maps Candlepin pools to subscription capacity records.
 */
@Component
public class CandlepinPoolCapacityMapper {

    private final CapacityProductExtractor productExtractor;

    CandlepinPoolCapacityMapper(CapacityProductExtractor productExtractor) {
        this.productExtractor = productExtractor;
    }

    public Collection<SubscriptionCapacity> mapPoolToSubscriptionCapacity(String ownerId,
        CandlepinPool pool) {

        List<String> productIds = extractProductIds(pool.getProvidedProducts());
        List<String> derivedProductIds = extractProductIds(pool.getDerivedProvidedProducts());

        Set<String> products = productExtractor.getProducts(productIds);
        Set<String> derivedProducts = productExtractor.getProducts(derivedProductIds);

        HashSet<String> allProducts = new HashSet<>(products);
        allProducts.addAll(derivedProducts);

        return allProducts.stream().map(product -> {
            SubscriptionCapacity capacity = new SubscriptionCapacity();
            capacity.setAccountNumber(pool.getAccountNumber());
            capacity.setOwnerId(ownerId);
            capacity.setProductId(product);
            capacity.setSubscriptionId(pool.getSubscriptionId());
            capacity.setBeginDate(pool.getStartDate());
            capacity.setEndDate(pool.getEndDate());
            Long socketCapacity = getSocketCapacity(pool);
            if (products.contains(product) && socketCapacity != null) {
                capacity.setPhysicalSockets(Math.toIntExact(socketCapacity));
            }
            if (derivedProducts.contains(product) && socketCapacity != null) {
                capacity.setVirtualSockets(Math.toIntExact(socketCapacity));
            }
            return capacity;
        }).collect(Collectors.toList());
    }

    private Long getSocketCapacity(CandlepinPool pool) {
        Integer sockets = pool.getProductAttributes().stream()
            .filter(attr -> attr.getName().equals("sockets"))
            .map(CandlepinProductAttribute::getValue).mapToInt(Integer::parseInt).boxed().findFirst()
            .orElse(null);
        if (sockets != null) {
            return sockets * pool.getQuantity();
        }
        return null;
    }

    private List<String> extractProductIds(Collection<CandlepinProvidedProduct> providedProducts) {
        return providedProducts.stream().map(CandlepinProvidedProduct::getProductId)
            .collect(Collectors.toList());
    }
}
