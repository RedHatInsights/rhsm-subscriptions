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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.contract.config.Channels;
import com.redhat.swatch.contract.model.TallyMeasurement;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import com.redhat.swatch.contract.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.contract.test.resources.LoggerCaptor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jboss.logmanager.LogContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
class TallySnapshotSummaryConsumerTest {

  private static final String ORG_ID = "org123";
  private static final LoggerCaptor LOGGER_CAPTOR = new LoggerCaptor();

  @Inject @Any InMemoryConnector connector;
  @InjectMock SubscriptionCapacityService capacityService;

  private InMemorySource<List<TallySummary>> tallyInChannel;

  @BeforeAll
  static void configureLogging() {
    LogContext.getLogContext()
        .getLogger(TallySnapshotSummaryConsumer.class.getName())
        .addHandler(LOGGER_CAPTOR);
  }

  @Transactional
  @BeforeEach
  public void setup() {
    tallyInChannel = connector.source(Channels.TALLY_IN);
  }

  @Test
  void testProcessTallySummary() {
    TallySummary tallySummary = givenTallySummaryMessage();

    whenReceiveTallySummary(tallySummary);

    thenLogWithMessage("Processing batch of 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithExactMatch() {
    // Given tally summary with specific criteria
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given matching capacity data
    SubscriptionCapacityView matchingCapacity =
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

    givenCapacityServiceReturns(List.of(matchingCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should find matching subscription
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithNoMatch() {
    // Given tally summary with specific criteria
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.AWS,
            "aws-123");

    // Given non-matching capacity data (different billing provider)
    SubscriptionCapacityView nonMatchingCapacity =
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

    givenCapacityServiceReturns(List.of(nonMatchingCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should process but not find matching subscription
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithPartialMatch() {
    // Given tally summary with specific criteria
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given capacity data with same org and product but different service level
    SubscriptionCapacityView partialMatchCapacity =
        givenCapacityView(
            "sub123",
            ORG_ID,
            "RHEL",
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            100.0,
            false);

    givenCapacityServiceReturns(List.of(partialMatchCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should process but not find matching subscription due to service level mismatch
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithMultipleMatches() {
    // Given tally summary with specific criteria
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given multiple matching capacity views
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

    givenCapacityServiceReturns(List.of(capacity1, capacity2));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should aggregate capacity from multiple subscriptions
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithUnlimitedUsage() {
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

    givenCapacityServiceReturns(List.of(unlimitedCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should handle unlimited usage correctly
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithDifferentOrganizations() {
    // Given tally summary for org123
    TallySummary tallySummary =
        givenTallySummaryWithCriteria(
            "RHEL",
            TallySnapshot.Sla.PREMIUM,
            TallySnapshot.Usage.PRODUCTION,
            TallySnapshot.BillingProvider.RED_HAT,
            null);

    // Given capacity for different organization
    SubscriptionCapacityView differentOrgCapacity =
        givenCapacityView(
            "sub123",
            "org456",
            "RHEL",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider.RED_HAT,
            null,
            100.0,
            false);

    givenCapacityServiceReturns(List.of(differentOrgCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should not match due to different org_id
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
  }

  @Test
  void testCapacityFilteringWithNullValues() {
    // Given tally summary with null service level and usage
    TallySummary tallySummary = givenTallySummaryWithCriteria("RHEL", null, null, null, null);

    // Given capacity with null service level and usage
    SubscriptionCapacityView nullValuesCapacity =
        givenCapacityView("sub123", ORG_ID, "RHEL", null, null, null, null, 100.0, false);

    givenCapacityServiceReturns(List.of(nullValuesCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should match null values correctly
    thenLogWithMessage("Created 1 utilization summaries from 1 tally messages");
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

    givenCapacityServiceReturns(List.of(rhelCapacity, openshiftCapacity));

    // When processing the tally summary
    whenReceiveTallySummary(tallySummary);

    // Then should create utilization summary for each snapshot
    thenLogWithMessage("Created 2 utilization summaries from 1 tally messages");
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

  private void thenLogWithMessage(String str) {
    LOGGER_CAPTOR.thenInfoLogWithMessage(str);
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

  private void givenCapacityServiceReturns(List<SubscriptionCapacityView> capacityViews) {
    when(capacityService.getCapacityForTallySummaries(any())).thenReturn(capacityViews);
  }
}
