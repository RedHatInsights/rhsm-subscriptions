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
package org.candlepin.subscriptions.resource.api.v2;

import static org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository.PHYSICAL;
import static org.candlepin.subscriptions.resource.api.v1.CapacityResource.HYPERVISOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityViewMetric;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacitySubscription;
import org.candlepin.subscriptions.utilization.api.model.SortDirection;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionEventType;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.candlepin.subscriptions.utilization.api.v2.model.SkuCapacityReportSort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
class SubscriptionTableControllerTest {

  private static final ProductId RHEL_FOR_X86 = ProductId.fromString("RHEL for x86");
  private static final String OFFERING_DESCRIPTION_SUFFIX = " test description";

  @MockBean SubscriptionCapacityViewRepository repository;
  @Autowired ApplicationClock clock;
  @Autowired SubscriptionTableController subscriptionTableController;

  private static final MeasurementSpec RH0180191 =
      MeasurementSpec.offering(
          "RH0180191", "RHEL Server", 2, 0, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH00604F5 =
      MeasurementSpec.offering(
          "RH00604F5", "RHEL Server", 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180192_SOCKETS =
      MeasurementSpec.offering(
          "RH0180192", "RHEL Server", 2, null, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180193_CORES =
      MeasurementSpec.offering(
          "RH0180193", "RHEL Server", null, 2, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180194_SOCKETS_AND_CORES =
      MeasurementSpec.offering(
          "RH0180194", "RHEL Server", 2, 2, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final MeasurementSpec RH0180195_UNLIMITED_USAGE =
      MeasurementSpec.offering(
          "RH0180192", "RHEL Server", null, null, ServiceLevel.STANDARD, Usage.PRODUCTION, true);
  private static final MeasurementSpec RH0180196_HYPERVISOR_SOCKETS =
      MeasurementSpec.offering(
          "RH0180196",
          "RHEL Server",
          2,
          0,
          HYPERVISOR,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          false);

  private enum Org {
    STANDARD("711497");

    private final String orgId;

    Org(String orgId) {
      this.orgId = orgId;
    }
  }

  /**
   * Creates a list of SubscriptionMeasurements that can be returned by a mock
   * SubscriptionMeasurementRepository.
   *
   * @param specs specifies what sub capacities to return
   */
  private void givenCapacities(ProductId productId, MeasurementSpec... specs) {

    Arrays.stream(specs)
        .forEach(
            s -> {
              var measurements = s.createMeasurements(productId);
              s.subscription.setMetrics(measurements);
            });
  }

  @Test
  void testGetSkuCapacityReportSingleSub() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a socket
    // capacity of 2,
    var productId = RHEL_FOR_X86;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var spec = RH0180191.withSub(expectedSub);

    givenCapacities(productId, spec);
    givenSubscriptionsInRepository(expectedSub);
    givenOfferings(spec);
    // When requesting a SKU capacity report for the eng product,
    var actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    var actualItem = actual.getData().get(0);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(4, actualItem.getQuantity(), "Incorrect quantity");
    assertEquals(MetricIdUtils.getSockets().toString(), actual.getMeta().getMeasurements().get(0));
    assertEquals(8, actualItem.getMeasurements().get(0));
    assertSubscription(expectedSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.SUBSCRIPTION_END,
        actualItem.getNextEventType(),
        "Wrong upcoming event type");
    assertEquals(
        expectedSub.getEndDate(), actualItem.getNextEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testGetSkuCapacityReportMultipleSubsSameSku() {
    // Given an org with two active subs with different quantities for the same SKU,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);

    givenCapacities(productId, spec1, spec2);
    givenSubscriptionsInRepository(expectedOlderSub, expectedNewerSub);

    givenOfferings(spec1); // spec2 is the same offering

    // When requesting a SKU capacity report for the eng product,
    var actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the subs and appropriate
    // quantity and capacities.
    assertEquals(
        1,
        actual.getData().size(),
        "Both subs are for same SKU so should collect into one capacity item.");
    var actualItem = actual.getData().get(0);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(
        9, actualItem.getQuantity(), "Item should contain the sum of all subs' quantities");
    assertEquals(18, actualItem.getMeasurements().get(0), "Wrong Standard Capacity");
    assertEquals(actualItem.getProductName(), actualItem.getProductName());
    assertEquals(
        SubscriptionEventType.SUBSCRIPTION_END,
        actualItem.getNextEventType(),
        "Wrong upcoming event type");
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
    var productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedNewerSub);
    var spec2 = RH00604F5.withSub(expectedOlderSub);

    givenCapacities(productId, spec1, spec2);
    givenSubscriptionsInRepository(expectedNewerSub, expectedOlderSub);

    givenOfferings(spec1, spec2);

    // When requesting a SKU capacity report for the eng product, sorted by SKU
    var actual =
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

    var actualItem = actual.getData().get(0);
    assertEquals(RH00604F5.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        5,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertEquals(10, actualItem.getMeasurements().get(0), "Wrong Standard Capacity");
    assertEquals(MetricIdUtils.getSockets().toString(), actual.getMeta().getMeasurements().get(0));
    assertSubscription(expectedOlderSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.SUBSCRIPTION_END,
        actualItem.getNextEventType(),
        "Wrong upcoming event type");
    assertEquals(
        expectedOlderSub.getEndDate(), actualItem.getNextEventDate(), "Wrong upcoming event date");

    actualItem = actual.getData().get(1);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        4,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertEquals(8, actualItem.getMeasurements().get(0), "Wrong Standard Capacity");
    assertEquals(MetricIdUtils.getSockets().toString(), actual.getMeta().getMeasurements().get(0));
    assertSubscription(expectedNewerSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.SUBSCRIPTION_END,
        actualItem.getNextEventType(),
        "Wrong upcoming event type");
    assertEquals(
        expectedNewerSub.getEndDate(),
        actualItem.getNextEventDate(),
        "Wrong upcoming event " + "date");
  }

  @Test
  void testGetSkuCapacityReportNoSub() {
    // Given an org with no active subs,

    givenSubscriptionsInRepository();

    // When requesting a SKU capacity report for an eng product,
    var actual =
        subscriptionTableController.capacityReportBySku(
            RHEL_FOR_X86, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains no inventory items.
    assertEquals(0, actual.getData().size(), "An empty inventory list should be returned.");
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {

    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);

    givenCapacities(RHEL_FOR_X86, spec1, spec2);
    givenSubscriptionsInRepository(expectedNewerSub, expectedOlderSub);

    givenOfferings(spec1); // spec2 is the same offering

    var report =
        subscriptionTableController.capacityReportBySku(
            RHEL_FOR_X86,
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
    assertEquals(1, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {
    ProductId productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);
    givenCapacities(productId, spec1, spec2);

    givenSubscriptionsInRepository();

    givenOfferings(spec1); // spec2 is the same offering

    var reportForUnmatchedSLA =
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

    givenSubscriptionsInRepository(expectedNewerSub);
    var reportForMatchingSLA =
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
    var productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedOlderSub);
    var spec2 = RH0180191.withSub(expectedNewerSub);
    givenCapacities(productId, spec1, spec2);

    givenSubscriptionsInRepository();

    givenOfferings(spec1); // spec2 is the same offering

    var reportForUnmatchedUsage =
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

    givenSubscriptionsInRepository(expectedNewerSub);
    var reportForMatchingUsage =
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
    var productId = RHEL_FOR_X86;
    var spec1 = RH0180191.withSub(stubSubscription("1236", "1237", 5, 6, 6));
    var spec2 = RH00604F5.withSub(stubSubscription("1234", "1235", 4, 5, 7));
    var rh0060192 =
        MeasurementSpec.offering(
                "RH0060192", "RHEL Server", 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION, false)
            .withSub(stubSubscription("1239", "1235", 4, 5, 7));
    var rh00604f7 =
        MeasurementSpec.offering(
                "RH00604F7", "RHEL Server", 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION, false)
            .withSub(stubSubscription("1238", "1235", 4, 5, 7));
    var rh00604f6 =
        MeasurementSpec.offering(
                "RH00604F6", "RHEL Server", 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION, false)
            .withSub(stubSubscription("1237", "1235", 4, 5, 7));

    givenCapacities(productId, spec1, spec2, rh00604f6, rh00604f7, rh0060192);

    givenSubscriptionsInRepository(
        spec1.subscription,
        spec2.subscription,
        rh00604f6.subscription,
        rh00604f7.subscription,
        rh0060192.subscription);

    givenOfferings(spec1, spec2, rh00604f6, rh00604f7, rh0060192);

    var reportWithOffsetAndLimit =
        subscriptionTableController.capacityReportBySku(
            productId, 0, 2, null, null, null, null, null, null, SkuCapacityReportSort.SKU, null);
    assertEquals(2, reportWithOffsetAndLimit.getData().size());
    assertEquals(5, reportWithOffsetAndLimit.getMeta().getCount());
  }

  @Test
  void testShouldUseMetricIdQueryParam() {
    var productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var expectedMuchOlderSub = stubSubscription("1238", "1237", 5, 24, 6);

    var coresSpec1 = RH0180193_CORES.withSub(expectedNewerSub);
    var coresSpec2 = RH0180194_SOCKETS_AND_CORES.withSub(expectedMuchOlderSub);
    givenOfferings(coresSpec1, coresSpec2);

    var socketsSpec1 = RH0180192_SOCKETS.withSub(expectedOlderSub);
    var socketsSpec2 = RH0180194_SOCKETS_AND_CORES.withSub(expectedMuchOlderSub);
    givenOfferings(socketsSpec1, socketsSpec2);

    givenCapacities(productId, coresSpec1, coresSpec2);
    givenCapacities(productId, socketsSpec1, socketsSpec2);
    givenSubscriptionsInRepository(coresSpec1.subscription, coresSpec2.subscription);
    givenSubscriptionsInRepository(socketsSpec1.subscription, socketsSpec2.subscription);

    var reportForMatchingCoresUom =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            ServiceLevelType.STANDARD,
            null,
            null,
            null,
            MetricIdUtils.getCores().toString(),
            SkuCapacityReportSort.SKU,
            null);
    assertEquals(2, reportForMatchingCoresUom.getData().size());

    var reportForMatchingSocketsUom =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            null,
            ServiceLevelType.STANDARD,
            null,
            null,
            null,
            MetricIdUtils.getSockets().toString(),
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
                    RHEL_FOR_X86,
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

    givenSubscriptionsInRepository();

    var report =
        subscriptionTableController.capacityReportBySku(
            RHEL_FOR_X86,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            MetricIdUtils.getCores().toString(),
            SkuCapacityReportSort.SKU,
            null);

    assertEquals(SubscriptionType.ANNUAL, report.getMeta().getSubscriptionType());
  }

  @Test
  void testShouldPopulateOnDemandSubscriptionType() {

    givenSubscriptionsInRepository();

    var report =
        subscriptionTableController.capacityReportBySku(
            ProductId.fromString("OpenShift-metrics"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            MetricIdUtils.getCores().toString(),
            SkuCapacityReportSort.SKU,
            null);

    assertEquals(SubscriptionType.ON_DEMAND, report.getMeta().getSubscriptionType());
  }

  @Test
  void testGetSkuCapacityReportUnlimitedQuantity() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with unlimited
    // usage.
    var productId = RHEL_FOR_X86;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var unlimitedSpec = RH0180195_UNLIMITED_USAGE.withSub(expectedSub);

    givenCapacities(productId, unlimitedSpec);
    givenSubscriptionsInRepository(expectedSub);

    when(repository.streamBy(any())).thenReturn(Stream.of(unlimitedSpec.subscription));
    givenOfferings(unlimitedSpec);

    // When requesting a SKU capacity report for the eng product,
    var actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and HasInfiniteQuantity
    // should be true.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    var actualItem = actual.getData().get(0);
    assertTrue(actualItem.getHasInfiniteQuantity(), "HasInfiniteQuantity should be true");
  }

  @Test
  void testShouldSortUnlimitedLastAscending() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedNewerSub);
    var unlimitedSpec = RH0180195_UNLIMITED_USAGE.withSub(expectedOlderSub);

    givenCapacities(productId, spec1, unlimitedSpec);
    givenSubscriptionsInRepository(expectedNewerSub, expectedOlderSub, unlimitedSpec.subscription);
    givenOfferings(spec1, unlimitedSpec);

    // When requesting a SKU capacity report for the eng product, sorted by quantity
    var actual =
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
            SkuCapacityReportSort.QUANTITY,
            null);

    // Then the report contains two inventory items containing a sub with appropriate
    // quantity and capacities, and RH00604F5 is listed first.
    assertEquals(
        2,
        actual.getData().size(),
        "Both subs are for different SKUs so should collect into two capacity items.");

    assertEquals(
        spec1.sku, actual.getData().get(0).getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        unlimitedSpec.sku,
        actual.getData().get(1).getSku(),
        "Wrong SKU. (Incorrect ordering of SKUs?)");
  }

  @Test
  void testShouldSortUnlimitedFirstDescending() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    var productId = RHEL_FOR_X86;
    var expectedNewerSub = stubSubscription("1234", "1235", 4, 5, 7);
    var expectedOlderSub = stubSubscription("1236", "1237", 5, 6, 6);
    var spec1 = RH0180191.withSub(expectedNewerSub);
    var unlimitedSpec = RH0180195_UNLIMITED_USAGE.withSub(expectedOlderSub);

    givenCapacities(productId, spec1, unlimitedSpec);
    givenSubscriptionsInRepository(expectedNewerSub, expectedOlderSub, unlimitedSpec.subscription);
    givenOfferings(spec1, unlimitedSpec);

    // When requesting a SKU capacity report for the eng product, sorted by quantity
    var actual =
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
            SkuCapacityReportSort.QUANTITY,
            SortDirection.DESC);

    // Then the report contains two inventory items containing a sub with appropriate
    // quantity and capacities, and RH0180195 is listed first.
    assertEquals(
        2,
        actual.getData().size(),
        "Both subs are for different SKUs so should collect into two capacity items.");

    assertEquals(
        unlimitedSpec.sku,
        actual.getData().get(0).getSku(),
        "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        spec1.sku, actual.getData().get(1).getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
  }

  @Test
  void testGetSkuCapacityReportHypervisorSocketsOnly() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a
    // hypervisor socket capacity of 2,
    var productId = RHEL_FOR_X86;
    var expectedSub = stubSubscription("1234", "1235", 4);
    var spec1 = RH0180196_HYPERVISOR_SOCKETS.withSub(expectedSub);

    givenCapacities(productId, spec1);
    givenSubscriptionsInRepository(expectedSub);

    givenOfferings(spec1);

    // When requesting a SKU capacity report for the eng product,
    var actual =
        subscriptionTableController.capacityReportBySku(
            productId,
            null,
            null,
            ReportCategory.HYPERVISOR,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    var actualItem = actual.getData().get(0);
    assertEquals(spec1.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(8, actualItem.getMeasurements().get(0), "Wrong Standard Capacity");
    assertEquals(ReportCategory.HYPERVISOR, actualItem.getCategory());
    assertEquals(MetricIdUtils.getSockets().toString(), actual.getMeta().getMeasurements().get(0));
  }

  private void givenOfferings(MeasurementSpec... specs) {
    Stream.of(specs)
        .forEach(
            spec -> {
              Offering offering = spec.createOffering();
              spec.subscription.setSku(offering.getSku());
              spec.subscription.setHasUnlimitedUsage(offering.isHasUnlimitedUsage());
              spec.subscription.setUsage(offering.getUsage());
              spec.subscription.setServiceLevel(offering.getServiceLevel());
              spec.subscription.setProductName(offering.getProductName());
            });
  }

  private void givenSubscriptionsInRepository(SubscriptionCapacityView... subs) {
    Mockito.doAnswer(c -> Stream.of(subs)).when(repository).streamBy(any());
  }

  private static void assertSubscription(
      SubscriptionCapacityView expectedSub, SkuCapacitySubscription actual) {
    assertEquals(expectedSub.getSubscriptionId(), actual.getId(), "Wrong Subscription ID");
    assertEquals(
        expectedSub.getSubscriptionNumber(), actual.getNumber(), "Wrong Subscription Number");
  }

  private static SubscriptionCapacityView stubSubscription(
      String id, String number, Integer quantity) {
    return stubSubscription(id, number, quantity, 6, 6);
  }

  /* Also specifies the sub active timeframe, relative to now. */
  private static SubscriptionCapacityView stubSubscription(
      String id, String number, Integer quantity, int startMonthsAgo, int endMonthsAway) {
    OffsetDateTime subStart = OffsetDateTime.now().minusMonths(startMonthsAgo);
    OffsetDateTime subEnd = subStart.plusMonths(startMonthsAgo + endMonthsAway);

    SubscriptionCapacityView subscription = new SubscriptionCapacityView();
    subscription.setSubscriptionId(id);
    subscription.setSubscriptionNumber(number);
    subscription.setQuantity(quantity);
    subscription.setStartDate(subStart);
    subscription.setEndDate(subEnd);
    return subscription;
  }

  /** Class to generate SubscriptionMeasurements as tests need them. */
  private static class MeasurementSpec {
    private SubscriptionCapacityView subscription;

    // Fields in this section specify the Offering used in the SubscriptionCapacity.
    final String sku;
    final String productName;
    final Integer sockets;
    final Integer cores;
    final String category;
    final Map<String, Integer> otherMetrics;
    final ServiceLevel serviceLevel;
    final Usage usage;
    final boolean hasUnlimitedUsage;

    private MeasurementSpec(
        String sku,
        String productName,
        Integer sockets,
        Integer cores,
        String category,
        Map<String, Integer> otherMetrics,
        ServiceLevel serviceLevel,
        Usage usage,
        boolean hasUnlimitedUsage,
        SubscriptionCapacityView subscription) {
      this.sku = sku;
      this.productName = productName;
      this.sockets = sockets;
      this.cores = cores;
      this.category = category;
      this.otherMetrics = otherMetrics;
      this.serviceLevel = serviceLevel;
      this.usage = usage;
      this.hasUnlimitedUsage = hasUnlimitedUsage;
      this.subscription = subscription;
    }

    public MeasurementSpec withMetric(String metric, Integer value) {
      otherMetrics.put(metric, value);
      return this;
    }

    public static MeasurementSpec offering(
        String sku,
        String productName,
        Integer sockets,
        Integer cores,
        ServiceLevel serviceLevel,
        Usage usage,
        Boolean hasUnlimitedUsage) {
      return offering(
          sku, productName, sockets, cores, PHYSICAL, serviceLevel, usage, hasUnlimitedUsage);
    }

    public static MeasurementSpec offering(
        String sku,
        String productName,
        Integer sockets,
        Integer cores,
        String category,
        ServiceLevel serviceLevel,
        Usage usage,
        Boolean hasUnlimitedUsage) {
      return new MeasurementSpec(
          sku,
          productName,
          sockets,
          cores,
          category,
          new HashMap<>(),
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
    public MeasurementSpec withSub(SubscriptionCapacityView sub) {
      return new MeasurementSpec(
          sku,
          productName,
          sockets,
          cores,
          category,
          otherMetrics,
          serviceLevel,
          usage,
          hasUnlimitedUsage,
          sub);
    }

    public SubscriptionCapacityViewMetric buildMeasurement(
        String type, MetricId metricId, Double value) {
      SubscriptionCapacityViewMetric metric = new SubscriptionCapacityViewMetric();
      metric.setMeasurementType(type);
      metric.setMetricId(metricId.toUpperCaseFormatted());
      metric.setCapacity(value);
      return metric;
    }

    public Set<SubscriptionCapacityViewMetric> createMeasurements(ProductId productId) {

      if (subscription == null) {
        subscription = stubSubscription("sub123", "number123", 1);
      }

      subscription.setProductTag(productId.toString());
      subscription.setSku(sku);
      subscription.setHasUnlimitedUsage(hasUnlimitedUsage);
      subscription.setOrgId(Org.STANDARD.orgId);

      var quantity = subscription.getQuantity();

      var coresMetric = MetricId.fromString("Cores");
      var socketsMetric = MetricId.fromString("Sockets");

      var measurements = new HashSet<SubscriptionCapacityViewMetric>();
      measurements.add(buildMeasurement(category, coresMetric, totalCapacity(cores, quantity)));
      measurements.add(buildMeasurement(category, socketsMetric, totalCapacity(sockets, quantity)));
      for (Map.Entry<String, Integer> otherMetric : otherMetrics.entrySet()) {
        measurements.add(
            buildMeasurement(
                category,
                MetricId.fromString(otherMetric.getKey()),
                totalCapacity(otherMetric.getValue(), quantity)));
      }

      subscription.setProductTag(productId.getValue());
      subscription.setMetrics(measurements);

      return measurements;
    }

    public Offering createOffering() {
      return Offering.builder()
          .sku(sku)
          .hasUnlimitedUsage(hasUnlimitedUsage)
          .serviceLevel(serviceLevel)
          .usage(usage)
          .productName(productName)
          .description(sku + OFFERING_DESCRIPTION_SUFFIX)
          .build();
    }

    private static Double totalCapacity(Integer capacity, long quantity) {
      return capacity == null ? null : (double) (capacity * quantity);
    }
  }
}
