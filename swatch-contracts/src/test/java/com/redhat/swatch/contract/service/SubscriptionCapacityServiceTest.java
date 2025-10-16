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

import com.redhat.swatch.contract.model.TallyMeasurement;
import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySummary;
import com.redhat.swatch.contract.repository.BillingProvider;
import com.redhat.swatch.contract.repository.SubscriptionCapacityView;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewMetric;
import com.redhat.swatch.contract.repository.SubscriptionCapacityViewRepository;
import com.redhat.swatch.panache.Specification;
import java.time.OffsetDateTime;
import java.util.List;
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

  @Mock private SubscriptionCapacityViewRepository capacityRepository;

  private SubscriptionCapacityService subscriptionCapacityService;

  @BeforeEach
  void setUp() {
    subscriptionCapacityService = new SubscriptionCapacityService(capacityRepository);
  }

  @Test
  void testGetCapacityForTallySummariesWithEmptyList() {
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(List.of());

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetCapacityForTallySummariesWithNullList() {
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(null);

    assertTrue(result.isEmpty());
  }

  @Test
  void testGetCapacityForTallySummariesWithSingleTallySummary() {
    // Given single tally summary for RHEL
    givenExistingCapacityViews(createCapacityView("sub123", "org123", "RHEL"));
    List<TallySummary> tallyMessages = List.of(createTallySummary("org123", "RHEL"));

    // When
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(tallyMessages);

    // Then: Returns matching capacity data
    assertSingleCapacityResult(result, "sub123", "org123", "RHEL");
  }

  @Test
  void testGetCapacityForTallySummariesWithMultipleTallySummaries() {
    // Given multiple tally summaries from different orgs
    givenExistingCapacityViews(
        createCapacityView("sub123", "org123", "RHEL"),
        createCapacityView("sub456", "org456", "OpenShift"));

    List<TallySummary> tallyMessages =
        List.of(createTallySummary("org123", "RHEL"), createTallySummary("org456", "OpenShift"));

    // When
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(tallyMessages);

    // Then returns capacity data for both
    assertMultipleCapacityResults(result, List.of("sub123", "sub456"));
  }

  @Test
  void testGetCapacityForTallySummariesWithServiceLevel() {
    // Given tally summary with Premium service level
    givenExistingCapacityViews(createCapacityView("sub123", "org123", "RHEL"));

    List<TallySummary> tallyMessages =
        List.of(createTallySummaryWithServiceLevel("org123", "RHEL", "Premium"));

    // When
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(tallyMessages);

    // Then service level mapping works correctly
    assertEquals(1, result.size());
  }

  @Test
  void testGetCapacityForTallySummariesWithBillingProvider() {
    // Given tally summary with AWS billing provider
    givenExistingCapacityViews(
        createCapacityViewWithBillingProviderAws("sub123", "org123", "RHEL"));

    List<TallySummary> tallyMessages =
        List.of(createTallySummaryWithBillingProvider("org123", "RHEL", "aws"));

    // When
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(tallyMessages);

    // Then billing provider mapping works correctly
    assertSingleCapacityResult(result, "sub123", "org123", "RHEL");
    assertEquals(BillingProvider.AWS, result.get(0).getBillingProvider());
  }

  @Test
  void testGetCapacityForTallySummariesWithMultipleSnapshotsInSameSummary() {
    // Given single summary with multiple product snapshots
    givenExistingCapacityViews(
        createCapacityView("sub123", "org123", "RHEL"),
        createCapacityView("sub456", "org123", "OpenShift"));

    TallySummary tallySummary =
        createTallySummaryWithMultipleSnapshots("org123", "RHEL", "OpenShift");

    // When
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(List.of(tallySummary));

    // Then processes all snapshots in the summary
    assertEquals(2, result.size());
  }

  @Test
  void testGetCapacityForTallySummariesWithNoCapacityFound() {
    // Given tally summary but no matching capacity data
    List<TallySummary> tallyMessages = List.of(createTallySummary("org123", "RHEL"));
    when(capacityRepository.streamBy(any(Specification.class))).thenReturn(Stream.empty());

    // When
    List<SubscriptionCapacityView> result =
        subscriptionCapacityService.getCapacityForTallySummaries(tallyMessages);

    // Then returns empty list
    assertTrue(result.isEmpty());
  }

  private void givenExistingCapacityViews(SubscriptionCapacityView... capacityViews) {
    when(capacityRepository.streamBy(any(Specification.class)))
        .thenReturn(Stream.of(capacityViews));
  }

  private void assertSingleCapacityResult(
      List<SubscriptionCapacityView> result,
      String expectedSubId,
      String expectedOrgId,
      String expectedProduct) {
    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(expectedSubId, result.get(0).getSubscriptionId());
    assertEquals(expectedOrgId, result.get(0).getOrgId());
    assertEquals(expectedProduct, result.get(0).getProductTag());
  }

  private void assertMultipleCapacityResults(
      List<SubscriptionCapacityView> result, List<String> expectedSubIds) {
    assertNotNull(result);
    assertEquals(expectedSubIds.size(), result.size());

    for (String expectedSubId : expectedSubIds) {
      assertTrue(
          result.stream().anyMatch(cv -> expectedSubId.equals(cv.getSubscriptionId())),
          "Should contain capacity for " + expectedSubId);
    }
  }

  private TallySummary createTallySummary(String orgId, String productId) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    TallySnapshot snapshot = createTallySnapshot(productId);
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  private TallySummary createTallySummaryWithServiceLevel(
      String orgId, String productId, String serviceLevel) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    TallySnapshot snapshot = createTallySnapshot(productId);
    snapshot.setSla(TallySnapshot.Sla.fromValue(serviceLevel));
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  private TallySummary createTallySummaryWithBillingProvider(
      String orgId, String productId, String billingProvider) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    TallySnapshot snapshot = createTallySnapshot(productId);
    snapshot.setBillingProvider(TallySnapshot.BillingProvider.fromValue(billingProvider));
    tallySummary.setTallySnapshots(List.of(snapshot));

    return tallySummary;
  }

  private TallySummary createTallySummaryWithMultipleSnapshots(String orgId, String... productIds) {
    TallySummary tallySummary = new TallySummary();
    tallySummary.setOrgId(orgId);

    List<TallySnapshot> snapshots = Stream.of(productIds).map(this::createTallySnapshot).toList();
    tallySummary.setTallySnapshots(snapshots);

    return tallySummary;
  }

  private TallySnapshot createTallySnapshot(String productId) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setId(UUID.randomUUID());
    snapshot.setProductId(productId);
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
      String subscriptionId, String orgId, String productTag) {
    SubscriptionCapacityView capacityView = createCapacityView(subscriptionId, orgId, productTag);
    capacityView.setBillingProvider(BillingProvider.AWS);
    return capacityView;
  }

  private SubscriptionCapacityView createCapacityView(
      String subscriptionId, String orgId, String productTag) {
    SubscriptionCapacityView capacityView = new SubscriptionCapacityView();
    capacityView.setSubscriptionId(subscriptionId);
    capacityView.setSubscriptionNumber("SUB-" + subscriptionId);
    capacityView.setOrgId(orgId);
    capacityView.setProductTag(productTag);
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
