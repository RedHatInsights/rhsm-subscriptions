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
package org.candlepin.subscriptions.files;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registry of product profiles. Essentially a map of profile names to profile objects */
public class ProductProfileRegistry {
  private static final Logger log = LoggerFactory.getLogger(ProductProfileRegistry.class);

  private final Map<Integer, ProductProfile> engProductIdToProfileMap;
  // NB: We should use ProductId as the key for type safety but that requires test updates
  private final Map<String, ProductProfile> swatchProductIdToProfileMap;
  private final Map<String, ProductProfile> productProfilesByName;
  private final Map<ProductUom, String> metricBySwatchProductAndUom;
  private final Map<String, String> swatchProductIdByOfferingProductName;
  private static final ProductProfileRegistry DEFAULT_REGISTRY = new ProductProfileRegistry();

  public static ProductProfileRegistry getDefaultRegistry() {
    return DEFAULT_REGISTRY;
  }

  public ProductProfileRegistry() {
    productProfilesByName = new HashMap<>();
    engProductIdToProfileMap = new HashMap<>();
    swatchProductIdToProfileMap = new HashMap<>();
    metricBySwatchProductAndUom = new HashMap<>();
    swatchProductIdByOfferingProductName = new HashMap<>();
  }

  // Only classes in this package should have any need to add product profiles
  void addProductProfile(ProductProfile profile) {
    if (this.productProfilesByName.containsKey(profile.getName())) {
      throw new IllegalStateException(
          String.format("A profile is already registered with name: %s", profile.getName()));
    }

    this.productProfilesByName.put(profile.getName(), profile);

    Set<SubscriptionWatchProduct> profileProducts = profile.getProducts();
    if (profileProducts.isEmpty()) {
      log.warn(
          "No products are set in product profile {}. This is probably a mistake.",
          profile.getName());
    }

    try {
      Set<Integer> duplicateProducts =
          profileProducts.stream()
              .map(SubscriptionWatchProduct::getEngProductId)
              .map(Integer::parseInt)
              .filter(engProductIdToProfileMap::containsKey)
              .collect(Collectors.toSet());

      if (!duplicateProducts.isEmpty()) {
        throw new IllegalStateException(
            "Failed to add profile "
                + profile.getName()
                + ".  The "
                + "following engineering product IDs are already defined: "
                + duplicateProducts);
      }
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Could not parse product: " + e.getMessage());
    }

    profileProducts.forEach(
        x -> engProductIdToProfileMap.put(Integer.parseInt(x.getEngProductId()), profile));

    Stream<String> fromProducts =
        profileProducts.stream().flatMap(x -> x.getSwatchProductIds().stream());
    Stream<String> fromRoles =
        profile.getSyspurposeRoles().stream().flatMap(r -> r.getSwatchProductIds().stream());
    Set<String> swatchProdIds =
        Stream.of(fromProducts, fromRoles).flatMap(Function.identity()).collect(Collectors.toSet());

    Set<String> duplicateIds =
        swatchProdIds.stream()
            .filter(swatchProductIdToProfileMap::containsKey)
            .collect(Collectors.toSet());

    if (!duplicateIds.isEmpty()) {
      throw new IllegalStateException(
          "Failed to add profile "
              + profile.getName()
              + ". The following Subscription Watch product IDs are already defined: "
              + duplicateIds);
    }

    swatchProdIds.forEach(x -> swatchProductIdToProfileMap.put(x, profile));

    metricBySwatchProductAndUom.putAll(profile.getMetricByProductAndUom());

    swatchProductIdByOfferingProductName.putAll(profile.getSwatchProductIdsByOfferingProductName());
  }

  public ProductProfile findProfileForSwatchProductId(String productId) {
    if (swatchProductIdToProfileMap.containsKey(productId)) {
      return swatchProductIdToProfileMap.get(productId);
    }
    log.warn("ProductId {} not found in product profile registry. Returning default.", productId);
    return ProductProfile.getDefault();
  }

