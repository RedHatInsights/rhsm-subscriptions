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
package com.redhat.swatch.contract.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.model.TallyMeasurement;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.model.UtilizationSummary;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
class TallySnapshotSummaryConsumerTest {

  private static final String ORG_ID = "org123";

  @Inject @Any InMemoryConnector connector;
  @InjectMock SubscriptionCapacityService capacityService;
  @InjectMock UtilizationSummaryProducer utilizationProducer;

  private InMemorySource<List<TallySummary>> tallyInChannel;

  @BeforeEach
  public void setup() {
    tallyInChannel = connector.source(Channels.TALLY_IN);
    when(utilizationProducer.send(anyList())).thenReturn(Uni.createFrom().voidItem());
  }

  @Test
  void testWhenCapacityFoundThenSubscriptionFoundIsTrue() {
    // Given tally summary
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given capacity service returns matching capacity
    givenCapacityServiceReturns(
        tallySummary.getTallySnapshots().get(0),
        List.of(
            givenCapacityView(
                "sub123",
                ORG_ID,
                "RHEL",
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                BillingProvider.RED_HAT,
                null,
                100.0,
                false)));

    whenReceiveTallySummary(tallySummary);

    // Then should create utilization summary with subscription found
    thenUtilizationSummariesSent(1);
    thenUtilizationSummaryHasSubscriptionFound(true);
    thenUtilizationSummaryHasProductId("RHEL");
  }

  @Test
  void testWhenNoCapacityFoundThenSubscriptionFoundIsFalse() {
    // Given tally summary
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.AWS,
            "aws-123");

    // Given capacity service returns no matching capacity
    givenCapacityServiceReturnsEmpty();

    whenReceiveTallySummary(tallySummary);

