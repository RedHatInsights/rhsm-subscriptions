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
package com.redhat.swatch.configuration.registry;

import java.util.Collection;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
// constructor is private so that the factory method is the only way to get a MetricId
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ProductId {
  private final String value;

  /**
   * Creates a ProductId from a String.
   *
   * @param value String value for the ProductId
   * @return a validated ProductId
   * @throws IllegalArgumentException if the productId is not defined in configuration
   */
  public static ProductId fromString(String value) {
    // NOTE: if the volume of data becomes large enough, we can pre-cache these values.
    return SubscriptionDefinitionRegistry.getInstance().getSubscriptions().stream()
        .map(SubscriptionDefinition::getVariants)
        .flatMap(Collection::stream)
        .map(Variant::getTag)
        .filter(productId -> productId.equalsIgnoreCase(value))
        .findFirst()
        .map(ProductId::new)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("ProductId: %s not found in configuration", value)));
  }

  public String toString() {
    return getValue();
  }
}
