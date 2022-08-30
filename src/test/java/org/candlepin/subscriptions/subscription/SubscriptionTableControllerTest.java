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

import static org.candlepin.subscriptions.utilization.api.model.ProductId.OPENSHIFT_METRICS;
import static org.candlepin.subscriptions.utilization.api.model.ProductId.RHEL;
import static org.candlepin.subscriptions.utilization.api.model.ProductId.RHEL_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.SubscriptionCapacityViewRepository;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
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

  @MockBean SubscriptionCapacityViewRepository subscriptionCapacityViewRepository;
  @MockBean SubscriptionCapacityRepository subCapRepo;
  @MockBean AccountListSource accountListSource;
  @Autowired ApplicationClock clock;

  @Autowired SubscriptionTableController subscriptionTableController;

  @BeforeEach
  void setup() throws AccountListSourceException {
    // The @ReportingAccessRequired annotation checks if the org of the user is allowlisted
    // to receive reports or not. This org will be used throughout most tests.
    when(accountListSource.containsReportingAccount("account123456")).thenReturn(true);
  }

  private static final SubCapSpec RH0180191 =
      SubCapSpec.offering(
          "RH0180191", "RHEL Server", 2, 0, 0, 0, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final SubCapSpec RH00604F5 =
      SubCapSpec.offering(
          "RH00604F5", "RHEL Server", 2, 0, 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION, false);
  private static final SubCapSpec RH0180192_SOCKETS =
      SubCapSpec.offering(
          "RH0180192",
          "RHEL Server",
          2,
          null,
          2,
          null,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          false);
  private static final SubCapSpec RH0180193_CORES =
      SubCapSpec.offering(
          "RH0180193",
          "RHEL Server",
          null,
          2,
          null,
          2,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          false);
  private static final SubCapSpec RH0180194_SOCKETS_AND_CORES =
      SubCapSpec.offering(
          "RH0180194", "RHEL Server", 2, 2, 2, 2, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final SubCapSpec RH0180195_UNLIMITED_USAGE =
      SubCapSpec.offering(
          "RH0180192",
          "RHEL Server",
          null,
          null,
          null,
          null,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          true);
  private static final SubCapSpec RH0180196_VIRTUAL_SOCKETS =
      SubCapSpec.offering(
          "RH0180196", "RHEL Server", 0, 0, 2, 0, ServiceLevel.STANDARD, Usage.PRODUCTION, false);
  private static final SubCapSpec RH0180197_VIRTUAL_CORES =
      SubCapSpec.offering(
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
   * Creates a list of capacities that can be returned by a mock SubscriptionCapacityRepository.
   *
   * @param org The organization that owns the capacities
   * @param specs specifies what sub capacities to return
   * @return a list of subscription capacity views
   */
  private List<SubscriptionCapacityView> givenCapacities(
      Org org, ProductId productId, SubCapSpec... specs) {
    return Arrays.stream(specs)
        .map(s -> s.createSubCapView(org, productId))
        .collect(Collectors.toUnmodifiableList());
  }

  @Test
  void testGetSkuCapacityReportSingleSub() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a socket
    // capacity of 2,
    ProductId productId = RHEL_SERVER;
    Sub expectedSub = Sub.sub("1234", "1235", 4);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(Org.STANDARD, productId, RH0180191.withSub(expectedSub));

    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null);

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
    assertEquals(expectedSub.end, actualItem.getNextEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testGetSkuCapacityReportMultipleSubsSameSku() {
    // Given an org with two active subs with different quantities for the same SKU,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedOlderSub),
            RH0180191.withSub(expectedNewerSub));
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null);

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
        expectedOlderSub.end,
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
    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedNewerSub),
            RH00604F5.withSub(expectedOlderSub));
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product, sorted by SKU
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, SkuCapacityReportSort.SKU, null);

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
    assertEquals(expectedOlderSub.end, actualItem.getNextEventDate(), "Wrong upcoming event date");

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
    assertEquals(expectedNewerSub.end, actualItem.getNextEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testGetSkuCapacityReportNoSub() {
    // Given an org with no active subs,
    ProductId productId = RHEL_SERVER;
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    // When requesting a SKU capacity report for an eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null);

    // Then the report contains no inventory items.
    assertEquals(0, actual.getData().size(), "An empty inventory list should be returned.");
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {

    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedOlderSub),
            RH0180191.withSub(expectedNewerSub));

    when(subscriptionCapacityViewRepository.findAllBy(
            eq("owner123456"),
            eq(RHEL.toString()),
            eq(ServiceLevel._ANY),
            eq(Usage._ANY),
            any(),
            any(),
            any()))
        .thenReturn(givenCapacities);

    SkuCapacityReport report =
        subscriptionTableController.capacityReportBySku(
            RHEL, null, null, null, null, null, null, null, SkuCapacityReportSort.SKU, null);
    assertEquals(1, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {
    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedOlderSub),
            RH0180191.withSub(expectedNewerSub));

    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), eq(ServiceLevel.STANDARD), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    SkuCapacityReport reportForUnmatchedSLA =
        subscriptionTableController.capacityReportBySku(
            RHEL_SERVER,
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
            RHEL_SERVER,
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

    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedOlderSub),
            RH0180191.withSub(expectedNewerSub));

    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), any(), eq(Usage.PRODUCTION), any(), any(), any()))
        .thenReturn(givenCapacities);

    SkuCapacityReport reportForUnmatchedUsage =
        subscriptionTableController.capacityReportBySku(
            RHEL_SERVER,
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
            RHEL_SERVER,
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

    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            RHEL_SERVER,
            RH0180191.withSub(Sub.sub("1236", "1237", 5, 6, 6)),
            RH00604F5.withSub(Sub.sub("1234", "1235", 4, 5, 7)),
            SubCapSpec.offering(
                    "RH00604F6",
                    "RHEL Server",
                    2,
                    0,
                    2,
                    0,
                    ServiceLevel.PREMIUM,
                    Usage.PRODUCTION,
                    false)
                .withSub(Sub.sub("1237", "1235", 4, 5, 7)),
            SubCapSpec.offering(
                    "RH00604F7",
                    "RHEL Server",
                    2,
                    0,
                    2,
                    0,
                    ServiceLevel.PREMIUM,
                    Usage.PRODUCTION,
                    false)
                .withSub(Sub.sub("1238", "1235", 4, 5, 7)),
            SubCapSpec.offering(
                    "RH0060192",
                    "RHEL Server",
                    2,
                    0,
                    2,
                    0,
                    ServiceLevel.PREMIUM,
                    Usage.PRODUCTION,
                    false)
                .withSub(Sub.sub("1239", "1235", 4, 5, 7)));

    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    SkuCapacityReport reportWithOffsetAndLimit =
        subscriptionTableController.capacityReportBySku(
            RHEL_SERVER, 0, 2, null, null, null, null, null, SkuCapacityReportSort.SKU, null);
    assertEquals(2, reportWithOffsetAndLimit.getData().size());
    assertEquals(5, reportWithOffsetAndLimit.getMeta().getCount());
  }

  @Test
  void testShouldUseUomQueryParam() {
    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    Sub expectedMuchOlderSub = Sub.sub("1238", "1237", 5, 24, 6);
    List<SubscriptionCapacityView> capacitiesWithCores =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180193_CORES.withSub(expectedNewerSub),
            RH0180194_SOCKETS_AND_CORES.withSub(expectedMuchOlderSub));

    List<SubscriptionCapacityView> capacitiesWithSockets =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180192_SOCKETS.withSub(expectedOlderSub),
            RH0180194_SOCKETS_AND_CORES.withSub(expectedMuchOlderSub));

    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), eq(ServiceLevel.STANDARD), any(), any(), any(), eq(Uom.CORES)))
        .thenReturn(capacitiesWithCores);
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), eq(ServiceLevel.STANDARD), any(), any(), any(), eq(Uom.SOCKETS)))
        .thenReturn(capacitiesWithSockets);

    SkuCapacityReport reportForMatchingCoresUom =
        subscriptionTableController.capacityReportBySku(
            RHEL_SERVER,
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
            RHEL_SERVER,
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
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), any(), any(), any(), any(), any()))
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
            Uom.CORES,
            SkuCapacityReportSort.SKU,
            null);

    assertEquals(SubscriptionType.ANNUAL, report.getMeta().getSubscriptionType());
  }

  @Test
  void testShouldPopulateOnDemandSubscriptionType() {
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), any(), any(), any(), any(), any(), any()))
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
            Uom.CORES,
            SkuCapacityReportSort.SKU,
            null);

    assertEquals(SubscriptionType.ON_DEMAND, report.getMeta().getSubscriptionType());
  }

  @Test
  void testGetSkuCapacityReportUnlimitedQuantity() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with unlimited
    // usage.
    ProductId productId = RHEL_SERVER;
    Sub expectedSub = Sub.sub("1234", "1235", 4);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(Org.STANDARD, productId, RH0180195_UNLIMITED_USAGE.withSub(expectedSub));

    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null);

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
    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedNewerSub),
            RH0180195_UNLIMITED_USAGE.withSub(expectedOlderSub));
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

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
            SkuCapacityReportSort.TOTAL_CAPACITY,
            null);

    // Then the report contains two inventory items containing a sub with appropriate
    // quantity and capacities, and RH00604F5 is listed first.
    assertEquals(
        2,
        actual.getData().size(),
        "Both subs are for different SKUs so should collect into two capacity items.");

    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");

    actualItem = actual.getData().get(1);
    assertEquals(
        RH0180195_UNLIMITED_USAGE.sku,
        actualItem.getSku(),
        "Wrong SKU. (Incorrect ordering of SKUs?)");
  }

  @Test
  void testShouldSortUnlimitedFirstDescending() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    ProductId productId = RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedNewerSub),
            RH0180195_UNLIMITED_USAGE.withSub(expectedOlderSub));
    when(subscriptionCapacityViewRepository.findAllBy(
            any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

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
        RH0180195_UNLIMITED_USAGE.sku,
        actualItem.getSku(),
        "Wrong SKU. (Incorrect ordering of SKUs?)");

    actualItem = actual.getData().get(1);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
  }

  @Test
  void testGetSkuCapacityReportVirtualSocketsOnly() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a virtual socket
    // capacity of 2,
    ProductId productId = RHEL_SERVER;
    Sub expectedSub = Sub.sub("1234", "1235", 4);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(Org.STANDARD, productId, RH0180196_VIRTUAL_SOCKETS.withSub(expectedSub));


    when(subscriptionCapacityViewRepository.findAllBy(
        any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(RH0180196_VIRTUAL_SOCKETS.sku, actualItem.getSku(), "Wrong SKU");
    assertCapacities(0, 8, Uom.SOCKETS, actualItem);
  }

  @Test
  void testGetSkuCapacityReportVirtualCoresOnly() {
    // Given an org with one active sub with a quantity of 4 and has an eng product with a virtual socket
    // capacity of 2,
    ProductId productId = RHEL_SERVER;
    Sub expectedSub = Sub.sub("1234", "1235", 4);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(Org.STANDARD, productId, RH0180197_VIRTUAL_CORES.withSub(expectedSub));


    when(subscriptionCapacityViewRepository.findAllBy(
        any(), anyString(), any(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            productId, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(RH0180197_VIRTUAL_CORES.sku, actualItem.getSku(), "Wrong SKU");
    assertCapacities(0, 8, Uom.CORES, actualItem);
  }

  private static void assertCapacities(
      int expectedPhysCap, int expectedVirtCap, Uom expectedUom, SkuCapacity actual) {
    assertEquals(expectedUom, actual.getUom(), "Wrong UOM");
    assertEquals(expectedPhysCap, actual.getPhysicalCapacity(), "Wrong Physical Capacity");
    assertEquals(expectedVirtCap, actual.getVirtualCapacity(), "Wrong Virtual Capacity");
    assertEquals(
        expectedPhysCap + expectedVirtCap, actual.getTotalCapacity(), "Wrong Total Capacity");
  }

  private static void assertSubscription(Sub expectedSub, SkuCapacitySubscription actual) {
    assertEquals(expectedSub.id, actual.getId(), "Wrong Subscription ID");
    assertEquals(expectedSub.number, actual.getNumber(), "Wrong Subscription Number");
  }

  /** A very stripped-down and immutable form of a Subscription used for test data. */
  private static class Sub {
    private final String id;
    private final String number;
    private final Integer quantity;
    private final OffsetDateTime start;
    private final OffsetDateTime end;

    private Sub(
        String id, String number, Integer quantity, OffsetDateTime start, OffsetDateTime end) {
      this.id = id;
      this.number = number;
      this.quantity = quantity;
      this.start = start;
      this.end = end;
    }

    public static Sub sub(String id, String number, Integer quantity) {
      return sub(id, number, quantity, 6, 6);
    }

    /* Also specifies the sub active timeframe, relative to now. */
    public static Sub sub(
        String id, String number, Integer quantity, int startMonthsAgo, int endMonthsAway) {
      OffsetDateTime subStart = OffsetDateTime.now().minusMonths(startMonthsAgo);
      OffsetDateTime subEnd = subStart.plusMonths(startMonthsAgo + endMonthsAway);

      return new Sub(id, number, quantity, subStart, subEnd);
    }
  }

  /** Immutable, reusable item to generate SubscriptionCapacityViews as tests need them. */
  private static class SubCapSpec {
    private final Sub sub;

    // Fields in this section specify the Offering used in the SubscriptionCapacity.
    private final String sku;
    private final String productName;
    private final Integer physicalSockets;
    private final Integer physicalCores;
    private final Integer virtualSockets;
    private final Integer virtualCores;
    private final ServiceLevel serviceLevel;
    private final Usage usage;
    private final boolean hasUnlimitedUsage;

    private SubCapSpec(
        String sku,
        String productName,
        Integer physicalSockets,
        Integer physicalCores,
        Integer virtualSockets,
        Integer virtualCores,
        ServiceLevel serviceLevel,
        Usage usage,
        boolean hasUnlimitedUsage,
        Sub sub) {
      this.sku = sku;
      this.productName = productName;
      this.physicalSockets = physicalSockets;
      this.physicalCores = physicalCores;
      this.virtualSockets = virtualSockets;
      this.virtualCores = virtualCores;
      this.serviceLevel = serviceLevel;
      this.usage = usage;
      this.hasUnlimitedUsage = hasUnlimitedUsage;
      this.sub = sub;
    }

    /*
    Creates a new Subscription Capacity Specification, specifying only the Offering details. The
    Swatch ProductId, subscription quantity, and org info still need supplied in order to create
    a full SubscriptionCapacityView.
    */
    public static SubCapSpec offering(
        String sku,
        String productName,
        Integer physicalSockets,
        Integer physicalCores,
        Integer virtualSockets,
        Integer virtualCores,
        ServiceLevel serviceLevel,
        Usage usage,
        Boolean hasUnlimitedUsage) {
      return new SubCapSpec(
          sku,
          productName,
          physicalSockets,
          physicalCores,
          virtualSockets,
          virtualCores,
          serviceLevel,
          usage,
          hasUnlimitedUsage,
          null);
    }

    /*
    Returns a new Subscription Capacity Specification but with the subscription information added.
     */
    public SubCapSpec withSub(Sub sub) {
      return new SubCapSpec(
          sku,
          productName,
          physicalSockets,
          physicalCores,
          virtualSockets,
          virtualCores,
          serviceLevel,
          usage,
          hasUnlimitedUsage,
          sub);
    }

    public SubscriptionCapacityView createSubCapView(Org org, ProductId productId) {
      return SubscriptionCapacityView.builder()
          .key(
              SubscriptionCapacityKey.builder()
                  .ownerId(org.orgId())
                  .productId(productId.toString())
                  .subscriptionId(sub.id)
                  .build())
          .quantity(sub.quantity)
          .subscriptionNumber(sub.number)
          .accountNumber(org.accountNumber)
          .beginDate(sub.start)
          .endDate(sub.end)
          .physicalCores(totalCapacity(physicalCores, sub.quantity))
          .physicalSockets(totalCapacity(physicalSockets, sub.quantity))
          .virtualSockets(totalCapacity(virtualSockets, sub.quantity))
          .virtualCores(totalCapacity(virtualCores, sub.quantity))
          .hasUnlimitedUsage(hasUnlimitedUsage)
          .sku(sku)
          .serviceLevel(serviceLevel)
          .usage(usage)
          .productName(productName)
          .build();
    }

    private static Integer totalCapacity(Integer capacity, long quantity) {
      return capacity == null ? null : capacity * (int) quantity;
    }
  }
}
