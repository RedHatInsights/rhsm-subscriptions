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
package org.candlepin.subscriptions.tally.billing;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.registry.Variant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.json.BillableUsage;
import org.candlepin.subscriptions.json.BillableUsage.Uom;
import org.candlepin.subscriptions.json.TallySnapshot;
import org.candlepin.subscriptions.json.TallySnapshot.BillingProvider;
import org.candlepin.subscriptions.json.TallySnapshot.Granularity;
import org.candlepin.subscriptions.json.TallySnapshot.Sla;
import org.candlepin.subscriptions.json.TallySnapshot.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BillableUsageMapper {

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

    boolean isHourlyGranularity = Objects.equals(Granularity.HOURLY, snapshot.getGranularity());

    boolean isSpecificUsage = !List.of(Usage.ANY, Usage.__EMPTY__).contains(snapshot.getUsage());

    boolean isSpecificServiceLevel = !List.of(Sla.ANY, Sla.__EMPTY__).contains(snapshot.getSla());

    boolean isSpecificBillingProvider =
        !List.of(BillingProvider.ANY, BillingProvider.__EMPTY__)
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

  public Stream<BillableUsage> fromTallySummary(TallySummary tallySummary) {
    return tallySummary.getTallySnapshots().stream()
        .filter(this::isSnapshotPAYGEligible)
        .filter(this::hasMeasurements)
        .flatMap(
            snapshot ->
                snapshot.getTallyMeasurements().stream()
                    // Filter out any HardwareMeasurementType.TOTAL measurements to prevent
                    // duplicates
                    .filter(
                        measurement ->
                            !Objects.equals(
                                HardwareMeasurementType.TOTAL.toString(),
                                measurement.getHardwareMeasurementType()))
                    .map(
                        measurement ->
                            new BillableUsage()
                                .withAccountNumber(tallySummary.getAccountNumber())
                                .withOrgId(tallySummary.getOrgId())
                                .withId(snapshot.getId())
                                .withSnapshotDate(snapshot.getSnapshotDate())
                                .withProductId(snapshot.getProductId())
                                .withSla(BillableUsage.Sla.fromValue(snapshot.getSla().value()))
                                .withUsage(
                                    BillableUsage.Usage.fromValue(snapshot.getUsage().value()))
                                .withBillingProvider(
                                    BillableUsage.BillingProvider.fromValue(
                                        snapshot.getBillingProvider().value()))
                                .withBillingAccountId(snapshot.getBillingAccountId())
                                .withUom(Uom.fromValue(measurement.getUom().value()))
                                .withValue(measurement.getValue())));
  }

  private boolean hasMeasurements(TallySnapshot tallySnapshot) {
    return tallySnapshot.getTallyMeasurements() != null
        && !tallySnapshot.getTallyMeasurements().isEmpty();
  }
}
