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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.model.TallyMeasurement;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository;
import com.redhat.swatch.panache.Specification;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionCapacityServiceTest {
  private static final ProductId RHEL = ProductId.fromString("RHEL for x86");
  private static final ProductId ROSA = ProductId.fromString("rosa");

  @Mock private SubscriptionCapacityViewRepository capacityRepository;

  private SubscriptionCapacityService subscriptionCapacityService;

  @BeforeEach
  void setUp() {
    subscriptionCapacityService = new SubscriptionCapacityService(capacityRepository);
  }

  @Test
  void testGetCapacityForTallySummaryWithEmptyTallySnapshots() {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId("org123");
    tallySummary.setTallySnapshots(List.of());
    var result = subscriptionCapacityService.getCapacityForTallySummary(tallySummary);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetCapacityForTallySummariesWithSingleTallySummary() {
    // Given single tally summary for RHEL
    givenExistingCapacityViews(createCapacityView("sub123", "org123", RHEL));
    TallySummary tallyMessage = createTallySummary("org123", RHEL);

    // When
    var result = subscriptionCapacityService.getCapacityForTallySummary(tallyMessage);

    // Then: Returns matching capacity data
    assertSingleCapacityResult(result, "sub123", "org123", RHEL);
  }

  @Test
  void testGetCapacityForTallySummariesWithMultipleTallySummaries() {
    // Given multiple tally summaries from different orgs
    givenExistingCapacityViews(
        createCapacityView("sub123", "org123", RHEL), createCapacityView("sub456", "org456", ROSA));

    TallySummary msg1 = createTallySummary("org123", RHEL);
    TallySummary msg2 = createTallySummary("org456", ROSA);

    // When: each Kafka message (one TallySummary) is processed individually
    var r1 = subscriptionCapacityService.getCapacityForTallySummary(msg1);
    var r2 = subscriptionCapacityService.getCapacityForTallySummary(msg2);
    var result = new HashMap<TallySnapshot, List<SubscriptionCapacityView>>(r1);
    result.putAll(r2);

    // Then returns capacity data for both
    assertMultipleCapacityResults(result, List.of("sub123", "sub456"));
  }

  @Test
  void testGetCapacityForTallySummariesWithServiceLevel() {
    // Given tally summary with Premium service level and matching capacity
    SubscriptionCapacityView matchingCapacity = createCapacityView("sub123", "org123", RHEL);
    matchingCapacity.setServiceLevel(com.redhat.swatch.common.model.ServiceLevel.PREMIUM);
    givenExistingCapacityViews(matchingCapacity);

    TallySummary tallyMessage = createTallySummaryWithServiceLevel("org123", RHEL, "Premium");

    // When
    var result = subscriptionCapacityService.getCapacityForTallySummary(tallyMessage);

    // Then service level filtering works correctly in runtime
    assertEquals(1, result.size());
    var capacities = result.values().iterator().next();
    assertEquals(
        com.redhat.swatch.common.model.ServiceLevel.PREMIUM, capacities.get(0).getServiceLevel());
  }

  @Test
  void testGetCapacityForTallySummariesWithBillingProvider() {
    // Given tally summary with AWS billing provider and matching capacity
    givenExistingCapacityViews(createCapacityViewWithBillingProviderAws("sub123", "org123", RHEL));

    TallySummary tallyMessage = createTallySummaryWithBillingProvider("org123", RHEL, "aws");

    // When
    var result = subscriptionCapacityService.getCapacityForTallySummary(tallyMessage);

    // Then billing provider mapping works correctly
    assertSingleCapacityResult(result, "sub123", "org123", RHEL);
    assertEquals(
        BillingProvider.AWS, result.values().iterator().next().get(0).getBillingProvider());
  }

  @Test
  void testGetCapacityForTallySummariesWithMultipleSnapshotsInSameSummary() {
    // Given single summary with multiple product snapshots
    givenExistingCapacityViews(
        createCapacityView("sub123", "org123", RHEL), createCapacityView("sub456", "org123", ROSA));

    TallySummary tallySummary = createTallySummaryWithMultipleSnapshots("org123", RHEL, ROSA);

    // When
    var result = subscriptionCapacityService.getCapacityForTallySummary(tallySummary);

    // Then processes all snapshots in the summary
    assertEquals(2, result.size());
  }

  @Test
  void testSnapshotWithAnySlaMatchesAllCapacities() {
    // Given capacity with Premium SLA
    SubscriptionCapacityView capacity = createCapacityView("sub123", "org123", RHEL);
    capacity.setServiceLevel(com.redhat.swatch.common.model.ServiceLevel.PREMIUM);
    capacity.setUsage(com.redhat.swatch.common.model.Usage.PRODUCTION);
    givenExistingCapacityViews(capacity);

    // Given tally snapshot with _ANY SLA/Usage
    TallySnapshot snapshot = createTallySnapshot(RHEL);
    snapshot.setSla(TallySnapshot.Sla.ANY);
    snapshot.setUsage(TallySnapshot.Usage.ANY);
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId("org123");
    tallySummary.setTallySnapshots(List.of(snapshot));

    var result = subscriptionCapacityService.getCapacityForTallySummary(tallySummary);

    assertEquals(1, result.size());
    assertEquals("sub123", result.values().iterator().next().get(0).getSubscriptionId());
  }

  @Test
  void testSnapshotWithMatchingSlaGetsCorrectCapacity() {
    // Given two capacities: one Premium and one Self-Support
    SubscriptionCapacityView premiumCapacity = createCapacityView("sub-premium", "org123", RHEL);
    premiumCapacity.setServiceLevel(com.redhat.swatch.common.model.ServiceLevel.PREMIUM);
    premiumCapacity.setUsage(com.redhat.swatch.common.model.Usage.PRODUCTION);

    SubscriptionCapacityView selfSupportCapacity =
        createCapacityView("sub-self-support", "org123", RHEL);
    selfSupportCapacity.setServiceLevel(com.redhat.swatch.common.model.ServiceLevel.SELF_SUPPORT);
    selfSupportCapacity.setUsage(com.redhat.swatch.common.model.Usage.DEVELOPMENT_TEST);

    when(capacityRepository.streamBy(any()))
        .thenReturn(Stream.of(premiumCapacity, selfSupportCapacity))
        .thenReturn(Stream.of(selfSupportCapacity));

    // Given two snapshots: one _ANY and one Self-Support
    TallySnapshot anySnapshot = createTallySnapshot(RHEL);
    anySnapshot.setSla(TallySnapshot.Sla.ANY);
    anySnapshot.setUsage(TallySnapshot.Usage.ANY);

    TallySnapshot selfSupportSnapshot = createTallySnapshot(RHEL);
    selfSupportSnapshot.setSla(TallySnapshot.Sla.SELF_SUPPORT);
    selfSupportSnapshot.setUsage(TallySnapshot.Usage.DEVELOPMENT_TEST);

    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId("org123");
    tallySummary.setTallySnapshots(List.of(anySnapshot, selfSupportSnapshot));

    var result = subscriptionCapacityService.getCapacityForTallySummary(tallySummary);

    // _ANY snapshot matches both capacities
    assertEquals(2, result.get(anySnapshot).size());

    // Self-Support snapshot matches only the Self-Support capacity
    assertEquals(1, result.get(selfSupportSnapshot).size());
    assertEquals("sub-self-support", result.get(selfSupportSnapshot).get(0).getSubscriptionId());
  }

  @Test
  void testGetCapacityForTallySummariesWithNoCapacityFound() {
    // Given tally summary but no matching capacity data
    TallySummary tallyMessage = createTallySummary("org123", RHEL);
    when(capacityRepository.streamBy(any(Specification.class))).thenReturn(Stream.empty());

    // When
    var result = subscriptionCapacityService.getCapacityForTallySummary(tallyMessage);

    // Then returns empty map
    assertTrue(result.isEmpty());
  }

  private void givenExistingCapacityViews(SubscriptionCapacityView... capacityViews) {
    when(capacityRepository.streamBy(any()))
        .thenAnswer(invocationOnMock -> Stream.of(capacityViews));
  }

  private void assertSingleCapacityResult(
      Map<TallySnapshot, List<SubscriptionCapacityView>> result,
      String expectedSubId,
      String expectedOrgId,
      ProductId expectedProduct) {
    assertNotNull(result);
    assertEquals(1, result.size());
    var capacities = result.values().iterator().next();
    assertEquals(1, capacities.size());
    assertEquals(expectedSubId, capacities.get(0).getSubscriptionId());
    assertEquals(expectedOrgId, capacities.get(0).getOrgId());
    assertEquals(expectedProduct.getValue(), capacities.get(0).getProductTag());
  }

  private void assertMultipleCapacityResults(
      Map<TallySnapshot, List<SubscriptionCapacityView>> result, List<String> expectedSubIds) {
    assertNotNull(result);
    assertEquals(expectedSubIds.size(), result.size());

    for (String expectedSubId : expectedSubIds) {
      assertTrue(
          result.values().stream()
              .anyMatch(
                  c -> c.stream().anyMatch(cv -> expectedSubId.equals(cv.getSubscriptionId()))),
          "Should contain capacity for " + expectedSubId);
    }
  }

  private TallySummary createTallySummary(String orgId, ProductId productId) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    TallySnapshot snapshot = createTallySnapshot(productId);
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  private TallySummary createTallySummaryWithServiceLevel(
      String orgId, ProductId productId, String serviceLevel) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    TallySnapshot snapshot = createTallySnapshot(productId);
    snapshot.setSla(TallySnapshot.Sla.fromValue(serviceLevel));
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  private TallySummary createTallySummaryWithBillingProvider(
      String orgId, ProductId productId, String billingProvider) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    TallySnapshot snapshot = createTallySnapshot(productId);
    snapshot.setBillingProvider(TallySnapshot.BillingProvider.fromValue(billingProvider));
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  private TallySummary createTallySummaryWithMultipleSnapshots(
      String orgId, ProductId... productIds) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    List<TallySnapshot> snapshots = Stream.of(productIds).map(this::createTallySnapshot).toList();
    tallySummary.setTallySnapshots(snapshots);

    return tallySummary;
  }

  private TallySnapshot createTallySnapshot(ProductId productId) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setId(UUID.randomUUID());
    snapshot.setProductId(productId.getValue());
    snapshot.setSnapshotDate(OffsetDateTime.now());
    snapshot.setGranularity(TallySnapshot.Granularity.DAILY);

    // Add some measurements
    TallyMeasurement measurement = new TallyMeasurement();
    measurement.setMetricId("Cores");
    measurement.setHardwareMeasurementType("PHYSICAL");
    measurement.setValue(10.0);
    measurement.setCurrentTotal(100.0);

    snapshot.setTallyMeasurements(List.of(measurement));

    return snapshot;
  }

  private SubscriptionCapacityView createCapacityViewWithBillingProviderAws(
      String subscriptionId, String orgId, ProductId productTag) {
    SubscriptionCapacityView capacityView = createCapacityView(subscriptionId, orgId, productTag);
    capacityView.setBillingProvider(BillingProvider.AWS);
    return capacityView;
  }

  private SubscriptionCapacityView createCapacityView(
      String subscriptionId, String orgId, ProductId productTag) {
    SubscriptionCapacityView capacityView = new SubscriptionCapacityView();
    capacityView.setSubscriptionId(subscriptionId);
    capacityView.setSubscriptionNumber("SUB-" + subscriptionId);
    capacityView.setOrgId(orgId);
    capacityView.setProductTag(productTag.getValue());
    capacityView.setSku("RH00001");
    capacityView.setProductName(productTag + " Product");
    capacityView.setQuantity(100L);
    capacityView.setHasUnlimitedUsage(false);
    capacityView.setStartDate(OffsetDateTime.now().minusDays(30));
    capacityView.setEndDate(OffsetDateTime.now().plusDays(365));

    // Add metrics
    SubscriptionCapacityViewMetric metric = new SubscriptionCapacityViewMetric();
    metric.setMetricId("Cores");
    metric.setCapacity(50.0);
    metric.setMeasurementType("PHYSICAL");

    capacityView.setMetrics(Set.of(metric));

    return capacityView;
  }
}
