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
package api;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.TallyMeasurement;
import com.redhat.swatch.contract.test.model.TallySnapshot;
import com.redhat.swatch.contract.test.model.TallySummary;
import domain.SubscriptionEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ContractsTestHelper {

  private ContractsTestHelper() {}

  /** Creates a TallySummary with default values for the given product ID. */
  public static TallySummary givenTallySummary(String orgId, List<TallySnapshot> tallySnapshots) {
    return new TallySummary().withOrgId(orgId).withTallySnapshots(tallySnapshots);
  }

  /** Creates a TallySnapshot with full customization. */
  public static TallySnapshot givenTallySnapshot(SubscriptionEvent subscriptionEvent) {
    var subscription = subscriptionEvent.getSubscription();
    var measurements =
        subscriptionEvent.getMetricValues().entrySet().stream()
            .map(m -> givenTallyMeasurement(m.getKey(), m.getValue()))
            .toList();

    return new TallySnapshot()
        .withId(UUID.randomUUID())
        .withProductId(subscription.getProduct().getName())
        .withSnapshotDate(OffsetDateTime.now())
        .withSla(subscription.getOffering().getServiceLevel().toTallySnapshotModel())
        .withUsage(subscription.getOffering().getUsage().toTallySnapshotModel())
        .withBillingProvider(
            subscription.getBillingProvider() != null
                ? subscription.getBillingProvider().toTallySnapshotModel()
                : null)
        .withBillingAccountId(subscription.getBillingAccountId())
        .withGranularity(
            subscription.getProduct().getId().isPayg()
                ? TallySnapshot.Granularity.HOURLY
                : TallySnapshot.Granularity.DAILY)
        .withTallyMeasurements(measurements);
  }

  public static TallyMeasurement givenTallyMeasurement(MetricId metricId, double value) {
    var tallyMeasurement = new TallyMeasurement();
    tallyMeasurement.setMetricId(metricId.getValue());
    tallyMeasurement.setValue(value);
    return tallyMeasurement;
  }
}
