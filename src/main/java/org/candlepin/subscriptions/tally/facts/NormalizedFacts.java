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
package org.candlepin.subscriptions.tally.facts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A normalized version of an inventory host's facts.
 */
public class NormalizedFacts {

    public static final String PRODUCTS_KEY = "products";
    public static final String CORES_KEY = "cores";

    private Set<String> products;
    private Integer cores;

    public NormalizedFacts() {
        products = new HashSet<>();
    }

    public Set<String> getProducts() {
        return products;
    }

    public void setProducts(Set<String> products) {
        this.products = products;
    }

    public void addProduct(String product) {
        products.add(product);
    }

    public Integer getCores() {
        return cores;
    }

    public void setCores(Integer cores) {
        this.cores = cores;
    }

    public Map<String, Object> toInventoryPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put(PRODUCTS_KEY, this.products);
        payload.put(CORES_KEY, this.cores);
        return payload;
    }
}
