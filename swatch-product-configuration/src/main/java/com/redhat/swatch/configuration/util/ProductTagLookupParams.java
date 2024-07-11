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
package com.redhat.swatch.configuration.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ProductTagLookupParams {

  Set<Integer> engIds;
  String role;
  String productName;
  Set<String> metricIds;
  Boolean isPaygEligibleProduct;
  Boolean is3rdPartyMigration;
  String productTag;

  ProductTagLookupParams(
      Set<Integer> engIds,
      String role,
      String productName,
      Set<String> metricIds,
      Boolean isPaygEligibleProduct,
      Boolean is3rdPartyMigration,
      String productTag) {
    this.engIds = engIds;
    this.role = role;
    this.productName = productName;
    this.metricIds = metricIds;
    this.isPaygEligibleProduct = isPaygEligibleProduct;
    this.is3rdPartyMigration = is3rdPartyMigration;
    this.productTag = productTag;
  }

  public static ProductTagLookupParamsBuilder builder() {
    return new ProductTagLookupParamsBuilder();
  }

  public static class ProductTagLookupParamsBuilder {

    private Set<Integer> engIds = new HashSet<>();
    private String role;
    private String productName;
    private Set<String> metricIds = new HashSet<>();
    private Boolean isPaygEligibleProduct;
    private Boolean is3rdPartyMigration;
    private String productTag;

    ProductTagLookupParamsBuilder() {}

    public ProductTagLookupParamsBuilder engIds(Set<Integer> engIds) {
      if (engIds == null) {
        return this;
      }
      this.engIds = engIds;
      return this;
    }

    public ProductTagLookupParamsBuilder engIds(Collection<String> engIds) {
      if (engIds != null) {
        this.engIds =
            engIds.stream()
                .map(
                    id -> {
                      try {
                        return Integer.valueOf(id);
                      } catch (NumberFormatException e) {
                        log.warn("Invalid number format: {}", id);
                        return null; // Return null for invalid elements
                      }
                    })
                .filter(Objects::nonNull) // Filter out invalid elements
                .collect(Collectors.toSet());
      }
      return this;
    }

    public ProductTagLookupParamsBuilder role(String role) {
      this.role = role;
      return this;
    }

    public ProductTagLookupParamsBuilder productName(String productName) {
      this.productName = productName;
      return this;
    }

    public ProductTagLookupParamsBuilder metricIds(Set<String> metricIds) {
      if (metricIds == null) {
        return this;
      }
      this.metricIds = metricIds;
      return this;
    }

    public ProductTagLookupParamsBuilder isPaygEligibleProduct(Boolean isPaygEligibleProduct) {
      this.isPaygEligibleProduct = isPaygEligibleProduct;
      return this;
    }

    public ProductTagLookupParamsBuilder is3rdPartyMigration(Boolean is3rdPartyMigration) {
      this.is3rdPartyMigration = is3rdPartyMigration;
      return this;
    }

    public ProductTagLookupParamsBuilder productTag(String productTag) {
      this.productTag = productTag;
      return this;
    }

    public ProductTagLookupParams build() {

      return new ProductTagLookupParams(
          this.engIds,
          this.role,
          this.productName,
          this.metricIds,
          this.isPaygEligibleProduct,
          this.is3rdPartyMigration,
          this.productTag);
    }
  }
}
