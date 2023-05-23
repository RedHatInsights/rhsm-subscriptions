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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.UpperCamelCaseStrategy.class)
public class UmbSubscription {
  private static final ZoneId RALEIGH_TIME_ZONE = ZoneId.of("America/New_York");
  private Identifiers identifiers;
  private SubscriptionStatus status;
  private Integer quantity;

  @JsonProperty("effectiveStartDate")
  private LocalDateTime effectiveStartDate;

  @JsonProperty("effectiveEndDate")
  private LocalDateTime effectiveEndDate;

  @JsonProperty("Product")
  @JacksonXmlElementWrapper(useWrapping = false)
  private SubscriptionProduct[] products;

  private Optional<String> getReference(String system, String entityName, String qualifier) {
    if (identifiers == null || identifiers.getReferences() == null) {
      return Optional.empty();
    }
    return Arrays.stream(identifiers.getReferences())
        .filter(reference -> system.equals(reference.getSystem()))
        .filter(reference -> entityName.equals(reference.getEntityName()))
        .filter(reference -> qualifier.equals(reference.getQualifier()))
        .map(Reference::getValue)
        .findFirst();
  }

  private Optional<String> getIdentifier(String system, String entityName, String qualifier) {
    if (identifiers == null || identifiers.getIds() == null) {
      return Optional.empty();
    }
    return Arrays.stream(identifiers.getIds())
        .filter(reference -> system.equals(reference.getSystem()))
        .filter(reference -> entityName.equals(reference.getEntityName()))
        .filter(reference -> qualifier.equals(reference.getQualifier()))
        .map(Reference::getValue)
        .findFirst();
  }

  public String getSubscriptionNumber() {
    return getIdentifier("SUBSCRIPTION", "Subscription", "number").orElseThrow();
  }

  public String getWebCustomerId() {
    return getReference("WEB", "Customer", "id")
        .map(value -> value.replace("_ICUST", ""))
        .orElseThrow();
  }

  public String getSku() {
    if (products.length != 1) {
      throw new IllegalStateException("Could not find top level SKU for subscription " + this);
    }
    return products[0].getSku();
  }

  public SubscriptionProductStatus[] getProductStatusState() {
    if (products.length != 1) {
      throw new IllegalStateException(
          "Could not find top level product with a status for subscription " + this);
    }
    return products[0].getProduct().getStatus();
  }

  public String getEbsAccountNumber() {
    return getReference("EBS", "Account", "number").orElse(null);
  }

  public static OffsetDateTime convertToUtc(LocalDateTime timestamp) {
    if (timestamp == null) {
      return null;
    }
    return timestamp
        .atZone(RALEIGH_TIME_ZONE)
        .withZoneSameInstant(ZoneOffset.UTC)
        .toOffsetDateTime();
  }

  public OffsetDateTime getEffectiveStartDateInUtc() {
    return convertToUtc(getEffectiveStartDate());
  }

  public OffsetDateTime getEffectiveEndDateInUtc() {
    return convertToUtc(getEffectiveEndDate());
  }
}
