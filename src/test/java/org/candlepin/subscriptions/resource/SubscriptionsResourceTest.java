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
package org.candlepin.subscriptions.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.AccountListSource;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityView;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.ProductId;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacity;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacityReport;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacityReportSort;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacitySubscription;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionEventType;
import org.candlepin.subscriptions.utilization.api.model.Uom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
public class SubscriptionsResourceTest {
  @MockBean SubscriptionCapacityRepository subCapRepo;
  @MockBean AccountListSource accountListSource;
  @Autowired ApplicationClock clock;

  @Autowired SubscriptionsResource target;

  @BeforeEach
  void setup() throws AccountListSourceException {
    // The @ReportingAccessRequired annotation checks if the org of the user is allowlisted
    // to receive reports or not. This org will be used throughout most tests.
    when(accountListSource.containsReportingAccount("account123456")).thenReturn(true);
  }

  private static final SubCapSpec RH0180191 =
      SubCapSpec.offering(
          "RH0180191", "RHEL Server", 2, 0, 0, 0, ServiceLevel.STANDARD, Usage.PRODUCTION);
  private static final SubCapSpec RH00604F5 =
      SubCapSpec.offering(
          "RH00604F5", "RHEL Server", 2, 0, 2, 0, ServiceLevel.PREMIUM, Usage.PRODUCTION);

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
    ProductId productId = ProductId.RHEL_SERVER;
    Sub expectedSub = Sub.sub("1234", "1235", 4);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(Org.STANDARD, productId, RH0180191.withSub(expectedSub));
    when(subCapRepo.findByKeyOwnerIdAndKeyProductId(any(), anyString(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        target.getSkuCapacityReport(
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
        SubscriptionEventType.END, actualItem.getUpcomingEventType(), "Wrong upcoming event type");
    assertEquals(expectedSub.end, actualItem.getUpcomingEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testGetSkuCapacityReportMultipleSubsSameSku() {
    // Given an org with two active subs with different quantities for the same SKU,
    // and the subs have an eng product with a socket capacity of 2,
    // and the subs have different ending dates,
    ProductId productId = ProductId.RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedOlderSub),
            RH0180191.withSub(expectedNewerSub));
    when(subCapRepo.findByKeyOwnerIdAndKeyProductId(any(), anyString(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        target.getSkuCapacityReport(
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
        SubscriptionEventType.END, actualItem.getUpcomingEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedOlderSub.end,
        actualItem.getUpcomingEventDate(),
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
    ProductId productId = ProductId.RHEL_SERVER;
    Sub expectedNewerSub = Sub.sub("1234", "1235", 4, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", "1237", 5, 6, 6);
    List<SubscriptionCapacityView> givenCapacities =
        givenCapacities(
            Org.STANDARD,
            productId,
            RH0180191.withSub(expectedNewerSub),
            RH00604F5.withSub(expectedOlderSub));
    when(subCapRepo.findByKeyOwnerIdAndKeyProductId(any(), anyString(), any(), any(), any(), any()))
        .thenReturn(givenCapacities);

    // When requesting a SKU capacity report for the eng product, sorted by SKU
    SkuCapacityReport actual =
        target.getSkuCapacityReport(
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
        SubscriptionEventType.END, actualItem.getUpcomingEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedOlderSub.end, actualItem.getUpcomingEventDate(), "Wrong upcoming event date");

    actualItem = actual.getData().get(1);
    assertEquals(RH0180191.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        4,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertCapacities(8, 0, Uom.SOCKETS, actualItem);
    assertSubscription(expectedNewerSub, actualItem.getSubscriptions().get(0));
    assertEquals(
        SubscriptionEventType.END, actualItem.getUpcomingEventType(), "Wrong upcoming event type");
    assertEquals(
        expectedNewerSub.end, actualItem.getUpcomingEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testGetSkuCapacityReportNoSub() {
    // Given an org with no active subs,
    ProductId productId = ProductId.RHEL_SERVER;
    when(subCapRepo.findByKeyOwnerIdAndKeyProductId(any(), anyString(), any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());

    // When requesting a SKU capacity report for an eng product,
    SkuCapacityReport actual =
        target.getSkuCapacityReport(
            productId, null, null, null, null, null, null, null, null, null);

    // Then the report contains no inventory items.
    assertEquals(0, actual.getData().size(), "An empty inventory list should be returned.");
  }

  // These tests are adapted from tests in CapacityResourceTest.
  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {
    //      SubscriptionCapacity capacity = new SubscriptionCapacity();
    //      capacity.setBeginDate(min);
    //      capacity.setEndDate(max);
    //
    //      when(repository.findByOwnerAndProductId(
    //              eq("owner123456"),
    //              eq(RHEL.toString()),
    //              eq(ServiceLevel._ANY),
    //              eq(Usage._ANY),
    //              eq(min),
    //              eq(max)))
    //              .thenReturn(Collections.singletonList(capacity));
    //
    //      CapacityReport report =
    //              resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null,
    // null, null);
    //
    //      assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        capacity.setBeginDate(min);
    //        capacity.setEndDate(max);
    //
    //        when(repository.findByOwnerAndProductId(
    //                eq("owner123456"),
    //                eq(RHEL.toString()),
    //                eq(ServiceLevel.PREMIUM),
    //                eq(Usage._ANY),
    //                eq(min),
    //                eq(max)))
    //                .thenReturn(Collections.singletonList(capacity));
    //
    //        CapacityReport report =
    //                resource.getCapacityReport(
    //                        RHEL, GranularityType.DAILY, min, max, null, null,
    // ServiceLevelType.PREMIUM, null);
    //
    //        assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseUsageQueryParam() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        capacity.setBeginDate(min);
    //        capacity.setEndDate(max);
    //
    //        when(repository.findByOwnerAndProductId(
    //                eq("owner123456"),
    //                eq(RHEL.toString()),
    //                eq(ServiceLevel._ANY),
    //                eq(Usage.PRODUCTION),
    //                eq(min),
    //                eq(max)))
    //                .thenReturn(Collections.singletonList(capacity));
    //
    //        CapacityReport report =
    //                resource.getCapacityReport(
    //                        RHEL, GranularityType.DAILY, min, max, null, null, null,
    // UsageType.PRODUCTION);
    //
    //        assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptySlaAsNull() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        capacity.setBeginDate(min);
    //        capacity.setEndDate(max);
    //
    //        when(repository.findByOwnerAndProductId(
    //                eq("owner123456"),
    //                eq(RHEL.toString()),
    //                eq(ServiceLevel._ANY),
    //                eq(Usage._ANY),
    //                eq(min),
    //                eq(max)))
    //                .thenReturn(Collections.singletonList(capacity));
    //
    //        CapacityReport report =
    //                resource.getCapacityReport(
    //                        RHEL, GranularityType.DAILY, min, max, null, null,
    // ServiceLevelType.EMPTY, null);
    //
    //        assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptyUsageAsNull() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        capacity.setBeginDate(min);
    //        capacity.setEndDate(max);
    //
    //        when(repository.findByOwnerAndProductId(
    //                eq("owner123456"),
    //                eq(RHEL.toString()),
    //                eq(ServiceLevel._ANY),
    //                eq(Usage._ANY),
    //                eq(min),
    //                eq(max)))
    //                .thenReturn(Collections.singletonList(capacity));
    //
    //        CapacityReport report =
    //                resource.getCapacityReport(
    //                        RHEL, GranularityType.DAILY, min, max, null, null, null,
    // UsageType.EMPTY);
    //
    //        assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldCalculateCapacityBasedOnMultipleSubscriptions() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        capacity.setVirtualSockets(5);
    //        capacity.setPhysicalSockets(2);
    //        capacity.setVirtualCores(20);
    //        capacity.setPhysicalCores(8);
    //        capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    //        capacity.setEndDate(max);
    //
    //        SubscriptionCapacity capacity2 = new SubscriptionCapacity();
    //        capacity2.setVirtualSockets(7);
    //        capacity2.setPhysicalSockets(11);
    //        capacity2.setVirtualCores(14);
    //        capacity2.setPhysicalCores(22);
    //        capacity2.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    //        capacity2.setEndDate(max);
    //
    //        when(repository.findByOwnerAndProductId(
    //                eq("owner123456"), eq(RHEL.toString()), eq(null), eq(null), eq(min), eq(max)))
    //                .thenReturn(Arrays.asList(capacity, capacity2));
    //
    //        CapacityReport report =
    //                resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null,
    // null, null);
    //
    //        CapacitySnapshot capacitySnapshot = report.getData().get(0);
    //        assertEquals(12, capacitySnapshot.getHypervisorSockets().intValue());
    //        assertEquals(13, capacitySnapshot.getPhysicalSockets().intValue());
    //        assertEquals(34, capacitySnapshot.getHypervisorCores().intValue());
    //        assertEquals(30, capacitySnapshot.getPhysicalCores().intValue());
  }

  @Test
  void testShouldThrowExceptionOnBadOffset() {
    //        SubscriptionsException e =
    //                assertThrows(
    //                        SubscriptionsException.class,
    //                        () -> {
    //                            resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max,
    // 11, 10, null, null);
    //                        });
    //        assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testShouldRespectOffsetAndLimit() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        capacity.setBeginDate(min);
    //        capacity.setEndDate(max);
    //
    //        when(repository.findByOwnerAndProductId(
    //                eq("owner123456"),
    //                eq(RHEL.toString()),
    //                eq(ServiceLevel._ANY),
    //                eq(Usage._ANY),
    //                eq(min),
    //                eq(max)))
    //                .thenReturn(Collections.singletonList(capacity));
    //
    //        CapacityReport report =
    //                resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, 1, 1, null,
    // null);
    //
    //        assertEquals(1, report.getData().size());
    //        assertEquals(
    //                OffsetDateTime.now().minusDays(3).truncatedTo(ChronoUnit.DAYS),
    //                report.getData().get(0).getDate());
  }

  @Test
  @WithMockRedHatPrincipal("1111")
  public void testAccessDeniedWhenAccountIsNotWhitelisted() {
    //        assertThrows(
    //                AccessDeniedException.class,
    //                () -> {
    //                    resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null,
    // null, null, null);
    //                });
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  public void testAccessDeniedWhenUserIsNotAnAdmin() {
    //        assertThrows(
    //                AccessDeniedException.class,
    //                () -> {
    //                    resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null,
    // null, null, null);
    //                });
  }

  @Test
  void testGetCapacitiesWeekly() {
    //        SubscriptionCapacity capacity = new SubscriptionCapacity();
    //        OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    //        OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    //        capacity.setBeginDate(begin);
    //        capacity.setEndDate(end);
    //
    //        when(repository.findByOwnerAndProductId(
    //                "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage.PRODUCTION, begin,
    // end))
    //                .thenReturn(Collections.singletonList(capacity));
    //
    //        List<CapacitySnapshot> actual =
    //                resource.getCapacities(
    //                        "owner123456",
    //                        RHEL,
    //                        ServiceLevel.STANDARD,
    //                        Usage.PRODUCTION,
    //                        Granularity.WEEKLY,
    //                        begin,
    //                        end);
    //
    //        // Add one because we generate reports including both endpoints on the timeline
    //        long expected = ChronoUnit.WEEKS.between(begin, end) + 1;
    //        assertEquals(expected, actual.size());
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
    private final boolean hashUnlimitedGuestSockets = false;

    private SubCapSpec(
        String sku,
        String productName,
        Integer physicalSockets,
        Integer physicalCores,
        Integer virtualSockets,
        Integer virtualCores,
        ServiceLevel serviceLevel,
        Usage usage,
        Sub sub) {
      this.sku = sku;
      this.productName = productName;
      this.physicalSockets = physicalSockets;
      this.physicalCores = physicalCores;
      this.virtualSockets = virtualSockets;
      this.virtualCores = virtualCores;
      this.serviceLevel = serviceLevel;
      this.usage = usage;
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
        Usage usage) {
      return new SubCapSpec(
          sku,
          productName,
          physicalSockets,
          physicalCores,
          virtualSockets,
          virtualCores,
          serviceLevel,
          usage,
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
          sub);
    }

    public SubscriptionCapacityView createSubCapView(Org org, ProductId productId) {
      SubscriptionCapacityView givenCapacity = new SubscriptionCapacityView();
      SubscriptionCapacityKey key =
          SubscriptionCapacityKey.builder()
              .ownerId(org.orgId())
              .productId(productId.toString())
              .subscriptionId(sub.id)
              .build();
      givenCapacity.setKey(key);

      givenCapacity.setBeginDate(sub.start);
      givenCapacity.setEndDate(sub.end);
      givenCapacity.setAccountNumber(org.accountNumber());
      givenCapacity.setPhysicalCores(physicalCores * sub.quantity);
      givenCapacity.setPhysicalSockets(physicalSockets * sub.quantity);
      givenCapacity.setVirtualSockets(virtualSockets * sub.quantity);
      givenCapacity.setVirtualCores(virtualCores * sub.quantity);
      givenCapacity.setServiceLevel(serviceLevel);
      givenCapacity.setUsage(usage);
      givenCapacity.setSku(sku);
      givenCapacity.setHasUnlimitedGuestSockets(hashUnlimitedGuestSockets);
      Subscription subscription =
          Subscription.builder()
              .sku(sku)
              .subscriptionId(sub.id)
              .subscriptionNumber(sub.number)
              .marketplaceSubscriptionId(null)
              .accountNumber(org.accountNumber())
              .ownerId(org.orgId())
              .quantity(sub.quantity)
              .startDate(sub.start)
              .endDate(sub.end)
              .build();
      givenCapacity.setSubscription(subscription);

      Offering offering =
          Offering.builder()
              .sku(this.sku)
              .productName(this.productName)
              .physicalSockets(this.physicalSockets)
              .physicalCores(this.physicalCores)
              .virtualSockets(this.virtualSockets)
              .virtualCores(this.virtualCores)
              .usage(this.usage)
              .serviceLevel(this.serviceLevel)
              .build();
      // childSkus, role, productFamily, and productIds are not specified since they aren't
      // used (or in the case of sku, physical*, virtual*, etc, already known)
      givenCapacity.setOffering(offering);

      return givenCapacity;
    }
  }
}
