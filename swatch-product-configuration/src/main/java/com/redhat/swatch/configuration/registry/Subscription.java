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

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

/**
 * Subscription is an offering with one or more variants. Defines a specific metering model. Has a
 * single technical fingerprint. Defines a set of metrics.
 */
@Data
public class Subscription {

  /**
   * A family of solutions that is logically related, having one or more subscriptions distinguished
   * by unique technical fingerprints (e.g. different arches)
   */
  @NotNull @NotEmpty private String platform; // required

  @NotNull @NotEmpty private String id; // required

  /**
   * Enables capability to inherit billing model information from their parent subscription Unused
   * prior to https://issues.redhat.com/browse/BIZ-629
   */
  private String parentSubscription;

  /**
   * defines an "in-the-box" subscription. Considered included from both usage and capacity
   * perspectives.
   */
  private List<String> includedSubscriptions;

  private Fingerprint fingerprint;
  private List<Variant> variants;
  private BillingWindow billingWindow;
  private String serviceType;
  private List<Metric> metrics;
  private Defaults defaults;

  public static Optional<Subscription> findByServiceType(String serviceType) throws IOException {
    return SubscriptionConfigRegistry.getInstance().findByServiceType(serviceType);
  }

  public static Optional<Subscription> findByArch(String arch) throws IOException {
    return SubscriptionConfigRegistry.getInstance().findByArch(arch);
  }

  // Subscription.getFinestGranularity()
  // Subscription.isPrometheusEnabled()
  // Subscription.supportsGranularity()

  public List<String> getMetricIds() {
    return this.getMetrics().stream()
        .map(Metric::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public Optional<Metric> getMetric(String metricId) {
    return this.getMetrics().stream().filter(Objects::nonNull).findFirst();
  }

  public static Optional<Subscription> findById(String id) throws IOException {
    return SubscriptionConfigRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> Objects.equals(subscription.getId(), id))
        .findFirst();
  }

  public static List<String> getAllServiceTypes() throws IOException {
    return SubscriptionConfigRegistry.getInstance().getAllServiceTypes();
  }
}
