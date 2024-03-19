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
package org.candlepin.subscriptions.tally;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.json.TallyMeasurement;
import org.candlepin.subscriptions.json.TallySummary;
import org.springframework.stereotype.Component;

@Component
public class TallySummaryMapper {

  public TallySummary mapSnapshots(String orgId, List<TallySnapshot> snapshots) {
    return createTallySummary(orgId, snapshots);
  }

  private TallySummary createTallySummary(String orgId, List<TallySnapshot> tallySnapshots) {
    var mappedSnapshots =
        tallySnapshots.stream().map(this::mapTallySnapshot).collect(Collectors.toList());
    return new TallySummary().withOrgId(orgId).withTallySnapshots(mappedSnapshots);
  }

  private org.candlepin.subscriptions.json.TallySnapshot mapTallySnapshot(
      TallySnapshot tallySnapshot) {

    var granularity =
        org.candlepin.subscriptions.json.TallySnapshot.Granularity.fromValue(
            tallySnapshot.getGranularity().getValue());

    var sla =
        org.candlepin.subscriptions.json.TallySnapshot.Sla.fromValue(
            tallySnapshot.getServiceLevel().getValue());

    var usage =
        org.candlepin.subscriptions.json.TallySnapshot.Usage.fromValue(
            tallySnapshot.getUsage().getValue());

    var billingProvider =
        org.candlepin.subscriptions.json.TallySnapshot.BillingProvider.fromValue(
            tallySnapshot.getBillingProvider().getValue());

    return new org.candlepin.subscriptions.json.TallySnapshot()
        .withGranularity(granularity)
        .withId(tallySnapshot.getId())
        .withProductId(tallySnapshot.getProductId())
        .withSnapshotDate(tallySnapshot.getSnapshotDate())
        .withSla(sla)
        .withUsage(usage)
        .withBillingProvider(billingProvider)
        .withBillingAccountId(tallySnapshot.getBillingAccountId())
        .withTallyMeasurements(mapMeasurements(tallySnapshot.getTallyMeasurements()));
  }

  private List<TallyMeasurement> mapMeasurements(
      Map<TallyMeasurementKey, Double> tallyMeasurements) {
    return tallyMeasurements.entrySet().stream()
        .map(
            entry ->
                new TallyMeasurement()
                    .withHardwareMeasurementType(entry.getKey().getMeasurementType().toString())
                    .withUom(entry.getKey().getMetricId())
                    .withMetricId(entry.getKey().getMetricId())
                    .withValue(entry.getValue()))
        .collect(Collectors.toList());
  }
}
