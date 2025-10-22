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
package domain;

import com.redhat.swatch.component.tests.utils.RandomUtils;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@EqualsAndHashCode(callSuper = true)
public class Contract extends Subscription {

  // Constants for ROSA contract defaults
  private static final String VCPU_HOUR_METRIC = "four_vcpu_hour";
  private static final String CORES_METRIC = "Cores";
  private static final double DEFAULT_VCPU_CAPACITY = 10.0;
  private static final double VCPU_TO_CORES_RATIO = 0.25;
  private static final String DEFAULT_PRODUCT_ID = "rosa";

  /** Customer ID from marketplace */
  private final String customerId;

  /** Seller Account ID */
  private final String sellerAccountId;

  /** Product Code */
  private final String productCode;

  /** Contract capacity dimensions */
  private final Map<String, Double> contractCapacity;

  public static Contract buildRosaContract(
      String orgId, String sku, BillingProvider billingProvider) {
    Objects.requireNonNull(orgId, "orgId cannot be null");
    Objects.requireNonNull(sku, "sku cannot be null");
    Objects.requireNonNull(billingProvider, "billingProvider cannot be null");

    String seed = RandomUtils.generateRandom();
    return Contract.builder()
        .customerId("customer" + seed)
        .sellerAccountId("seller" + seed)
        .productCode("product" + seed)
        .contractCapacity(Map.of(VCPU_HOUR_METRIC, DEFAULT_VCPU_CAPACITY))
        .subscriptionMeasurements(Map.of(CORES_METRIC, DEFAULT_VCPU_CAPACITY * VCPU_TO_CORES_RATIO))
        .billingProvider(billingProvider)
        .billingAccountId("billing" + seed)
        .orgId(orgId)
        .productId(DEFAULT_PRODUCT_ID)
        .offering(Offering.buildRosaOffering(sku))
        .subscriptionId(seed)
        .subscriptionNumber(seed)
        .startDate(OffsetDateTime.now().minusDays(1))
        .endDate(OffsetDateTime.now().plusDays(1))
        .build();
  }
}
