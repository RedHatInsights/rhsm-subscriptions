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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Registry of product profiles.  Essentially a map of profile names to profile objects */
public class ProductProfileRegistry {
    private static final Logger log = LoggerFactory.getLogger(ProductProfileRegistry.class);

    private final Map<String, ProductProfile> profileMap;
    private static final ProductProfileRegistry DEFAULT_REGISTRY = new ProductProfileRegistry();

    public static ProductProfileRegistry getDefaultRegistry() {
        return DEFAULT_REGISTRY;
    }

    public ProductProfileRegistry() {
        profileMap = new HashMap<>();
    }

    // Only classes in this package should have any need to add product profiles
    void addProductProfile(ProductProfile profile) {
        Set<String> profileIds = profile.getProductIds();
        if (profileIds.isEmpty()) {
            log.warn("No product IDs are set in product profile {}. This is probably a mistake.",
                profile.getName());
        }

        Set<String> duplicates =
            profileIds.stream().filter(profileMap::containsKey).collect(Collectors.toSet());

        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Failed to add profile " + profile.getName() + ".  The " +
                "following product IDs are already defined: " + duplicates);
        }

        profileIds.forEach(x -> profileMap.put(x, profile));
    }

    public ProductProfile findProfile(String productId) {
        if (profileMap.containsKey(productId)) {
            return profileMap.get(productId);
        }
        log.debug("Product {} not found in product profile registry. Returning default.", productId);
        return ProductProfile.getDefault();
    }

    public Set<String> listProfiles() {
        return profileMap.values().stream().map(ProductProfile::getName).collect(Collectors.toSet());
    }

    public Set<ProductProfile> getAllProductProfiles() {
        return new HashSet<>(profileMap.values());
    }
}
