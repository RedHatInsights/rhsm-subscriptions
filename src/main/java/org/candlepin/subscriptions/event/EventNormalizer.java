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
package org.candlepin.subscriptions.event;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Normalizes incoming events by filtering and transforming them based on product configuration.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Filters events to only include PAYG-eligible product tags
 *   <li>Filters measurements to only those supported by the product tags
 *   <li>Flattens multi-tag, multi-measurement events into individual events with one tag and one
 *       measurement each
 *   <li>Ensures only valid tag-measurement combinations (as defined in product configuration) are
 *       produced
 * </ul>
 *
 * <p>This normalization ensures that downstream processing only handles valid, PAYG-eligible events
 * with a consistent structure.
 */
@Component
public class EventNormalizer {

  private static final Logger log = LoggerFactory.getLogger(EventNormalizer.class);
  private static final String ANSIBLE_INFRASTRUCTURE_HOUR = "Ansible Infrastructure Hour";

  private final ResolvedEventMapper resolvedEventMapper;

  public EventNormalizer(ResolvedEventMapper resolvedEventMapper) {
    this.resolvedEventMapper = resolvedEventMapper;
  }

  /**
   * Flattens an event into multiple events, each with at most one tag and one measurement. Only
   * creates events for valid tag-measurement combinations as defined in the product configuration.
   * Invalid combinations (where the tag does not support the measurement) are filtered out.
   *
   * <pre>
   *   Incoming event:
   *      Event(['OpenShift-metrics', 'rhel-for-x86-els-payg-addon'], {cores: 2, vCPUs: 4})
   *   Flattened events (filtered to valid combinations only):
   *      Event(['OpenShift-metrics'], {cores: 2})       // OpenShift-metrics supports Cores
   *      Event(['rhel-for-x86-els-payg-addon'], {vCPUs: 4})  // rhel-for-x86-els-payg-addon supports vCPUs
   *   Invalid combinations filtered out:
   *      Event(['OpenShift-metrics'], {vCPUs: 4})       // OpenShift-metrics does not support vCPUs
   *      Event(['rhel-for-x86-els-payg-addon'], {cores: 2})  // rhel-for-x86-els-payg-addon does not support Cores
   * </pre>
   *
   * @param event the event to flatten
   * @return list of flattened events containing only valid tag-measurement pairs
   */
  public List<Event> flattenEventUsage(Event event) {
    Set<String> tags = event.getProductTag();
    Set<Measurement> measurements = new HashSet<>(event.getMeasurements());

    return tags.stream()
        .flatMap(
            tag -> {
              // Get the metrics supported by this tag from the product configuration
              Set<String> supportedMetrics =
                  MetricIdUtils.getMetricIdsFromConfigForTag(tag)
                      .map(MetricId::toUpperCaseFormatted)
                      .collect(Collectors.toSet());

              // Filter measurements to only those supported by this tag
              return measurements.stream()
                  .filter(
                      m ->
                          supportedMetrics.contains(
                              MetricIdUtils.toUpperCaseFormatted(m.getMetricId())))
                  .map(measurement -> create(event, tag, measurement));
            })
        .toList();
  }

  public Event normalizeEvent(Event event) {
    // NOTE we will probably remove the below serviceType normalization
    // after https://issues.redhat.com/browse/SWATCH-2533
    // placeholder card to remove it in https://issues.redhat.com/browse/SWATCH-2794
    if (Objects.nonNull(event.getServiceType())
        && event.getServiceType().equals(ANSIBLE_INFRASTRUCTURE_HOUR)) {
      event.setServiceType("Ansible Managed Node");
    }

    // Filter out non-paygo product tags and measurements
    Set<String> payGoTags = getPayGoTags(event.getProductTag());
    event.setProductTag(payGoTags);
    event.setMeasurements(getApplicableMeasurements(event.getMeasurements(), payGoTags));

    return event;
  }

  private Set<String> getPayGoTags(Set<String> productTags) {
    if (productTags == null || productTags.isEmpty()) {
      return productTags;
    }

    Set<String> paygoTags =
        productTags.stream().filter(this::isPaygoEligibleTag).collect(Collectors.toSet());

    if (paygoTags.isEmpty()) {
      log.debug("No paygo-eligible tags found in {}.", productTags);
      return Set.of();
    }
    return paygoTags;
  }

  private List<Measurement> getApplicableMeasurements(
      List<Measurement> measurements, Set<String> paygoTags) {
    if (measurements == null || measurements.isEmpty()) {
      return measurements;
    }

    // If there are no paygo tags, return all measurements since we can't determine validity
    // at this point. Event validation will catch this if the event does not provide enough
    // information to determine the tag.
    if (paygoTags == null || paygoTags.isEmpty()) {
      return measurements;
    }

    // Get all supported metrics for the specified tags
    Set<String> supportedMetrics =
        paygoTags.stream()
            .flatMap(MetricIdUtils::getMetricIdsFromConfigForTag)
            .map(MetricId::toUpperCaseFormatted)
            .collect(Collectors.toSet());

    List<Measurement> validMeasurements =
        measurements.stream()
            .filter(
                m -> supportedMetrics.contains(MetricIdUtils.toUpperCaseFormatted(m.getMetricId())))
            .toList();

    if (log.isDebugEnabled() && validMeasurements.size() < measurements.size()) {
      List<Measurement> filteredOut =
          measurements.stream().filter(m -> !supportedMetrics.contains(m.getMetricId())).toList();
      log.debug(
          "Filtered out unsupported measurements for paygo tags {}: {}. Keeping: {}",
          paygoTags,
          filteredOut.stream().map(Measurement::getMetricId).collect(Collectors.toList()),
          validMeasurements.stream().map(Measurement::getMetricId).collect(Collectors.toList()));
    }
    return validMeasurements;
  }

  private boolean isPaygoEligibleTag(String productTag) {
    return Variant.findByTag(productTag)
        .map(Variant::getSubscription)
        .map(SubscriptionDefinition::isPaygEligible)
        .orElse(false);
  }

  private Event create(Event from, String tag, Measurement measurement) {
    Event target = new Event();
    resolvedEventMapper.copy(target, from);
    target.setProductTag(Set.of(tag));
    target.setMeasurements(List.of(measurement));
    return target;
  }
}
