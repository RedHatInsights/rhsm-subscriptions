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
package utils;

import static utils.TallyMeasurementBuilder.buildPhysicalMeasurement;

import com.redhat.swatch.component.tests.utils.RandomUtils;
import domain.BillingProvider;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.TallyMeasurement;
import org.candlepin.subscriptions.billable.usage.TallySnapshot;
import org.candlepin.subscriptions.billable.usage.TallySummary;

public final class TallySummaryBuilder {

  private TallySummaryBuilder() {}

  public static TallySummary buildTallySummary(
      String orgId,
      String productId,
      String metricId,
      double value,
      BillingProvider billingProvider,
      String billingAccountId) {
    return buildTallySummary(
        orgId,
        productId,
        metricId,
        value,
        billingProvider,
        billingAccountId,
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC));
  }

  public static TallySummary buildTallySummary(
      String orgId,
      String productId,
      String metricId,
      double value,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime snapshotDate) {
    return buildTallySummary(
        orgId,
        productId,
        billingProvider,
        billingAccountId,
        snapshotDate,
        TallyMeasurementBuilder.buildPhysicalMeasurement(metricId, value));
  }

  public static TallySummary buildTallySummary(
      String orgId,
      String productId,
      BillingProvider billingProvider,
      String billingAccountId,
      TallyMeasurement... measurements) {
    return buildTallySummary(
        orgId,
        productId,
        billingProvider,
        billingAccountId,
        OffsetDateTime.now().minusHours(1).withOffsetSameInstant(ZoneOffset.UTC),
        measurements);
  }

  public static TallySummary buildTallySummary(
      String orgId,
      String productId,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime snapshotDate,
      TallyMeasurement... measurements) {
    var snapshot = new TallySnapshot();
    snapshot.setId(UUID.randomUUID());
    snapshot.setProductId(productId);
    snapshot.setBillingProvider(billingProvider.toTallyApiModel());
    snapshot.setBillingAccountId(billingAccountId);
    snapshot.setSnapshotDate(snapshotDate);
    snapshot.setSla(TallySnapshot.Sla.PREMIUM);
    snapshot.setUsage(TallySnapshot.Usage.PRODUCTION);
    snapshot.setGranularity(TallySnapshot.Granularity.HOURLY);
    snapshot.setTallyMeasurements(List.of(measurements));

    var tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);
    tallySummary.setTallySnapshots(List.of(snapshot));
    return tallySummary;
  }

  public static TallySummary buildTallySummaryWithDefaults(
      String orgId, String productId, String metricId, double value) {
    return buildTallySummary(
        orgId, productId, metricId, value, BillingProvider.AWS, RandomUtils.generateRandom());
  }

  public static TallySummary buildTallySummaryWithCurrentTotal(
      String orgId,
      String productId,
      String metricId,
      double value,
      double currentTotal,
      BillingProvider billingProvider,
      String billingAccountId) {
    return buildTallySummary(
        orgId,
        productId,
        billingProvider,
        billingAccountId,
        buildPhysicalMeasurement(metricId, value, currentTotal));
  }

  public static TallySummary buildTallySummaryWithCurrentTotal(
      String orgId,
      String productId,
      String metricId,
      double value,
      double currentTotal,
      BillingProvider billingProvider,
      String billingAccountId,
      OffsetDateTime snapshotDate) {
    return buildTallySummary(
        orgId,
        productId,
        billingProvider,
        billingAccountId,
        snapshotDate,
        buildPhysicalMeasurement(metricId, value, currentTotal));
  }

  public static TallySummary buildTallySummaryWithGranularity(
      String orgId,
      String productId,
      String metricId,
      double value,
      TallySnapshot.Granularity granularity,
      UUID snapshotId) {

    TallySummary tallySummary = buildTallySummaryWithDefaults(orgId, productId, metricId, value);
    TallySnapshot snapshot = tallySummary.getTallySnapshots().getFirst();
    snapshot.setGranularity(granularity);
    snapshot.setId(snapshotId);
    return tallySummary;
  }
}
