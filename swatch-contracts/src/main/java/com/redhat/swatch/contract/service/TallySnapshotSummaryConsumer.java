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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.config.Channels.TALLY_IN;

import com.redhat.swatch.contract.model.Measurement;
import com.redhat.swatch.contract.model.TallyMeasurement;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.model.UtilizationSummary;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consumer for tally snapshot summaries that processes them in batches and enriches them with
 * capacity data.
 */
@Slf4j
@ApplicationScoped
public class TallySnapshotSummaryConsumer {

  private final SubscriptionCapacityService capacityService;
  private final UtilizationSummaryProducer utilizationProducer;

  @Inject
  public TallySnapshotSummaryConsumer(
      SubscriptionCapacityService capacityService, UtilizationSummaryProducer utilizationProducer) {
    this.capacityService = capacityService;
    this.utilizationProducer = utilizationProducer;
  }

  @Blocking
  @Incoming(TALLY_IN)
  public Uni<Void> process(List<TallySummary> tallySummaries) {
    log.info("Processing batch of {} tally messages", tallySummaries.size());

    // Get capacity data for all tally messages in batch
    var capacities = capacityService.getCapacityForTallySummaries(tallySummaries);
    log.debug("Retrieved {} capacity records for batch", capacities.size());

    // Calculate the utilization summary messages
    var utilizationSummaries = createUtilizationSummaries(tallySummaries, capacities);

    // And send
    return utilizationProducer.send(utilizationSummaries);
  }

  private List<UtilizationSummary> createUtilizationSummaries(
      List<TallySummary> tallySummaries,
      Map<TallySnapshot, List<SubscriptionCapacityView>> capacities) {
    List<UtilizationSummary> utilizationSummaries = new ArrayList<>();
    for (TallySummary tallySummary : tallySummaries) {
      for (TallySnapshot snapshot : tallySummary.getTallySnapshots()) {
        utilizationSummaries.add(createUtilizationSummary(tallySummary, snapshot, capacities));
      }
    }

    return utilizationSummaries;
  }

  /**
   * Creates a UtilizationSummary from a TallySummary and TallySnapshot, enriching measurements with
   * capacity data from matching subscriptions.
   *
   * @param tallyMessage the tally summary containing org info
   * @param snapshot the tally snapshot containing product and measurement data
   * @param capacities all capacity data retrieved for the batch
   * @return enriched utilization summary
   */
  private UtilizationSummary createUtilizationSummary(
      TallySummary tallyMessage,
      TallySnapshot snapshot,
      Map<TallySnapshot, List<SubscriptionCapacityView>> capacities) {

    // Find matching capacity data for this specific snapshot
    List<SubscriptionCapacityView> matchingCapacities =
        capacities.getOrDefault(snapshot, List.of());

    // Create enriched measurements
    List<Measurement> enrichedMeasurements = new ArrayList<>();
    boolean subscriptionFound = !matchingCapacities.isEmpty();

    if (snapshot.getTallyMeasurements() != null) {
      for (TallyMeasurement measurement : snapshot.getTallyMeasurements()) {
        Measurement enrichedMeasurement = enrichMeasurement(measurement, matchingCapacities);
        enrichedMeasurements.add(enrichedMeasurement);
      }
    }

    return new UtilizationSummary()
        .withOrgId(tallyMessage.getOrgId())
        .withTallySnapshotUuid(snapshot.getId())
        .withBillingProvider(
            snapshot.getBillingProvider() == null
                ? null
                : UtilizationSummary.BillingProvider.fromValue(
                    snapshot.getBillingProvider().value()))
        .withBillingAccountId(snapshot.getBillingAccountId())
        .withSnapshotDate(snapshot.getSnapshotDate())
        .withProductId(snapshot.getProductId())
        .withSla(
            snapshot.getSla() == null
                ? null
                : UtilizationSummary.Sla.fromValue(snapshot.getSla().toString()))
        .withUsage(
            snapshot.getUsage() == null
                ? null
                : UtilizationSummary.Usage.fromValue(snapshot.getUsage().toString()))
        .withGranularity(
            snapshot.getGranularity() == null
                ? null
                : UtilizationSummary.Granularity.fromValue(snapshot.getGranularity().toString()))
        .withMeasurements(enrichedMeasurements)
        .withSubscriptionFound(subscriptionFound);
  }

  /**
   * Enriches a tally measurement with capacity data from matching subscriptions. Sums capacity
   * values and determines if any subscription has unlimited usage.
   *
   * @param measurement the tally measurement to enrich
   * @param matchingCapacities capacity data that matches the snapshot criteria
   * @return enriched measurement with capacity and unlimited usage info
   */
  private Measurement enrichMeasurement(
      TallyMeasurement measurement, List<SubscriptionCapacityView> matchingCapacities) {

    // Find total capacity for this metric across all matching subscriptions
    double totalCapacity = 0.0;
    boolean hasUnlimitedUsage = false;

    for (SubscriptionCapacityView capacity : matchingCapacities) {
      // Check if this subscription has unlimited usage
      if (Boolean.TRUE.equals(capacity.getHasUnlimitedUsage())) {
        hasUnlimitedUsage = true;
      }

      // Sum capacity for matching metrics
      if (capacity.getMetrics() != null) {
        for (SubscriptionCapacityViewMetric metric : capacity.getMetrics()) {
          if (Objects.equals(metric.getMetricId(), measurement.getMetricId())) {
            totalCapacity += metric.getCapacity();
          }
        }
      }
    }

    return new Measurement()
        .withHardwareMeasurementType(measurement.getHardwareMeasurementType())
        .withMetricId(measurement.getMetricId())
        .withValue(measurement.getValue())
        .withCurrentTotal(measurement.getCurrentTotal())
        .withCapacity(totalCapacity > 0 ? totalCapacity : null)
        .withUnlimited(hasUnlimitedUsage);
  }
}
