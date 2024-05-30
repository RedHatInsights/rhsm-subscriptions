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

import static com.redhat.swatch.billable.usage.configuration.Channels.BILLABLE_USAGE_OUT;
import static com.redhat.swatch.billable.usage.configuration.Channels.TALLY_SUMMARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.kafka.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.billable.usage.model.TallyMeasurement;
import com.redhat.swatch.billable.usage.model.TallySnapshot;
import com.redhat.swatch.billable.usage.model.TallySummary;
import com.redhat.swatch.clients.contracts.api.model.Contract;
import com.redhat.swatch.clients.contracts.api.model.Metric;
import com.redhat.swatch.clients.contracts.api.resources.ApiException;
import com.redhat.swatch.clients.contracts.api.resources.DefaultApi;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class TallySummaryMessageConsumerTest {
  private static final String ORG_ID = "123";
  private static final String CORES = MetricIdUtils.getCores().getValue();
  private static final TallySnapshot.BillingProvider BILLING_PROVIDER =
      TallySnapshot.BillingProvider.AWS;
  private static final TallySnapshot.Usage USAGE = TallySnapshot.Usage.PRODUCTION;
  private static final TallySnapshot.Sla SERVICE_LEVEL = TallySnapshot.Sla.PREMIUM;
  private static final String HARDWARE_MEASUREMENT_TYPE = "AWS";
  private static final String PRODUCT_ID = "rosa";
  private static final String BILLING_ACCOUNT_ID = "456";

  @InjectMock @RestClient DefaultApi contractsApi;
  @InjectSpy BillingProducer billingProducer;
  @InjectSpy BillableUsageRemittanceRepository usageRemittanceRepository;
  @Inject @Any InMemoryConnector connector;

  private InMemorySource<TallySummary> source;
  private InMemorySink<BillableUsage> target;
  OffsetDateTime snapshotDate;
  List<TallySnapshot> snapshots = new ArrayList<>();

  @Transactional
  @BeforeEach
  void setup() {
    source = connector.source(TALLY_SUMMARY);
    target = connector.sink(BILLABLE_USAGE_OUT);
    snapshotDate = OffsetDateTime.now();
    usageRemittanceRepository.deleteAll();
    snapshots.clear();
    target.clear();
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

  @Test
  void testRemittanceFailsToBeSent() {
    // the billing factor for the Cores metric is 0.25, so the effective value is 32 (8/0.25)
    givenValidContractWithMetric(8);
    givenSnapshotWithUsages(80);
    givenExceptionWhenProduceUsage();

    whenSendSnapshots();

    // 48 because the contract limit was 32, so 80 - 32 = 48.
    thenRemittanceIsCreatedWithPendingValue(48.0);
    thenRemittanceIsNotEmitted();

    // reset repository
    reset(usageRemittanceRepository);

    // the message will be retried automatically by the `@RetryWithExponentialBackoff` annotation
    givenNoExceptionWhenProduceUsage();
    thenRemittanceIsEmitted();
    // verify that the repository was not used after retrying the same message
    verify(usageRemittanceRepository, times(0)).getRemittanceSummaries(any());
  }

  private void givenExceptionWhenProduceUsage() {
    Mockito.doThrow(new RuntimeException("Test exception!")).when(billingProducer).produce(any());
  }

  private void givenNoExceptionWhenProduceUsage() {
    Mockito.reset(billingProducer);
  }

  private void givenSnapshotWithUsages(double... usages) {
    TallySnapshot snapshot = new TallySnapshot();
    List<TallyMeasurement> measurements = new ArrayList<>();
    for (Double usage : usages) {
      measurements.add(
          new TallyMeasurement()
              .withHardwareMeasurementType(HARDWARE_MEASUREMENT_TYPE)
              .withMetricId(CORES)
              .withValue(usage)
              .withCurrentTotal(usage));
    }
    snapshot.setId(UUID.randomUUID());
    snapshot.setTallyMeasurements(measurements);
    snapshot.setSnapshotDate(snapshotDate);
    snapshot.setBillingAccountId(BILLING_ACCOUNT_ID);
    snapshot.setBillingProvider(TallySnapshot.BillingProvider.AWS);
    snapshot.setGranularity(TallySnapshot.Granularity.HOURLY);
    snapshot.setProductId(PRODUCT_ID);
    snapshot.setUsage(USAGE);
    snapshot.setSla(SERVICE_LEVEL);

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
              eq(BILLING_PROVIDER.value()),
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
    source.send(new TallySummary().withOrgId(ORG_ID).withTallySnapshots(snapshots));
  }

  private void thenRemittanceIsCreatedWithPendingValue(double expected) {
    Awaitility.await().untilAsserted(() -> verifyRemittanceIsCreatedWithPendingValue(expected));
  }

  @Transactional
  public void verifyRemittanceIsCreatedWithPendingValue(double expected) {
    var remittances = usageRemittanceRepository.listAll();
    assertEquals(1, remittances.size());
    assertEquals(expected, remittances.get(0).getRemittedPendingValue());
  }

  private void thenRemittanceIsEmitted() {
    Awaitility.await().untilAsserted(() -> assertEquals(1, target.received().size()));
  }

  private void thenRemittanceIsNotEmitted() {
    assertEquals(0, target.received().size());
  }
}
