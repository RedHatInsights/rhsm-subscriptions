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
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
public class Subscription {
  private final String orgId;
  private final Product product;
  private final String subscriptionId;
  private final String subscriptionNumber;
  private final Offering offering;
  private final OffsetDateTime startDate;
  private final OffsetDateTime endDate;
  private final BillingProvider billingProvider;
  private final String billingAccountId;
  private final Map<MetricId, Double> subscriptionMeasurements;

  public static Subscription buildRhelSubscription(String orgId, Map<MetricId, Double> capacity) {
    return buildRhelSubscription(orgId, capacity, null);
  }

  public static Subscription buildRhelSubscriptionUsingSku(
      String orgId, Map<MetricId, Double> capacity, String sku) {
    Objects.requireNonNull(sku, "sku cannot be null");
    return buildRhelSubscription(orgId, capacity, sku);
  }

  public static Subscription buildRhelSubscription(
      String orgId, Map<MetricId, Double> capacity, String sku) {
    Objects.requireNonNull(orgId, "orgId cannot be null");

    String seed = RandomUtils.generateRandom();
    return Subscription.builder()
        .subscriptionMeasurements(capacity)
        .orgId(orgId)
        .product(Product.RHEL)
        .offering(
            Offering.buildRhelOffering(
                Objects.requireNonNullElse(sku, seed),
                capacity.getOrDefault(MetricIdUtils.getCores(), null),
                capacity.getOrDefault(MetricIdUtils.getSockets(), null)))
        .subscriptionId(seed)
        .subscriptionNumber(seed)
        .startDate(OffsetDateTime.now().minusDays(1))
        .endDate(OffsetDateTime.now().plusDays(1))
        .build();
  }
}
