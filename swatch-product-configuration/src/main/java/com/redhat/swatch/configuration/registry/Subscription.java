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

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
   * Enables capability to inherit billing model information from their parent subscription. Unused
   * prior to <a href="https://issues.redhat.com/browse/BIZ-629">BIZ-629</a>
   */
  private String parentSubscription;

  /**
   * defines an "in-the-box" subscription. Considered included from both usage and capacity
   * perspectives.
   */
  private List<String> includedSubscriptions = new ArrayList<>();

  private List<Variant> variants = new ArrayList<>();
  private BillingWindow billingWindow;
  private String serviceType;
  private List<Metric> metrics = new ArrayList<>();
  private Defaults defaults;

  /**
   * @param serviceType
   * @return Optional<Subscription>
   */
  public static Optional<Subscription> findByServiceType(String serviceType) {

    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> Objects.equals(subscription.getServiceType(), serviceType))
        .findFirst();
  }

  public List<String> getMetricIds() {
    return this.getMetrics().stream()
        .map(Metric::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public Optional<Metric> getMetric(String metricId) {
    return this.getMetrics().stream().filter(x -> Objects.equals(x.getId(), metricId)).findFirst();
  }

  public static Optional<Subscription> findById(String id) {
    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> Objects.equals(subscription.getId(), id))
        .findFirst();
  }

  /**
   * @return List<String> serviceTypes
   */
  public static List<String> getAllServiceTypes() {
    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .map(Subscription::getServiceType)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public boolean isPrometheusEnabled() {

    return this.getMetrics().stream().anyMatch(metric -> Objects.nonNull(metric.getPrometheus()));
  }

  public String getFinestGranularity() {

    return this.isPrometheusEnabled() ? "HOURLY" : "DAILY";
  }

  public List<String> getSupportedGranularity() {
    var granularity = List.of("HOURLY", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY");

    return isPrometheusEnabled() ? granularity : granularity.subList(1, granularity.size());
  }

  /**
   * An engineering id can be found in either a fingerprint or variant. Check the variant first. If
   * not found, check the fingerprint.
   *
   * @param engProductId
   * @return Optional<Subscription> subscription
   */
  public static Optional<Subscription> lookupSubscriptionByEngId(String engProductId) {
    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> variant.getEngineeringIds().contains(engProductId)))
        .findFirst();
  }

  /**
   * Looks for productName matching a variant
   *
   * @param productName
   * @return Optional<Subscription>
   */
  public static Optional<Subscription> lookupSubscriptionByProductName(String productName) {
    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> variant.getProductNames().contains(productName)))
        .findFirst();
  }

  /**
   * Looks for role matching a variant
   *
   * @param role
   * @return Optional<Subscription>
   */
  public static Optional<Subscription> lookupSubscriptionByRole(String role) {
    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> variant.getRoles().contains(role)))
        .findFirst();
  }

  /**
   * Looks for tag matching a variant
   *
   * @param tag
   * @return Optional<Subscription>
   */
  public static Optional<Subscription> lookupSubscriptionByTag(@NotNull @NotEmpty String tag) {

    return SubscriptionRegistry.getInstance().getSubscriptions().stream()
        .filter(subscription -> !subscription.getVariants().isEmpty())
        .filter(
            subscription ->
                subscription.getVariants().stream()
                    .anyMatch(variant -> Objects.equals(tag, variant.getTag())))
        .findFirst();
  }
}
