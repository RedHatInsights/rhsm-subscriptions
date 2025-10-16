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

import com.redhat.swatch.contract.model.TallyMeasurement;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ContractsTestHelper {

  private static final String DEFAULT_METRIC_ID = "Cores";
  private static final String DEFAULT_HARDWARE_TYPE = "PHYSICAL";
  private static final double DEFAULT_VALUE = 10.0;
  private static final double DEFAULT_CURRENT_TOTAL = 100.0;

  private ContractsTestHelper() {}

  /** Creates a TallySummary with default values for the given product ID. */
  public static TallySummary givenTallySummary(String orgId, String productId) {
    return new TallySummary()
        .withOrgId(orgId)
        .withTallySnapshots(List.of(givenTallySnapshot(productId)));
  }

  /** Creates a TallySummary with custom SLA, Usage, and BillingProvider. */
  public static TallySummary givenTallySummary(
      String orgId, String productId, TallySnapshot.Sla sla) {
    return givenTallySummary(orgId, productId, sla, null, null, null);
  }

  /** Creates a TallySummary with full customization options. */
  public static TallySummary givenTallySummary(
      String orgId,
      String productId,
      TallySnapshot.Sla sla,
      TallySnapshot.Usage usage,
      TallySnapshot.BillingProvider billingProvider,
      String billingAccountId) {

    TallySnapshot snapshot =
        givenTallySnapshot(productId, sla, usage, billingProvider, billingAccountId);
    return new TallySummary().withOrgId(orgId).withTallySnapshots(List.of(snapshot));
  }

  /** Creates a TallySnapshot with default Premium/Production/RedHat values. */
  public static TallySnapshot givenTallySnapshot(String productId) {
    return givenTallySnapshot(
        productId,
        TallySnapshot.Sla.PREMIUM,
        TallySnapshot.Usage.PRODUCTION,
        TallySnapshot.BillingProvider.RED_HAT,
        null);
  }

  /** Creates a TallySnapshot with specified SLA only (other values default). */
  public static TallySnapshot givenTallySnapshot(String productId, TallySnapshot.Sla sla) {
    return givenTallySnapshot(
        productId,
        sla,
        TallySnapshot.Usage.PRODUCTION,
        TallySnapshot.BillingProvider.RED_HAT,
        null);
  }

  /** Creates a TallySnapshot with full customization. */
  public static TallySnapshot givenTallySnapshot(
      String productId,
      TallySnapshot.Sla sla,
      TallySnapshot.Usage usage,
      TallySnapshot.BillingProvider billingProvider,
      String billingAccountId) {

    TallyMeasurement measurement = givenTallyMeasurement();

    return new TallySnapshot()
        .withId(UUID.randomUUID())
        .withProductId(productId)
        .withSnapshotDate(OffsetDateTime.now())
        .withSla(sla)
        .withUsage(usage)
        .withBillingProvider(billingProvider)
        .withBillingAccountId(billingAccountId)
        .withGranularity(TallySnapshot.Granularity.HOURLY)
        .withTallyMeasurements(List.of(measurement));
  }

  /** Creates a TallyMeasurement with default values. */
  public static TallyMeasurement givenTallyMeasurement() {
    return givenTallyMeasurement(DEFAULT_VALUE, DEFAULT_CURRENT_TOTAL);
  }

  /** Creates a TallyMeasurement with custom values. */
  public static TallyMeasurement givenTallyMeasurement(double value, double currentTotal) {
    return new TallyMeasurement()
        .withMetricId(DEFAULT_METRIC_ID)
        .withHardwareMeasurementType(DEFAULT_HARDWARE_TYPE)
        .withValue(value)
        .withCurrentTotal(currentTotal);
  }
}
