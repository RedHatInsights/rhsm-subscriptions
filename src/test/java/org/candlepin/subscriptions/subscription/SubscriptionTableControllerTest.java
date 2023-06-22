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
package org.candlepin.subscriptions.subscription;

import static org.candlepin.subscriptions.utilization.api.model.ProductId.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionMeasurementRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.db.model.SubscriptionProductId;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resource.SubscriptionTableController;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
class SubscriptionTableControllerTest {

  private final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

  @MockBean SubscriptionMeasurementRepository measurementRepository;
  @MockBean SubscriptionRepository subscriptionRepository;
  @MockBean OfferingRepository offeringRepository;
  @MockBean AccountListSource accountListSource;
  @Autowired ApplicationClock clock;

  @Autowired SubscriptionTableController subscriptionTableController;

  @BeforeEach
  void setup() throws AccountListSourceException {
    // The @ReportingAccessRequired annotation checks if the org of the user is allowlisted
    // to receive reports or not. This org will be used throughout most tests.
    when(accountListSource.containsReportingAccount("account123456")).thenReturn(true);
  }

  private static final MeasurementSpec RH0180191 =
      MeasurementSpec.offering(
          "RH0180191", "RHEL Server", 2, 0, 0, 0, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH00604F5 =
      MeasurementSpec.offering(
          "RH00604F5", "RHEL Server", 2, 0, 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180192_SOCKETS =
      MeasurementSpec.offering(
          "RH0180192",
          "RHEL Server",
          2,
          null,
          2,
          null,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          false);
  private static final MeasurementSpec RH0180193_CORES =
      MeasurementSpec.offering(
          "RH0180193",
          "RHEL Server",
          null,
          2,
          null,
          2,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          false);
  private static final MeasurementSpec RH0180194_SOCKETS_AND_CORES =
      MeasurementSpec.offering(
          "RH0180194", "RHEL Server", 2, 2, 2, 2, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180195_UNLIMITED_USAGE =
      MeasurementSpec.offering(
          "RH0180192",
          "RHEL Server",
          null,
          null,
          null,
          null,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          true);
  private static final MeasurementSpec RH0180196_HYPERVISOR_SOCKETS =
      MeasurementSpec.offering(
          "RH0180196", "RHEL Server", 0, 0, 2, 0, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180197_HYPERVISOR_CORES =
      MeasurementSpec.offering(
          "RH0180197", "RHEL Server", 0, 0, 0, 2, ServiceLevel.STANDARD, Usage.PRODUCTION, false);

  private enum Org {
    STANDARD("711497", "477931");

    private final String orgId;
    private final String accountNumber;

    Org(String orgId, String accountNumber) {
      this.orgId = orgId;
      this.accountNumber = accountNumber;
    }

    public String orgId() {
      return orgId;
    }

    public String accountNumber() {
      return accountNumber;
    }
  }

  /**
   * Creates a list of SubscriptionMeasurements that can be returned by a mock
   * SubscriptionMeasurementRepository.
   *
   * @param org The organization that owns the capacities
   * @param specs specifies what sub capacities to return
   * @return a list of subscription capacity views
   */
  private List<SubscriptionMeasurement> givenCapacities(
      Org org, ProductId productId, MeasurementSpec... specs) {
    return Arrays.stream(specs)
        .map(s -> s.createMeasurements(org, productId))
        .flatMap(List::stream)
        .collect(Collectors.toUnmodifiableList());
  }

  private void mockOfferings(MeasurementSpec... specs) {
    Set<String> skus = new HashSet<>();
    List<Offering> offerings = new ArrayList<>();
    Arrays.stream(specs)
        .forEach(
            x -> {
              skus.add(x.sku);
              offerings.add(x.createOffering());
            });
    when(offeringRepository.findBySkuIn(skus)).thenReturn(offerings);
  }

  @Test
  void testGetSkuCapacityReportSingleSub() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a socket
    // capacity of 2,
    var productId = RHEL_SERVER;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var spec = RH0180191.withSub(expectedSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(4, actualItem.getQuantity(), "Incorrect quantity");
    assertCapacities(8, 0, Uom.SOCKETS, actualItem);
    assertSubscription(expectedSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.END, actualItem.getNextEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedSub.getEndDate(), actualItem.getNextEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testGetSkuCapacityReportMultipleSubsSameSku() {
    // Given an org with two active subs with different quantities for the same SKU,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, spec2);
    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1); // spec2 is the same offering

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the subs and appropriate
    // quantity and capacities.
    assertEquals(
        1,
        actual.getData().size(),
        "Both subs are for same SKU so should collect into one capacity item.");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(
        9, actualItem.getQuantity(), "Item should contain the sum of all subs' quantities");
    assertCapacities(18, 0, Uom.SOCKETS, actualItem);
    assertEquals(
        SubscriptionEventType.END, actualItem.getNextEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedOlderSub.getEndDate(),
        actualItem.getNextEventDate(),
        "Wrong upcoming event date. Given two or more subs, the even take should be the subscription enddate closest to now.");

    SkuCapacitySubscription actualSub = actualItem.getSubscriptions().get(0);
    assertSubscription(expectedOlderSub, actualSub);

    actualSub = actualItem.getSubscriptions().get(1);
    assertSubscription(expectedNewerSub, actualSub);
  }

  @Test
  void testGetSkuCapacityReportDifferentSkus() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedNewerSub);
    var spec2 = RH00604F5.withSub(expectedOlderSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, spec2);
    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1, spec2);

    // When requesting a SKU capacity report for the eng product, sorted by SKU
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            SkuCapacityReportSort.SKU,
            null);

    // Then the report contains two inventory items containing a sub with appropriate
    // quantity and capacities, and RH00604F5 is listed first.
    assertEquals(
        2,
        actual.getData().size(),
        "Both subs are for different SKUs so should collect into two capacity items.");

    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(RH00604F5.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        5,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertCapacities(10, 10, Uom.SOCKETS, actualItem);
    assertSubscription(expectedOlderSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.END, actualItem.getNextEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedOlderSub.getEndDate(), actualItem.getNextEventDate(), "Wrong upcoming event date");

    actualItem = actual.getData().get(1);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        4,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertCapacities(8, 0, Uom.SOCKETS, actualItem);
    assertSubscription(expectedNewerSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.END, actualItem.getNextEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedNewerSub.getEndDate(),
        actualItem.getNextEventDate(),
        "Wrong upcoming event " + "date");
  }

  @Test
  void testGetSkuCapacityReportNoSub() {
    // Given an org with no active subs,
    var productId = RHEL_SERVER;
    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(Collections.emptyList());

    // When requesting a SKU capacity report for an eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains no inventory items.
    assertEquals(0, actual.getData().size(), "An empty inventory list should be returned.");
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {
    var productId = RHEL;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, spec2);
    when(measurementRepository.findAllBy(
            eq("owner123456"),
            eq(RHEL.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1); // spec2 is the same offering

    SkuCapacityReport report =
        subscriptionTableController.capacityReportBySku(
            RHEL, null, null, null, null, null, null, null, null, SkuCapacityReportSort.SKU, null);
    assertEquals(1, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {
    ProductId productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, spec2);
    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel.STANDARD),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1); // spec2 is the same offering

    SkuCapacityReport reportForUnmatchedSLA =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            ServiceLevelType.PREMIUM,
            null,
            null,
            null,
            null,
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(0, reportForUnmatchedSLA.getData().size());

    SkuCapacityReport reportForMatchingSLA =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            ServiceLevelType.STANDARD,
            null,
            null,
            null,
            null,
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(1, reportForMatchingSLA.getData().size());
  }

  @Test
  void testShouldUseUsageQueryParam() {
    var productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    Subscription expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, spec2);
    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage.PRODUCTION),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1); // spec2 is the same offering

    SkuCapacityReport reportForUnmatchedUsage =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            null,
            UsageType.DEVELOPMENT_TEST,
            null,
            null,
            null,
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(0, reportForUnmatchedUsage.getData().size());

    SkuCapacityReport reportForMatchingUsage =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            null,
            UsageType.PRODUCTION,
            null,
            null,
            null,
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(1, reportForMatchingUsage.getData().size());
  }