    // Then should create utilization summary with subscription not found
    thenUtilizationSummariesSent(1);
    thenUtilizationSummaryHasSubscriptionFound(false);
    thenUtilizationSummaryHasProductId("RHEL");
  }

  @Test
  void testWhenMultipleCapacitiesThenAggregatesCorrectly() {
    // Given tally summary
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given multiple capacity views for the same snapshot
    SubscriptionCapacityView capacity1 =
        givenCapacityView(
            "sub123",
            ORG_ID,
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            50.0,
            false);
    SubscriptionCapacityView capacity2 =
        givenCapacityView(
            "sub456",
            ORG_ID,
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            75.0,
            false);

    givenCapacityServiceReturns(
        tallySummary.getTallySnapshots().get(0), List.of(capacity1, capacity2));

    whenReceiveTallySummary(tallySummary);

    // Then should create one utilization summary that aggregates both capacities
    thenUtilizationSummariesSent(1);
    thenUtilizationSummaryHasSubscriptionFound(true);
  }

  @Test
  void testWhenUnlimitedCapacityThenHandlesCorrectly() {
    // Given tally summary with measurements
    TallySummary tallySummary =
        givenTallySummaryWithMeasurements(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given capacity with unlimited usage
    SubscriptionCapacityView unlimitedCapacity =
        givenCapacityView(
            "sub123",
            ORG_ID,
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            0.0,
            true);

    givenCapacityServiceReturns(
        tallySummary.getTallySnapshots().get(0), List.of(unlimitedCapacity));

    whenReceiveTallySummary(tallySummary);

    // Then should create utilization summary with unlimited capacity
    thenUtilizationSummariesSent(1);
    thenUtilizationSummaryHasSubscriptionFound(true);
  }

  @Test
  void testMultipleSnapshotsWithDifferentProducts() {
    // Given tally summary with multiple snapshots for different products
    TallySnapshot rhelSnapshot = givenTallySnapshot("RHEL", TallySnapshot.Sla.PREMIUM);
    TallySnapshot openshiftSnapshot = givenTallySnapshot("OpenShift", TallySnapshot.Sla.STANDARD);

    TallySummary tallySummary =
        new TallySummary()
            .withOrgId(ORG_ID)
            .withTallySnapshots(List.of(rhelSnapshot, openshiftSnapshot));

    // Given capacity for both products
    SubscriptionCapacityView rhelCapacity =
        givenCapacityView(
            "sub123",
            ORG_ID,
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            100.0,
            false);
    SubscriptionCapacityView openshiftCapacity =
        givenCapacityView(
            "sub456",
            ORG_ID,
            "OpenShift",
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            200.0,
            false);

    givenCapacityServiceReturns(
        Map.of(
            rhelSnapshot, List.of(rhelCapacity),
            openshiftSnapshot, List.of(openshiftCapacity)));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should create utilization summary for each snapshot
    thenUtilizationSummariesSent(2);
  }

  private TallySummary givenTallySummaryMessage() {
    TallySnapshot snapshot =
        new TallySnapshot()
            .withId(UUID.randomUUID())
            .withProductId("RHEL")
            .withSnapshotDate(OffsetDateTime.now())
            .withSla(TallySnapshot.Sla.PREMIUM)
            .withUsage(TallySnapshot.Usage.PRODUCTION)
            .withBillingProvider(TallySnapshot.BillingProvider.RED_HAT)
            .withGranularity(TallySnapshot.Granularity.HOURLY);

    return new TallySummary().withOrgId(ORG_ID).withTallySnapshots(List.of(snapshot));
  }

  private void whenReceiveTallySummary(TallySummary... tallySummaries) {
    tallyInChannel.send(List.of(tallySummaries));
  }

  private TallySummary givenTallySummaryWithCriteria(
      String productId,
      TallySnapshot.Sla sla,
      TallySnapshot.Usage usage,
      TallySnapshot.BillingProvider billingProvider,
      String billingAccountId) {

    TallySnapshot snapshot =
        new TallySnapshot()
            .withId(UUID.randomUUID())
            .withProductId(productId)
            .withSnapshotDate(OffsetDateTime.now())
            .withSla(sla)
            .withUsage(usage)
            .withBillingProvider(billingProvider)
            .withBillingAccountId(billingAccountId)
            .withGranularity(TallySnapshot.Granularity.HOURLY);

    return new TallySummary().withOrgId(ORG_ID).withTallySnapshots(List.of(snapshot));
  }

  private TallySummary givenTallySummaryWithMeasurements(
      String productId,
      TallySnapshot.Sla sla,
      TallySnapshot.Usage usage,
      TallySnapshot.BillingProvider billingProvider,
      String billingAccountId) {

    TallyMeasurement measurement =
        new TallyMeasurement()
            .withMetricId("Cores")
            .withHardwareMeasurementType("PHYSICAL")
            .withValue(10.0)
            .withCurrentTotal(100.0);

    TallySnapshot snapshot =
        new TallySnapshot()
            .withId(UUID.randomUUID())
            .withProductId(productId)
            .withSnapshotDate(OffsetDateTime.now())
            .withSla(sla)
            .withUsage(usage)
            .withBillingProvider(billingProvider)
            .withBillingAccountId(billingAccountId)
            .withGranularity(TallySnapshot.Granularity.HOURLY)
            .withTallyMeasurements(List.of(measurement));

    return new TallySummary().withOrgId(ORG_ID).withTallySnapshots(List.of(snapshot));
  }

  private TallySnapshot givenTallySnapshot(String productId, TallySnapshot.Sla sla) {
    return new TallySnapshot()
        .withId(UUID.randomUUID())
        .withProductId(productId)
        .withSnapshotDate(OffsetDateTime.now())
        .withSla(sla)
        .withUsage(TallySnapshot.Usage.PRODUCTION)
        .withBillingProvider(TallySnapshot.BillingProvider.RED_HAT)
        .withGranularity(TallySnapshot.Granularity.HOURLY);
  }

  private SubscriptionCapacityView givenCapacityView(
      String subscriptionId,
      String orgId,
      String productTag,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      Double capacity,
      Boolean hasUnlimitedUsage) {

    SubscriptionCapacityView capacityView = new SubscriptionCapacityView();
    capacityView.setSubscriptionId(subscriptionId);
    capacityView.setSubscriptionNumber("SUB-" + subscriptionId);
    capacityView.setOrgId(orgId);
    capacityView.setProductTag(productTag);
    capacityView.setSku("RH00001");
    capacityView.setProductName(productTag + " Product");
    capacityView.setServiceLevel(serviceLevel);
    capacityView.setUsage(usage);
    capacityView.setBillingProvider(billingProvider);
    capacityView.setBillingAccountId(billingAccountId);
    capacityView.setQuantity(100L);
    capacityView.setHasUnlimitedUsage(hasUnlimitedUsage);
    capacityView.setStartDate(OffsetDateTime.now().minusDays(30));
    capacityView.setEndDate(OffsetDateTime.now().plusDays(365));

    // Add metrics if capacity is provided
    if (capacity != null && capacity > 0) {
      SubscriptionCapacityViewMetric metric = new SubscriptionCapacityViewMetric();
      metric.setMetricId("Cores");
      metric.setCapacity(capacity);
      metric.setMeasurementType("PHYSICAL");
      capacityView.setMetrics(Set.of(metric));
    }

    return capacityView;
  }

  private void givenCapacityServiceReturns(
      Map<TallySnapshot, List<SubscriptionCapacityView>> capacityViews) {
    when(capacityService.getCapacityForTallySummaries(any())).thenReturn(capacityViews);
  }

  private void givenCapacityServiceReturns(
      TallySnapshot snapshot, List<SubscriptionCapacityView> capacityViews) {
    when(capacityService.getCapacityForTallySummaries(any()))
        .thenReturn(Map.of(snapshot, capacityViews));
  }

  private void givenCapacityServiceReturnsEmpty() {
    when(capacityService.getCapacityForTallySummaries(any())).thenReturn(Map.of());
  }

  private void thenUtilizationSummariesSent(int expectedCount) {
    Awaitility.await()
        .untilAsserted(
            () -> {
              verify(utilizationProducer)
                  .send(
                      argThat(
                          summaries -> {
                            assertEquals(
                                expectedCount,
                                summaries.size(),
                                "Expected number of utilization summaries sent");
                            return true;
                          }));
            });
  }

  private void thenUtilizationSummaryHasSubscriptionFound(boolean expectedSubscriptionFound) {
    verify(utilizationProducer)
        .send(
            argThat(
                summaries -> {
                  assertFalse(
                      summaries.isEmpty(), "At least one utilization summary should be sent");
                  UtilizationSummary first = summaries.get(0);
                  assertNotNull(
                      first.getSubscriptionFound(),
                      "Utilization summary should have subscription found flag");
                  assertEquals(
                      expectedSubscriptionFound,
                      first.getSubscriptionFound(),
                      "Utilization summary should have correct subscription found flag");
                  return true;
                }));
  }

  private void thenUtilizationSummaryHasProductId(String expectedProductId) {
    verify(utilizationProducer)
        .send(
            argThat(
                summaries -> {
                  assertFalse(
                      summaries.isEmpty(), "At least one utilization summary should be sent");
                  UtilizationSummary first = summaries.get(0);
                  assertNotNull(first.getProductId(), "Utilization summary should have product ID");
                  assertEquals(
                      expectedProductId,
                      first.getProductId(),
                      "Utilization summary should have correct product ID");
                  return true;
                }));
  }
}
