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
package org.candlepin.subscriptions.umb;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class UmbOperationalProduct {
  private String sku;
  private String skuDescription;
  private String role;
  private ProductRelationship productRelationship;
  private Identifiers identifiers;

  @JsonProperty("Attribute")
  @JacksonXmlElementWrapper(useWrapping = false)
  private ProductAttribute[] attributes;

  public String getAttribute(String code) {
    if (attributes == null) {
      return null;
    }
    return Arrays.stream(attributes)
        .filter(attribute -> code.equals(attribute.getCode()))
        .map(ProductAttribute::getValue)
        .findFirst()
        .orElse(null);
  }

  public Set<String> getChildSkus() {
    if (productRelationship == null || productRelationship.getChildProducts() == null) {
      return Set.of();
    }
    return Arrays.stream(productRelationship.getChildProducts())
        .map(ChildProduct::getSku)
        .collect(Collectors.toSet());
  }
}