  @Test
  void testCountReturnedInMetaIgnoresOffsetAndLimit() {
    var productId = RHEL_SERVER;
    var spec1 = RH0180191.withSub(stubSubscription("1236", "1237", 5, 6, 6));
    var spec2 = RH00604F5.withSub(stubSubscription("1234", "1235", 4, 5, 7));
    var rh0060192 =
        MeasurementSpec.offering(
                "RH0060192",
                "RHEL Server",
                2,
                0,
                2,
                0,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                false)
            .withSub(stubSubscription("1239", "1235", 4, 5, 7));
    var rh00604f7 =
        MeasurementSpec.offering(
                "RH00604F7",
                "RHEL Server",
                2,
                0,
                2,
                0,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                false)
            .withSub(stubSubscription("1238", "1235", 4, 5, 7));
    var rh00604f6 =
        MeasurementSpec.offering(
                "RH00604F6",
                "RHEL Server",
                2,
                0,
                2,
                0,
                ServiceLevel.PREMIUM,
                Usage.PRODUCTION,
                false)
            .withSub(stubSubscription("1237", "1235", 4, 5, 7));
    var givenCapacities =
        givenCapacities(Org.STANDARD, productId, spec1, spec2, rh00604f6, rh00604f7, rh0060192);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1, spec2, rh00604f6, rh00604f7, rh0060192);

