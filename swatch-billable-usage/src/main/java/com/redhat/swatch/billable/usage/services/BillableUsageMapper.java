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
package com.redhat.swatch.billable.usage.services;

import com.redhat.swatch.billable.usage.model.TallyMeasurement;
import com.redhat.swatch.billable.usage.model.TallySnapshot;
import com.redhat.swatch.billable.usage.model.TallySummary;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;

@Slf4j
@ApplicationScoped
public class BillableUsageMapper {

  private static final String HARDWARE_MEASUREMENT_TYPE_TOTAL = "TOTAL";

  /**
   * We only want to send snapshot information for PAYG product ids. To prevent duplicate data, we
   * don't want to send snapshots with the Usage or ServiceLevel of "_ANY". We only want to report
   * on hourly metrics, so the Granularity should be HOURLY.
   *
   * @param snapshot tally snapshot
   * @return eligibility status
   */
  protected boolean isSnapshotPAYGEligible(TallySnapshot snapshot) {
    String productId = snapshot.getProductId();

    boolean isApplicableProduct =
        Variant.findByTag(productId)
            .map(Variant::getSubscription)
            .map(SubscriptionDefinition::isPaygEligible)
            .orElse(false);

    boolean isHourlyGranularity =
        Objects.equals(TallySnapshot.Granularity.HOURLY, snapshot.getGranularity());

    boolean isSpecificUsage =
        !List.of(TallySnapshot.Usage.ANY, TallySnapshot.Usage.__EMPTY__)
            .contains(snapshot.getUsage());

    boolean isSpecificServiceLevel =
        !List.of(TallySnapshot.Sla.ANY, TallySnapshot.Sla.__EMPTY__).contains(snapshot.getSla());

    boolean isSpecificBillingProvider =
        !List.of(TallySnapshot.BillingProvider.ANY, TallySnapshot.BillingProvider.__EMPTY__)
            .contains(snapshot.getBillingProvider());

    boolean isSpecificBillingAccountId = !Objects.equals(snapshot.getBillingAccountId(), "_ANY");

    boolean isSnapshotPAYGEligible =
        isHourlyGranularity
            && isApplicableProduct
            && isSpecificUsage
            && isSpecificServiceLevel
            && isSpecificBillingProvider
            && isSpecificBillingAccountId;

    if (!isSnapshotPAYGEligible) {
      log.debug("Snapshot not billable {}", snapshot);
    }
    return isSnapshotPAYGEligible;
  }

  public Stream<BillableUsage> fromTallySummary(TallySummary summary) {
    return summary.getTallySnapshots().stream()
        .filter(this::isSnapshotPAYGEligible)
        .filter(this::hasMeasurements)
        .flatMap(
            snapshot ->
                snapshot.getTallyMeasurements().stream()
                    // Filter out any HardwareMeasurementType.TOTAL measurements to prevent
                    // duplicates
                    .filter(this::isNotHardwareMeasurementTypeTotal)
                    .map(m -> toBillableUsage(m, summary, snapshot)));
  }

  private BillableUsage toBillableUsage(
      TallyMeasurement measurement, TallySummary summary, TallySnapshot snapshot) {
    return new BillableUsage()
        .withOrgId(summary.getOrgId())
        .withTallyId(snapshot.getId())
        .withSnapshotDate(snapshot.getSnapshotDate())
        .withProductId(snapshot.getProductId())
        .withSla(BillableUsage.Sla.fromValue(snapshot.getSla().value()))
        .withUsage(BillableUsage.Usage.fromValue(snapshot.getUsage().value()))
        .withBillingProvider(
            BillableUsage.BillingProvider.fromValue(snapshot.getBillingProvider().value()))
        .withBillingAccountId(snapshot.getBillingAccountId())
        .withMetricId(measurement.getMetricId())
        .withValue(measurement.getValue())
        .withHardwareMeasurementType(measurement.getHardwareMeasurementType())
        .withCurrentTotal(measurement.getCurrentTotal());
  }

  private boolean isNotHardwareMeasurementTypeTotal(TallyMeasurement measurement) {
    return !HARDWARE_MEASUREMENT_TYPE_TOTAL.equals(measurement.getHardwareMeasurementType());
  }

  private boolean hasMeasurements(TallySnapshot tallySnapshot) {
    return tallySnapshot.getTallyMeasurements() != null
        && !tallySnapshot.getTallyMeasurements().isEmpty();
  }
}
