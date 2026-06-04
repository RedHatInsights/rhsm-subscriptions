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

import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.TallyMeasurement;
import org.candlepin.subscriptions.billable.usage.TallySnapshot;
import org.candlepin.subscriptions.billable.usage.TallySummary;

public final class BillableUsageTestHelper {

  private BillableUsageTestHelper() {}

  public static TallySummary createTallySummary(
      String orgId,
      String productId,
      String metricId,
      double value,
      BillingProvider billingProvider,
      String billingAccountId) {
    return createTallySummary(
        orgId,
        productId,
        metricId,
        value,
        billingProvider,
        billingAccountId,
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC));
  }

  /**
   * Create a tally summary with a specific snapshot date. Use for time-boundary tests (e.g. last
   * month vs current month).
   */
  public static TallySummary createTallySummary(
      String orgId,
      String productId,
      String metricId,
      double value,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime snapshotDate) {

    var measurement = new TallyMeasurement();
    measurement.setHardwareMeasurementType("PHYSICAL");
    measurement.setMetricId(metricId);
    measurement.setValue(value);
    measurement.setCurrentTotal(value);

    var snapshot = new TallySnapshot();
    snapshot.setId(UUID.randomUUID());
    snapshot.setProductId(productId);
    snapshot.setBillingProvider(billingProvider.toTallyApiModel());
    snapshot.setBillingAccountId(billingAccountId);
    snapshot.setSnapshotDate(snapshotDate);
    snapshot.setSla(TallySnapshot.Sla.PREMIUM);
    snapshot.setUsage(TallySnapshot.Usage.PRODUCTION);
    snapshot.setGranularity(TallySnapshot.Granularity.HOURLY);
    snapshot.setTallyMeasurements(List.of(measurement));

    var tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  public static TallySummary createTallySummaryWithDefaults(
      String orgId, String productId, String metricId, double value) {
    return createTallySummary(
        orgId, productId, metricId, value, BillingProvider.AWS, RandomUtils.generateRandom());
  }

  public static TallySummary createTallySummaryWithGranularity(
      String orgId,
      String productId,
      String metricId,
      double value,
      TallySnapshot.Granularity granularity,
      UUID snapshotId) {

    TallySummary tallySummary = createTallySummaryWithDefaults(orgId, productId, metricId, value);
    TallySnapshot snapshot = tallySummary.getTallySnapshots().get(0);
    snapshot.setGranularity(granularity);
    snapshot.setId(snapshotId);
    return tallySummary;
  }
}
