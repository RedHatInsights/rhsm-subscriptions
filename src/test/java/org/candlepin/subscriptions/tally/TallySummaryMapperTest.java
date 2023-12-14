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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.junit.jupiter.api.Test;

class TallySummaryMapperTest {

  @Test
  void testMapSnapshots() {
    String org = "O1";
    TallySnapshot rosa =
        buildSnapshot(
            org,
            "ROSA",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.AWS,
            "Storage-gibibytes",
            2);
    TallySnapshot rhel =
        buildSnapshot(
            org,
            "RHEL",
            Granularity.HOURLY,
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            MetricIdUtils.getSockets().getValue(),
            24);

    var snapshots = List.of(rosa, rhel);

    TallySummaryMapper mapper = new TallySummaryMapper();
    TallySummary summary = mapper.mapSnapshots(org, snapshots);
    assertEquals(org, summary.getOrgId());
    List<org.candlepin.subscriptions.json.TallySnapshot> summarySnaps = summary.getTallySnapshots();
    assertEquals(snapshots.size(), summarySnaps.size());

    var mappedRosaOptional = findSnapshot(summary, "ROSA");
    assertTrue(mappedRosaOptional.isPresent());
    assertMappedSnapshot(rosa, mappedRosaOptional.get());

    var mappedRhelOptional = findSnapshot(summary, "RHEL");
    assertTrue(mappedRhelOptional.isPresent());
    assertMappedSnapshot(rhel, mappedRhelOptional.get());
  }

  void assertMappedSnapshot(
      TallySnapshot expected, org.candlepin.subscriptions.json.TallySnapshot mapped) {
    assertEquals(expected.getId(), mapped.getId());
    assertEquals(expected.getBillingAccountId(), mapped.getBillingAccountId());
    assertEquals(expected.getBillingProvider().getValue(), mapped.getBillingProvider().value());
    assertEquals(expected.getGranularity().getValue(), mapped.getGranularity().value());
    assertEquals(expected.getProductId(), mapped.getProductId());
    assertEquals(expected.getSnapshotDate(), mapped.getSnapshotDate());
    assertEquals(expected.getServiceLevel().getValue(), mapped.getSla().value());
    assertEquals(expected.getUsage().getValue(), mapped.getUsage().value());

    var expectedMeasurements = expected.getTallyMeasurements();
    var mappedMeasurements = mapped.getTallyMeasurements();
    assertEquals(expectedMeasurements.size(), mappedMeasurements.size());

    mappedMeasurements.forEach(
        m -> {
          HardwareMeasurementType type =
              HardwareMeasurementType.valueOf(m.getHardwareMeasurementType());
          TallyMeasurementKey key = new TallyMeasurementKey(type, m.getUom());
          assertTrue(expectedMeasurements.containsKey(key));
          Double expectedValue = expectedMeasurements.get(key);
          assertEquals(expectedValue, m.getValue());
        });
  }

  TallySnapshot buildSnapshot(
      String orgId,
      String productId,
      Granularity granularity,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String metricId,
      double val) {
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.PHYSICAL, metricId), val);
    measurements.put(new TallyMeasurementKey(HardwareMeasurementType.TOTAL, metricId), val);

    return TallySnapshot.builder()
        .orgId(orgId)
        .productId(productId)
        .snapshotDate(OffsetDateTime.now())
        .tallyMeasurements(measurements)
        .granularity(granularity)
        .serviceLevel(sla)
        .usage(usage)
        .billingProvider(billingProvider)
        .build();
  }

  Optional<org.candlepin.subscriptions.json.TallySnapshot> findSnapshot(
      TallySummary summary, String product) {
    return summary.getTallySnapshots().stream()
        .filter(s -> product.equalsIgnoreCase(s.getProductId()))
        .findFirst();
  }
}
