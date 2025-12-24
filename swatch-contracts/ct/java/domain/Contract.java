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

import com.redhat.swatch.component.tests.logging.Log;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.MetricId;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = true)
public class Contract extends Subscription {

  /** Customer ID from marketplace */
  private final String customerId;

  /** Seller Account ID (only relevant for AWS contracts) */
  private final String sellerAccountId;

  /** Product Code */
  private final String productCode;

  /** Plan ID (only relevant for Azure contracts) */
  private final String planId;

  /** Resource ID (only relevant for Azure contracts) */
  private final String resourceId;

  /** Client ID (only relevant for Azure contracts) */
  private final String clientId;

  public Map<String, Double> getContractMetrics() {
    Map<String, Double> metrics = new HashMap<>();
    for (var entry : getSubscriptionMeasurements().entrySet()) {
      Metric metric = getProduct().getMetric(entry.getKey());
      if (metric != null) {
        metrics.put(
            metric.getAwsDimension(),
            entry.getValue() * getProduct().getMetric(entry.getKey()).getBillingFactor());
      } else {
        Log.warn("Metric %s not found in product %s", entry.getKey(), getProduct().getId());
      }
    }

    return metrics;
  }

  public static Contract buildRosaContract(
      String orgId, BillingProvider billingProvider, Map<MetricId, Double> capacity) {
    return buildRosaContract(orgId, billingProvider, capacity, null);
  }

  public static Contract buildRosaContract(
      String orgId, BillingProvider billingProvider, Map<MetricId, Double> capacity, String sku) {
    Objects.requireNonNull(orgId, "orgId cannot be null");
    Objects.requireNonNull(billingProvider, "billingProvider cannot be null");
    Objects.requireNonNull(capacity, "capacity cannot be null");

    Product product = Product.ROSA;
    String seed = RandomUtils.generateRandom();
    return Contract.builder()
        .customerId("customer" + seed)
        .sellerAccountId("seller" + seed)
        .productCode("product" + seed)
        .planId("plan" + seed)
        .clientId("clientId" + seed)
        .resourceId("resourceId" + seed)
        .subscriptionMeasurements(capacity)
        .billingProvider(billingProvider)
        .billingAccountId("billing" + seed)
        .orgId(orgId)
        .product(product)
        .offering(Offering.buildRosaOffering(Objects.requireNonNullElse(sku, seed)))
        .subscriptionId(seed)
        .subscriptionNumber(seed)
        .startDate(OffsetDateTime.now().minusDays(1))
        .endDate(OffsetDateTime.now().plusDays(1))
        .build();
  }
}
