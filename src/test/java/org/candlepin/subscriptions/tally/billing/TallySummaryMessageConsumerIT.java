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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contracts.api.model.Contract;
import com.redhat.swatch.contracts.api.model.Metric;
import com.redhat.swatch.contracts.api.resources.DefaultApi;
import com.redhat.swatch.contracts.client.ApiException;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.tally.TallySummaryMapper;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "kafka-queue", "test"})
@Tag("integration")
class TallySummaryMessageConsumerIT implements ExtendWithEmbeddedKafka {

  private static final String ORG_ID = "123";
  private static final String CORES = MetricIdUtils.getCores().getValue();
  private static final BillingProvider BILLING_PROVIDER = BillingProvider.AWS;
  private static final Usage USAGE = Usage.PRODUCTION;
  private static final ServiceLevel SERVICE_LEVEL = ServiceLevel.PREMIUM;
  private static final HardwareMeasurementType HARDWARE_MEASUREMENT_TYPE =
      HardwareMeasurementType.AWS;
  private static final String PRODUCT_ID = "rosa";
  private static final String BILLING_ACCOUNT_ID = "456";

  @MockBean DefaultApi contractsApi;
  @SpyBean TallySummaryMessageConsumer consumer;
  @SpyBean BillingProducer billingProducer;
  @Autowired KafkaTemplate<String, TallySummary> kafkaTemplate;

  @Autowired
  @Qualifier("billingProducerTallySummaryTopicProperties")
  TaskQueueProperties taskQueueProperties;

  @SpyBean BillableUsageRemittanceRepository usageRemittanceRepository;
  @Autowired TallySnapshotRepository snapshotRepository;
  @Autowired TallySummaryMapper tallySummaryMapper;

  OffsetDateTime snapshotDate;
  List<TallySnapshot> snapshots = new ArrayList<>();

  @Transactional
  @BeforeEach
  void setup() {
    snapshotDate = OffsetDateTime.now();
    usageRemittanceRepository.deleteAll();
    snapshotRepository.deleteAll();
    snapshots.clear();
  }

  @Test
  void testRemittanceIsStored() {
    // the billing factor for the Cores metric is 0.25, so the effective value is 32 (8/0.25)
    givenValidContractWithMetric(8);
    givenSnapshotWithUsages(80);

    whenSendSnapshots();

    // 48 because the contract limit was 32, so 80 - 32 = 48.
    thenRemittanceIsCreatedWithPendingValue(48.0);
    thenRemittanceIsEmitted();
  }

  private void givenSnapshotWithUsages(double... usages) {
    TallySnapshot snapshot = new TallySnapshot();
    Map<TallyMeasurementKey, Double> measurements = new HashMap<>();
    for (Double usage : usages) {
      measurements.put(new TallyMeasurementKey(HARDWARE_MEASUREMENT_TYPE, CORES), usage);
    }
    snapshot.setTallyMeasurements(measurements);
    snapshot.setSnapshotDate(snapshotDate);
    snapshot.setOrgId(ORG_ID);
    snapshot.setBillingAccountId(BILLING_ACCOUNT_ID);
    snapshot.setBillingProvider(BillingProvider.AWS);
    snapshot.setGranularity(Granularity.HOURLY);
    snapshot.setProductId(PRODUCT_ID);
    snapshot.setUsage(USAGE);
    snapshot.setServiceLevel(SERVICE_LEVEL);
    snapshotRepository.save(snapshot);

    snapshots.add(snapshot);
  }

  /**
   * a valid contract is a contract within the snapshot date using a supported metric by product ID.
   */
  private void givenValidContractWithMetric(int value) {
    try {
      String metricIdForContract =
          switch (BILLING_PROVIDER) {
            case AWS -> SubscriptionDefinition.getAwsDimension(PRODUCT_ID, CORES);
            case RED_HAT -> SubscriptionDefinition.getRhmMetricId(PRODUCT_ID, CORES);
            case AZURE -> SubscriptionDefinition.getAzureDimension(PRODUCT_ID, CORES);
            default -> fail("Unsupported provider for tests!");
          };

      when(contractsApi.getContract(
              eq(ORG_ID),
              eq(PRODUCT_ID),
              any(),
              eq(BILLING_PROVIDER.getValue()),
              eq(BILLING_ACCOUNT_ID),
              any()))
          .thenReturn(
              List.of(
                  new Contract()
                      .startDate(snapshotDate.minusDays(10))
                      .endDate(snapshotDate.plusDays(10))
                      .addMetricsItem(new Metric().metricId(metricIdForContract).value(value))));
    } catch (ApiException e) {
      fail(e);
    }
  }

  private void whenSendSnapshots() {
    kafkaTemplate.send(
        taskQueueProperties.getTopic(), tallySummaryMapper.mapSnapshots(ORG_ID, snapshots));
  }

  private void thenRemittanceIsCreatedWithPendingValue(double expected) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              var remittances = usageRemittanceRepository.findAll();
              assertEquals(1, remittances.size());
              assertEquals(expected, remittances.get(0).getRemittedPendingValue());
            });
  }

  private void thenRemittanceIsEmitted() {
    verify(billingProducer).produce(any());
  }
}
