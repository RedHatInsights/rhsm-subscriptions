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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class TallySnapshotControllerMetricsTest {
  @MockBean MaxSeenSnapshotStrategy maxSeenStrat;
  @MockBean CombiningRollupSnapshotStrategy combiningStrat;
  @MockBean InventoryAccountUsageCollector accountUsageCollector;
  @MockBean EventController eventController;
  @MockBean MetricUsageCollector usageCollector;

  @Autowired TallySnapshotController controller;
  @Autowired MeterRegistry registry;

  @Test
  void produceSnapshotsForOrg() {
    var snapList = createSnaps(10, Granularity.DAILY, "RHEL for x86");
    when(maxSeenStrat.produceSnapshotsFromCalculations(any(AccountUsageCalculation.class)))
        .thenReturn(snapList);
    when(accountUsageCollector.tally("123")).thenReturn(new AccountUsageCalculation("123"));

    controller.produceSnapshotsForOrg("123");

    var counter =
        Counter.builder("swatch_tally_tallied_usage_total")
            .tags(
                "product",
                "RHEL for x86",
                "billing_provider_id",
                BillingProvider.RED_HAT.getValue())
            .withRegistry(registry);

    for (var s : Set.of("CORES", "SOCKETS")) {
      var c = counter.withTag("metric_id", s);
      assertEquals(10.0, c.count());
    }
  }

  @Test
  void produceHourlySnapshotsForOrg() {
    var snapList = createSnaps(10, Granularity.HOURLY, "rosa");

    doAnswer(
            AdditionalAnswers.answerVoid(
                (orgId, serviceType, eventRecordDate, batchSize, batchConsumer) -> {
                  var e1 = new Event();
                  e1.setTimestamp(OffsetDateTime.now());
                  e1.setRecordDate(OffsetDateTime.now());
                  var e2 = new Event();
                  e2.setTimestamp(OffsetDateTime.now());
                  e2.setRecordDate(OffsetDateTime.now());
                  ((Consumer) batchConsumer).accept(List.of(e1, e2));
                }))
        .when(eventController)
        .processEventsInBatches(eq("123"), any(), any(), anyInt(), any());

    // Perform these operations just to get calcCache.isEmpty() to evaluate to false
    doAnswer(
            AdditionalAnswers.answerVoid(
                (batch, cache) -> {
                  var calc = new AccountUsageCalculation("123");
                  var event = new Event();
                  event.setTimestamp(OffsetDateTime.now());
                  ((AccountUsageCalculationCache) cache).put(event, calc);
                }))
        .when(usageCollector)
        .calculateUsage(anyList(), any(AccountUsageCalculationCache.class));

    when(combiningStrat.produceSnapshotsFromCalculations(
            eq("123"), any(), eq(Set.of("rosa")), any(), eq(Granularity.HOURLY), any()))
        .thenReturn(Map.of("123", snapList));

    controller.produceHourlySnapshotsForOrg("123");

    var counter =
        Counter.builder("swatch_tally_tallied_usage_total")
            .tags("product", "rosa", "billing_provider_id", BillingProvider.RED_HAT.getValue())
            .withRegistry(registry);

    for (var s : Set.of("CORES", "SOCKETS")) {
      var c = counter.withTag("metric_id", s);
      assertEquals(10.0, c.count());
    }
  }

  private ArrayList<TallySnapshot> createSnaps(
      int numSnaps, Granularity granularity, String productId) {
    var snapList = new ArrayList<TallySnapshot>(numSnaps);
    for (int i = 0; i < numSnaps; i++) {
      var measurements = new HashMap<TallyMeasurementKey, Double>();

      for (int j = 0; j < 4; j++) {
        var measurement = generateMeasurements(j);
        measurements.put(measurement, 1.0);
      }

      Set<Usage> usages = Set.of(Usage.PRODUCTION, Usage._ANY);
      Set<ServiceLevel> slas = Set.of(ServiceLevel.PREMIUM, ServiceLevel._ANY);
      Set<BillingProvider> providers = Set.of(BillingProvider.RED_HAT);

      // Emulate the creation of all the superset tally buckets.  We want to ensure that the less
      // specific buckets are
      // filtered out
      Set<List<Object>> tuples = Sets.cartesianProduct(usages, slas, providers);
      tuples.forEach(
          t -> {
            Usage u = (Usage) t.get(0);
            ServiceLevel s = (ServiceLevel) t.get(1);
            BillingProvider bp = (BillingProvider) t.get(2);
            var snap = new TallySnapshot();
            snap.setProductId(productId);
            snap.setGranularity(granularity);
            snap.setServiceLevel(s);
            snap.setUsage(u);
            snap.setBillingProvider(bp);
            snap.setTallyMeasurements(measurements);
            snapList.add(snap);
          });
    }
    return snapList;
  }

  private @NotNull TallyMeasurementKey generateMeasurements(int j) {
    var measurement = new TallyMeasurementKey();
    switch (j) {
      case 0 -> {
        measurement.setMetricId("CORES");
        measurement.setMeasurementType(HardwareMeasurementType.PHYSICAL);
      }
      case 1 -> {
        measurement.setMetricId("CORES");
        measurement.setMeasurementType(HardwareMeasurementType.TOTAL);
      }
      case 2 -> {
        measurement.setMetricId("SOCKETS");
        measurement.setMeasurementType(HardwareMeasurementType.PHYSICAL);
      }
      case 3 -> {
        measurement.setMetricId("SOCKETS");
        measurement.setMeasurementType(HardwareMeasurementType.TOTAL);
      }
      default -> throw new IllegalStateException("MeasurementKey creation failed");
    }
    return measurement;
  }
}
