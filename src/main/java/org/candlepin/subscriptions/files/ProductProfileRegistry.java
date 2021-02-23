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

import org.candlepin.subscriptions.utilization.api.model.ProductId;

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

    private final Map<Integer, ProductProfile> productToProfileMap;
    // NB: We should use ProductId as the key for type safety but that requires test updates
    private final Map<String, ProductProfile> productIdToProfileMap;
    private static final ProductProfileRegistry DEFAULT_REGISTRY = new ProductProfileRegistry();

    public static ProductProfileRegistry getDefaultRegistry() {
        return DEFAULT_REGISTRY;
    }

    public ProductProfileRegistry() {
        productToProfileMap = new HashMap<>();
        productIdToProfileMap = new HashMap<>();
    }

    // Only classes in this package should have any need to add product profiles
    void addProductProfile(ProductProfile profile) {
        Set<SubscriptionWatchProduct> profileProducts = profile.getProducts();
        if (profileProducts.isEmpty()) {
            log.warn("No products are set in product profile {}. This is probably a mistake.",
                profile.getName());
        }

        try {
            Set<Integer> duplicateProducts = profileProducts.stream()
                .map(SubscriptionWatchProduct::getProduct)
                .map(Integer::parseInt)
                .filter(productToProfileMap::containsKey)
                .collect(Collectors.toSet());

            if (!duplicateProducts.isEmpty()) {
                throw new IllegalStateException("Failed to add profile " + profile.getName() + ".  The " +
                    "following products are already defined: " + duplicateProducts);
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalStateException("Could not parse product: " + e.getMessage());
        }

        profileProducts.forEach(x -> productToProfileMap.put(Integer.parseInt(x.getProduct()), profile));

        Set<String> duplicateIds = profileProducts.stream()
            .flatMap(x -> x.getProductIds().stream())
            .filter(productIdToProfileMap::containsKey)
            .collect(Collectors.toSet());

        if (!duplicateIds.isEmpty()) {
            throw new IllegalStateException("Failed to add profile " + profile.getName() +
                ". The following productIds are already defined: " + duplicateIds);
        }

        profileProducts.stream()
            .flatMap(x -> x.getProductIds().stream())
            .forEach(x -> productIdToProfileMap.put(x, profile));
    }

    public ProductProfile findProfileForProductId(String productId) {
        if (productIdToProfileMap.containsKey(productId)) {
            return productIdToProfileMap.get(productId);
        }
        log.warn("ProductId {} not found in product profile registry. Returning default.", productId);
        return ProductProfile.getDefault();
    }

    public ProductProfile findProfileForProductId(ProductId productId) {
        return findProfileForProductId(productId.toString());
    }

    public ProductProfile findProfileForProduct(String product) {
        return findProfileForProduct(Integer.parseInt(product));
    }

    public ProductProfile findProfileForProduct(Integer product) {
        if (productToProfileMap.containsKey(product)) {
            return productToProfileMap.get(product);
        }
        log.warn("Product {} not found in product profile registry. Returning default.", product);
        return ProductProfile.getDefault();
    }

    public Set<String> listProfiles() {
        return productToProfileMap.values().stream().map(ProductProfile::getName).collect(Collectors.toSet());
    }

    public Set<ProductProfile> getAllProductProfiles() {
        return new HashSet<>(productToProfileMap.values());
    }

    public Map<Integer, Set<String>> getProductToProductIdsMap() {
        Map<Integer, Set<String>> productToProductIdsMap = new HashMap<>();
        productToProfileMap.values().stream()
            .distinct()
            .flatMap(x -> x.getProducts().stream())
            .forEach(x -> {
                Integer id = Integer.parseInt(x.getProduct());
                if (productToProductIdsMap.containsKey(id)) {
                    throw new IllegalStateException("Duplicate productId found: " + id);
                }
                productToProductIdsMap.put(id, x.getProductIds());
            });
        return productToProductIdsMap;
    }

    public Map<String, Set<String>> getRoleToProductsMap() {
        Map<String, Set<String>> roleToProductsMap = new HashMap<>();
        productToProfileMap.values().stream()
            .distinct()
            .flatMap(x -> x.getSyspurposeRoles().stream())
            .forEach(x -> {
                String role = x.getName();
                if (roleToProductsMap.containsKey(role)) {
                    throw new IllegalStateException("Duplicate role found: " + role);
                }
                roleToProductsMap.put(role, x.getProductIds());
            });
        return roleToProductsMap;
    }

    public Map<String, String> getArchToProductMap() {
        Map<String, String> archToProductMap = new HashMap<>();
        productToProfileMap.values().stream()
            .distinct()
            .map(ProductProfile::getArchitectureProductIdMap)
            .forEach(archToProductMap::putAll);
        return archToProductMap;
    }
}