    SkuCapacityReport reportWithOffsetAndLimit =
        subscriptionTableController.capacityReportBySku(
            productId, 0, 2, null, null, null, null, null, null, SkuCapacityReportSort.SKU, null);
    assertEquals(2, reportWithOffsetAndLimit.getData().size());
    assertEquals(5, reportWithOffsetAndLimit.getMeta().getCount());
  }

  @Test
  void testShouldUseUomQueryParam() {
    var productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var expectedMuchOlderSub = stubSubscription("1238", "1237", 5, 24, 6);

    var coresSpec1 = RH0180193_CORES.withSub(expectedNewerSub);
    var coresSpec2 = RH0180194_SOCKETS_AND_CORES.withSub(expectedMuchOlderSub);
    mockOfferings(coresSpec1, coresSpec2);

    var socketsSpec1 = RH0180192_SOCKETS.withSub(expectedOlderSub);
    var socketsSpec2 = RH0180194_SOCKETS_AND_CORES.withSub(expectedMuchOlderSub);
    mockOfferings(socketsSpec1, socketsSpec2);

    var capacitiesWithCores = givenCapacities(Org.STANDARD, productId, coresSpec1, coresSpec2);
    var capacitiesWithSockets =
        givenCapacities(Org.STANDARD, productId, socketsSpec1, socketsSpec2);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            eq(MetricId.CORES),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel.STANDARD),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(capacitiesWithCores);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            eq(MetricId.SOCKETS),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel.STANDARD),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(capacitiesWithSockets);

    SkuCapacityReport reportForMatchingCoresUom =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            ServiceLevelType.STANDARD,
            null,
            null,
            null,
            Uom.CORES,
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(2, reportForMatchingCoresUom.getData().size());

    SkuCapacityReport reportForMatchingSocketsUom =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            ServiceLevelType.STANDARD,
            null,
            null,
            null,
            Uom.SOCKETS,
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(2, reportForMatchingSocketsUom.getData().size());
  }

  @Test
  void testShouldThrowExceptionOnBadOffset() {
    SubscriptionsException e =
        assertThrows(
            SubscriptionsException.class,
            () ->
                subscriptionTableController.capacityReportBySku(
                    RHEL_SERVER,
                    11,
                    10,
                    null,
                    null,
                    UsageType.PRODUCTION,
                    null,
                    null,
                    null,
                    SkuCapacityReportSort.SKU,
                    null));
    assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testShouldPopulateAnnualSubscriptionType() {
    when(measurementRepository.findAllBy(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    SkuCapacityReport report =
        subscriptionTableController.capacityReportBySku(
            RHEL_SERVER,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Uom.CORES,
            SkuCapacityReportSort.SKU,
            null);

    assertEquals(SubscriptionType.ANNUAL, report.getMeta().getSubscriptionType());
  }

  @Test
  void testShouldPopulateOnDemandSubscriptionType() {
    when(measurementRepository.findAllBy(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    SkuCapacityReport report =
        subscriptionTableController.capacityReportBySku(
            OPENSHIFT_METRICS,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Uom.CORES,
            SkuCapacityReportSort.SKU,
            null);

    assertEquals(SubscriptionType.ON_DEMAND, report.getMeta().getSubscriptionType());
  }

  @Test
  void testGetSkuCapacityReportUnlimitedQuantity() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with unlimited
    // usage.
    var productId = RHEL_SERVER;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var unlimitedSpec = RH0180195_UNLIMITED_USAGE.withSub(expectedSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, unlimitedSpec);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    when(subscriptionRepository.findUnlimited(any()))
        .thenReturn(List.of(unlimitedSpec.subscription));
    mockOfferings(unlimitedSpec);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and HasInfiniteQuantity
    // should be true.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertTrue(actualItem.getHasInfiniteQuantity(), "HasInfiniteQuantity should be true");
  }

  @Test
  void testShouldSortUnlimitedLastAscending() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedNewerSub);
    var unlimitedSpec = RH0180195_UNLIMITED_USAGE.withSub(expectedOlderSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, unlimitedSpec);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);

    when(subscriptionRepository.findUnlimited(any()))
        .thenReturn(List.of(unlimitedSpec.subscription));
    mockOfferings(spec1, unlimitedSpec);

    // When requesting a SKU capacity report for the eng product, sorted by quantity
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            SkuCapacityReportSort.TOTAL_CAPACITY,
            null);

    // Then the report contains two inventory items containing a sub with appropriate
    // quantity and capacities, and RH00604F5 is listed first.
    assertEquals(
        2,
        actual.getData().size(),
        "Both subs are for different SKUs so should collect into two capacity items.");

    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(spec1.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");

    actualItem = actual.getData().get(1);
    assertEquals(
        unlimitedSpec.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
  }

  @Test
  void testShouldSortUnlimitedFirstDescending() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_SERVER;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedNewerSub);
    var unlimitedSpec = RH0180195_UNLIMITED_USAGE.withSub(expectedOlderSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1, unlimitedSpec);
    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    when(subscriptionRepository.findUnlimited(any()))
        .thenReturn(List.of(unlimitedSpec.subscription));
    mockOfferings(spec1, unlimitedSpec);

    // When requesting a SKU capacity report for the eng product, sorted by quantity
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            SkuCapacityReportSort.TOTAL_CAPACITY,
            SortDirection.DESC);

    // Then the report contains two inventory items containing a sub with appropriate
    // quantity and capacities, and RH0180195 is listed first.
    assertEquals(
        2,
        actual.getData().size(),
        "Both subs are for different SKUs so should collect into two capacity items.");

    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(
        unlimitedSpec.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");

    actualItem = actual.getData().get(1);
    assertEquals(spec1.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
  }

  @Test
  void testGetSkuCapacityReportHypervisorSocketsOnly() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a
    // hypervisor socket capacity of 2,
    var productId = RHEL_SERVER;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var spec1 = RH0180196_HYPERVISOR_SOCKETS.withSub(expectedSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(spec1.sku, actualItem.getSku(), "Wrong SKU");
    assertCapacities(0, 8, Uom.SOCKETS, actualItem);
  }

  @Test
  void testGetSkuCapacityReportHypervisorCoresOnly() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a
    // hypervisor socket capacity of 2,
    var productId = RHEL_SERVER;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var spec1 = RH0180197_HYPERVISOR_CORES.withSub(expectedSub);
    var givenCapacities = givenCapacities(Org.STANDARD, productId, spec1);

    when(measurementRepository.findAllBy(
            any(),
            eq(productId.toString()),
            nullable(MetricId.class),
            nullable(HypervisorReportCategory.class),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(OffsetDateTime.class),
            any(OffsetDateTime.class)))
        .thenReturn(givenCapacities);
    mockOfferings(spec1);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(spec1.sku, actualItem.getSku(), "Wrong SKU");
    assertCapacities(0, 8, Uom.CORES, actualItem);
  }

  private static void assertCapacities(
      int expectedCap, int expectedHypCap, Uom expectedUom, SkuCapacity actual) {
    assertEquals(expectedUom, actual.getUom(), "Wrong UOM");
    assertEquals(expectedCap, actual.getCapacity(), "Wrong Standard Capacity");
    assertEquals(expectedHypCap, actual.getHypervisorCapacity(), "Wrong Hypervisor Capacity");
    assertEquals(expectedCap + expectedHypCap, actual.getTotalCapacity(), "Wrong Total Capacity");
  }

  private static void assertSubscription(Subscription expectedSub, SkuCapacitySubscription actual) {
    assertEquals(expectedSub.getSubscriptionId(), actual.getId(), "Wrong Subscription ID");
    assertEquals(
        expectedSub.getSubscriptionNumber(), actual.getNumber(), "Wrong Subscription Number");
  }

  private static Subscription stubSubscription(String id, String number, Integer quantity) {
    return stubSubscription(id, number, quantity, 6, 6);
  }

  /* Also specifies the sub active timeframe, relative to now. */
  private static Subscription stubSubscription(
      String id, String number, Integer quantity, int startMonthsAgo, int endMonthsAway) {
    OffsetDateTime subStart = OffsetDateTime.now().minusMonths(startMonthsAgo);
    OffsetDateTime subEnd = subStart.plusMonths(startMonthsAgo + endMonthsAway);

    return Subscription.builder()
        .subscriptionId(id)
        .subscriptionNumber(number)
        .quantity(quantity)
        .startDate(subStart)
        .endDate(subEnd)
        .build();
  }

  /** Class to generate SubscriptionMeasurements as tests need them. */
  private static class MeasurementSpec {
    private Subscription subscription;

    // Fields in this section specify the Offering used in the SubscriptionCapacity.
    final String sku;
    final String productName;
    final Integer sockets;
    final Integer cores;
    final Integer hypervisorSockets;
    final Integer hypervisorCores;
    final ServiceLevel serviceLevel;
    final Usage usage;
    final boolean hasUnlimitedUsage;

    private MeasurementSpec(
        String sku,
        String productName,
        Integer sockets,
        Integer cores,
        Integer hypervisorSockets,
        Integer hypervisorCores,
        ServiceLevel serviceLevel,
        Usage usage,
        boolean hasUnlimitedUsage,
        Subscription subscription) {
      this.sku = sku;
      this.productName = productName;
      this.sockets = sockets;
      this.cores = cores;
      this.hypervisorSockets = hypervisorSockets;
      this.hypervisorCores = hypervisorCores;
      this.serviceLevel = serviceLevel;
      this.usage = usage;
      this.hasUnlimitedUsage = hasUnlimitedUsage;
      this.subscription = subscription;
    }

    /*
    Creates a new Subscription Capacity Specification, specifying only the Offering details. The
    Swatch ProductId, subscription quantity, and org info still need supplied in order to create
    a full SubscriptionCapacityView.
    */
    public static MeasurementSpec offering(
        String sku,
        String productName,
        Integer sockets,
        Integer cores,
        Integer hypervisorSockets,
        Integer hypervisorCores,
        ServiceLevel serviceLevel,
        Usage usage,
        Boolean hasUnlimitedUsage) {
      return new MeasurementSpec(
          sku,
          productName,
          sockets,
          cores,
          hypervisorSockets,
          hypervisorCores,
          serviceLevel,
          usage,
          hasUnlimitedUsage,
          null);
    }

    /**
     * Returns a new Subscription Capacity Specification but with the subscription information
     * added. We want to return a new one, so we don't mutate the objects created from .offering as
     * test fixtures.
     */
    public MeasurementSpec withSub(Subscription sub) {
      return new MeasurementSpec(
          sku,
          productName,
          sockets,
          cores,
          hypervisorSockets,
          hypervisorCores,
          serviceLevel,
          usage,
          hasUnlimitedUsage,
          sub);
    }

    public SubscriptionMeasurement buildMeasurement(String type, MetricId metric, Double value) {
      if (value == null) {
        return null;
      }

      return SubscriptionMeasurement.builder()
          .measurementType(type)
          .metricId(metric.toString().toUpperCase())
          .value(value)
          .build();
    }

    public List<SubscriptionMeasurement> createMeasurements(Org org, ProductId productId) {
      if (subscription == null) {
        subscription = stubSubscription("sub123", "number123", 1);
      }

      var offering = Offering.builder().sku(sku).hasUnlimitedUsage(hasUnlimitedUsage).build();

      offering.addSubscription(subscription);
      subscription.setOrgId(org.orgId);
      subscription.setAccountNumber(org.accountNumber);

      var quantity = subscription.getQuantity();
      var measurements =
          Stream.of(
                  buildMeasurement("PHYSICAL", MetricId.CORES, totalCapacity(cores, quantity)),
                  buildMeasurement("PHYSICAL", MetricId.SOCKETS, totalCapacity(sockets, quantity)),
                  buildMeasurement(
                      "HYPERVISOR", MetricId.CORES, totalCapacity(hypervisorCores, quantity)),
                  buildMeasurement(
                      "HYPERVISOR", MetricId.SOCKETS, totalCapacity(hypervisorSockets, quantity)))
              .filter(Objects::nonNull)
              .toList();

      var pId = SubscriptionProductId.builder().productId(productId.toString()).build();

      subscription.addSubscriptionProductId(pId);
      subscription.addSubscriptionMeasurements(measurements);

      return measurements;
    }

    public Offering createOffering() {
      return Offering.builder()
          .sku(sku)
          .hasUnlimitedUsage(hasUnlimitedUsage)
          .serviceLevel(serviceLevel)
          .usage(usage)
          .productName(productName)
          .build();
    }

    private static Double totalCapacity(Integer capacity, long quantity) {
      return capacity == null ? null : (double) (capacity * quantity);
    }
  }
}
