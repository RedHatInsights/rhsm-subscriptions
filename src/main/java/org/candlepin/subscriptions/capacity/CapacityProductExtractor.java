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

import org.candlepin.subscriptions.files.ProductIdToProductsMapSource;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Given a list of product IDs provided by a given product/subscription, returns the effective view of
 * products for capacity.
 */
@Component
public class CapacityProductExtractor {

    private final Map<Integer, List<String>> productIdToProductsMap;

    public CapacityProductExtractor(ProductIdToProductsMapSource productIdToProductsMapSource)
        throws IOException {

        this.productIdToProductsMap = productIdToProductsMapSource.getValue();
    }

    public Set<String> getProducts(Collection<Integer> productIds) {
        // NOTE: this logic can grow over time (e.g. to blacklist certain combinations, etc).
        return productIds.stream()
            .map(productIdToProductsMap::get).filter(Objects::nonNull).flatMap(List::stream)
            .collect(Collectors.toSet());
    }
}