  public ProductProfile findProfileForSwatchProductId(ProductId productId) {
    return findProfileForSwatchProductId(productId.toString());
  }

  public ProductProfile findProfileForEngProductId(String product) {
    return findProfileForEngProductId(Integer.parseInt(product));
  }

  public ProductProfile findProfileForEngProductId(Integer product) {
    if (engProductIdToProfileMap.containsKey(product)) {
      return engProductIdToProfileMap.get(product);
    }
    log.warn("Product {} not found in product profile registry. Returning default.", product);
    return ProductProfile.getDefault();
  }

  public Optional<ProductProfile> getProfileByName(String name) {
    return Optional.ofNullable(productProfilesByName.get(name));
  }

  public Set<String> listProfiles() {
    return productProfilesByName.keySet();
  }

  public Set<ProductProfile> getAllProductProfiles() {
    return new HashSet<>(productProfilesByName.values());
  }

  public Map<Integer, Set<String>> getEngProductIdToSwatchProductIdsMap() {
    Map<Integer, Set<String>> engProductIdToSwatchProductIdsMap = new HashMap<>();
    engProductIdToProfileMap.values().stream()
        .distinct()
        .flatMap(x -> x.getProducts().stream())
        .forEach(
            x -> {
              Integer engId = Integer.parseInt(x.getEngProductId());
              if (engProductIdToSwatchProductIdsMap.containsKey(engId)) {
                throw new IllegalStateException("Duplicate engineering product ID found: " + engId);
              }
              engProductIdToSwatchProductIdsMap.put(engId, x.getSwatchProductIds());
            });
    return engProductIdToSwatchProductIdsMap;
  }

  public Map<String, Set<String>> getRoleToSwatchProductIdsMap() {
    Map<String, Set<String>> roleToProductsMap = new HashMap<>();
    productProfilesByName.values().stream()
        .distinct()
        .flatMap(x -> x.getSyspurposeRoles().stream())
        .forEach(
            x -> {
              String role = x.getName();
              if (roleToProductsMap.containsKey(role)) {
                throw new IllegalStateException("Duplicate role found: " + role);
              }
              roleToProductsMap.put(role, x.getSwatchProductIds());
            });
    return roleToProductsMap;
  }

  public Map<String, String> getArchToSwatchProductIdsMap() {
    Map<String, String> archToProductMap = new HashMap<>();
    productProfilesByName.values().stream()
        .distinct()
        .map(ProductProfile::getArchitectureSwatchProductIdMap)
        .forEach(archToProductMap::putAll);
    return archToProductMap;
  }

  /**
   * Verify that the granularity requested is compatible with the finest granularity supported by
   * the product. For example, if the requester asks for HOURLY granularity but the product only
   * supports DAILY granularity, we can't meaningfully fulfill that request.
   *
   * @throws IllegalStateException if the granularities are not compatible
   */
  public void validateGranularityCompatibility(
      ProductId productId, Granularity requestedGranularity) {
    ProductProfile productProfile = findProfileForSwatchProductId(productId);
    if (!productProfile.supportsGranularity(requestedGranularity)) {
      String msg =
          String.format(
              "%s does not support any granularity finer than %s",
              productId.toString(), productProfile.getFinestGranularity());
      throw new IllegalStateException(msg);
    }
  }

  /**
   * Look up a metric ID from its swatch profile ID and measurement UOM.
   *
   * @param swatchProfileId swatch profile ID
   * @param uom measurement UOM
   * @return the metric
   */
  public String lookupMetricId(String swatchProfileId, Measurement.Uom uom) {
    return this.metricBySwatchProductAndUom.get(new ProductUom(swatchProfileId, uom.toString()));
  }

  /**
   * Look up a product ID by its offering product name
   *
   * @param offeringProductName the offering (often OpenShift) product name
   * @return the product ID
   */
  public String getProductIdByOfferingProductName(String offeringProductName) {
    return this.swatchProductIdByOfferingProductName.get(offeringProductName);
  }
}
