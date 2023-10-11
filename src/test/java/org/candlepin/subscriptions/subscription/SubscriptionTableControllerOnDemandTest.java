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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.ProductId;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.db.OfferingRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.resource.SubscriptionTableController;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.tally.AccountListSourceException;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.BillingProviderType;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacity;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacityReport;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacityReportSort;
import org.candlepin.subscriptions.utilization.api.model.SkuCapacitySubscription;
import org.candlepin.subscriptions.utilization.api.model.SubscriptionEventType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"api", "test"})
@WithMockRedHatPrincipal("123456")
class SubscriptionTableControllerOnDemandTest {

  private static final ProductId RHOSAK = ProductId.fromString("rhosak");
  private static final String OFFERING_DESCRIPTION_SUFFIX = " test description";

  @MockBean SubscriptionRepository subscriptionRepository;
  @MockBean OfferingRepository offeringRepository;
  @Autowired ApplicationClock clock;

  @Autowired SubscriptionTableController subscriptionTableController;

  @BeforeEach
  void setup() throws AccountListSourceException {
    // The @ReportingAccessRequired annotation checks if the org of the user is allowlisted
    // to receive reports or not. This org will be used throughout most tests.
  }

  private static final SubCapSpec MW01882 =
      SubCapSpec.offering(
          "MW01882",
          "OpenShift Streams for Apache Kafka",
          null,
          null,
          null,
          null,
          ServiceLevel.STANDARD,
          Usage.PRODUCTION,
          false);
  private static final SubCapSpec MW01882RN =
      SubCapSpec.offering(
          "MW01882RN",
          "OpenShift Streams for Apache Kafka",
          null,
          null,
          null,
          null,
          ServiceLevel.PREMIUM,
          Usage.PRODUCTION,
          true);
  private static final SubCapSpec MW01882S =
      SubCapSpec.offering(
          "MW01882S",
          "OpenShift Streams for Apache Kafka",
          null,
          null,
          null,
          null,
          ServiceLevel.STANDARD,
          Usage.DEVELOPMENT_TEST,
          false);

  private enum Org {
    STANDARD("711497");

    private final String orgId;

    Org(String orgId) {
      this.orgId = orgId;
    }

    public String orgId() {
      return orgId;
    }
  }

  /**
   * Creates a list of capacities that can be returned by a mock SubscriptionCapacityRepository.
   *
   * @param org The organization that owns the capacities
   * @param specs specifies what sub capacities to return
   * @return a list of subscription capacity views
   */
  private List<Subscription> givenSubscriptions(Org org, SubCapSpec... specs) {
    return Arrays.stream(specs).map(s -> s.createSubscription(org)).toList();
  }

  @Test
  void testGetSkuCapacityReportSingleSub() {
    // Given an org with one active sub with a quantity of 1
    Sub expectedSub = Sub.sub("1234", MW01882.sku, "1235", 1);
    List<Subscription> givenSubs = givenSubscriptions(Org.STANDARD, MW01882.withSub(expectedSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            RHOSAK, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and appropriate
    // quantity and capacities.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(MW01882.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(1, actualItem.getQuantity(), "Incorrect quantity");
    assertEquals(
        MW01882.serviceLevel,
        ServiceLevel.fromString(actualItem.getServiceLevel().toString()),
        "Incorrect Service Level");
    assertEquals(
        MW01882.usage, Usage.fromString(actualItem.getUsage().toString()), "Incorrect Usage");
    assertSubscription(expectedSub, actualItem.getSubscriptions().get(0));
  }

  @Test
  void testGetSkuCapacityReportMultipleSubsSameSku() {
    // Given an org with two active subs with different quantities for the same SKU,
    // and the subs have different ending dates,
    Sub expectedNewerSub = Sub.sub("1234", MW01882.sku, "1235", 1, BillingProvider.RED_HAT, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", MW01882.sku, "1237", 2, BillingProvider.RED_HAT, 6, 6);

    List<Subscription> givenSubs =
        givenSubscriptions(
            Org.STANDARD, MW01882.withSub(expectedNewerSub), MW01882.withSub(expectedOlderSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            RHOSAK, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the subs and appropriate
    // quantity and capacities.
    assertEquals(
        1,
        actual.getData().size(),
        "Both subs are for same SKU so should collect into one capacity item.");
    SkuCapacity actualItem = actual.getData().get(0);
    assertEquals(MW01882.sku, actualItem.getSku(), "Wrong SKU");
    assertEquals(
        3, actualItem.getQuantity(), "Item should contain the sum of all subs' quantities");

    SkuCapacitySubscription actualSub = actualItem.getSubscriptions().get(1);
    assertSubscription(expectedOlderSub, actualSub);

    actualSub = actualItem.getSubscriptions().get(0);
    assertSubscription(expectedNewerSub, actualSub);
  }

  @Test
  void testGetSkuCapacityReportDifferentSkus() {
    // Given an org with two active subs with different quantities for different SKUs,
    // and the subs have different ending dates,
    Sub expectedNewerSub = Sub.sub("1234", MW01882.sku, "1235", 4, BillingProvider.RED_HAT, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", MW01882RN.sku, "1237", 5, BillingProvider.RED_HAT, 6, 6);

    List<Subscription> givenSubs =
        givenSubscriptions(
            Org.STANDARD, MW01882.withSub(expectedNewerSub), MW01882RN.withSub(expectedOlderSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    // When requesting a SKU capacity report for the eng product, sorted by SKU
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            RHOSAK,
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
    assertEquals(MW01882.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        4,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertSubscription(expectedNewerSub, actualItem.getSubscriptions().get(0));

    actualItem = actual.getData().get(1);
    assertEquals(MW01882RN.sku, actualItem.getSku(), "Wrong SKU. (Incorrect ordering of SKUs?)");
    assertEquals(
        5,
        actualItem.getQuantity(),
        "Sub quantity should come from only sub(s) providing the SKU.");
    assertSubscription(expectedOlderSub, actualItem.getSubscriptions().get(0));
  }

  @Test
  void testGetSkuCapacityReportNoSub() {
    // Given an org with no active subs,
    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(Collections.emptyList());

    // When requesting a SKU capacity report for an eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            RHOSAK, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains no inventory items.
    assertEquals(0, actual.getData().size(), "An empty inventory list should be returned.");
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {
    Sub expectedNewerSub = Sub.sub("1234", MW01882.sku, "1235", 1, BillingProvider.RED_HAT, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", MW01882.sku, "1237", 2, BillingProvider.RED_HAT, 6, 6);

    List<Subscription> givenSubs =
        givenSubscriptions(
            Org.STANDARD, MW01882.withSub(expectedNewerSub), MW01882.withSub(expectedOlderSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    SkuCapacityReport report =
        subscriptionTableController.capacityReportBySku(
            RHOSAK,
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
    Sub expectedNewerSub = Sub.sub("1234", MW01882.sku, "1235", 1, BillingProvider.RED_HAT, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", MW01882.sku, "1237", 2, BillingProvider.RED_HAT, 6, 6);

    List<Subscription> givenSubs =
        givenSubscriptions(
            Org.STANDARD, MW01882.withSub(expectedNewerSub), MW01882.withSub(expectedOlderSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    SkuCapacityReport reportForMatchingSLA =
        subscriptionTableController.capacityReportBySku(
            RHOSAK,
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
    Sub expectedNewerSub = Sub.sub("1234", MW01882.sku, "1235", 1, BillingProvider.RED_HAT, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", MW01882.sku, "1237", 2, BillingProvider.RED_HAT, 6, 6);

    List<Subscription> givenSubs =
        givenSubscriptions(
            Org.STANDARD, MW01882.withSub(expectedNewerSub), MW01882.withSub(expectedOlderSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    SkuCapacityReport reportForMatchingUsage =
        subscriptionTableController.capacityReportBySku(
            RHOSAK,
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
  void testGetSkuCapacityReportUnlimitedQuantity() {
    // Given an org with one active sub with a quantity of 1 and has an eng product with unlimited
    // usage.
    Sub expectedSub = Sub.sub("1234", MW01882RN.sku, "1235", 1);

    List<Subscription> givenSubs = givenSubscriptions(Org.STANDARD, MW01882RN.withSub(expectedSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            RHOSAK, null, null, null, null, null, null, null, null, null, null);

    // Then the report contains a single inventory item containing the sub and HasInfiniteQuantity
    // should be true.
    assertEquals(1, actual.getData().size(), "Wrong number of items returned");
    SkuCapacity actualItem = actual.getData().get(0);
    assertTrue(actualItem.getHasInfiniteQuantity(), "HasInfiniteQuantity should be true");
    assertEquals(actualItem.getSku() + OFFERING_DESCRIPTION_SUFFIX, actualItem.getProductName());
  }

  @Test
  void testOnDemandSkuPopulatesNextEvent() {
    // Given an org with one active sub with a quantity of 1 and has an eng product with unlimited
    // usage.
    Sub expectedSub = Sub.sub("1234", MW01882RN.sku, "1235", 1);

    List<Subscription> givenSubs = givenSubscriptions(Org.STANDARD, MW01882RN.withSub(expectedSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    // When requesting a SKU capacity report for the eng product,
    SkuCapacityReport actual =
        subscriptionTableController.capacityReportBySku(
            RHOSAK, null, null, null, null, null, null, null, null, null, null);
    SkuCapacity actualItem = actual.getData().get(0);

    // Then the report should contain end date of the contributing subscription
    assertEquals(
        SubscriptionEventType.END, actualItem.getNextEventType(), "Wrong upcoming event type");
    assertEquals(expectedSub.end, actualItem.getNextEventDate(), "Wrong upcoming event date");
  }

  @Test
  void testShouldGetUniqueResultPerBillingProviderSameSku() {
    Sub expectedNewerSub = Sub.sub("1234", MW01882.sku, "1235", 1, BillingProvider.RED_HAT, 5, 7);
    Sub expectedOlderSub = Sub.sub("1236", MW01882.sku, "1237", 2, BillingProvider.AWS, 6, 6);

    List<Subscription> givenSubs =
        givenSubscriptions(
            Org.STANDARD, MW01882.withSub(expectedNewerSub), MW01882.withSub(expectedOlderSub));

    when(subscriptionRepository.findByCriteria(any(), any())).thenReturn(givenSubs);

    SkuCapacityReport reportForMatchingUsage =
        subscriptionTableController.capacityReportBySku(
            RHOSAK,
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
    assertEquals(2, reportForMatchingUsage.getData().size());
    var billingProvidersReturned =
        reportForMatchingUsage.getData().stream().map(SkuCapacity::getBillingProvider).toList();
    assertTrue(billingProvidersReturned.contains(BillingProviderType.AWS));
    assertTrue(billingProvidersReturned.contains(BillingProviderType.RED_HAT));
  }

  private static void assertSubscription(Sub expectedSub, SkuCapacitySubscription actual) {
    assertEquals(expectedSub.id, actual.getId(), "Wrong Subscription ID");
    assertEquals(expectedSub.number, actual.getNumber(), "Wrong Subscription Number");
  }

  /** A very stripped-down and immutable form of a Subscription used for test data. */
  private static class Sub {
    private final String id;
    private final String sku;
    private final String number;
    private final Integer quantity;
    private final BillingProvider billingProvider;
    private final OffsetDateTime start;
    private final OffsetDateTime end;

    private Sub(
        String id,
        String sku,
        String number,
        Integer quantity,
        BillingProvider billingProvider,
        OffsetDateTime start,
        OffsetDateTime end) {
      this.id = id;
      this.sku = sku;
      this.number = number;
      this.quantity = quantity;
      this.billingProvider = billingProvider;
      this.start = start;
      this.end = end;
    }

    public static Sub sub(String id, String sku, String number, Integer quantity) {
      return sub(id, sku, number, quantity, BillingProvider.RED_HAT, 6, 6);
    }

    /* Also specifies the sub active timeframe, relative to now. */
    public static Sub sub(
        String id,
        String sku,
        String number,
        Integer quantity,
        BillingProvider billingProvider,
        int startMonthsAgo,
        int endMonthsAway) {
      OffsetDateTime subStart = OffsetDateTime.now().minusMonths(startMonthsAgo);
      OffsetDateTime subEnd = subStart.plusMonths(startMonthsAgo + endMonthsAway);

      return new Sub(id, sku, number, quantity, billingProvider, subStart, subEnd);
    }
  }

  /** Immutable, reusable item to generate SubscriptionCapacityViews as tests need them. */
  private static class SubCapSpec {
    private final Sub sub;

    // Fields in this section specify the Offering used in the SubscriptionCapacity.
    private final String sku;
    private final String productName;
    private final Integer sockets;
    private final Integer cores;
    private final Integer hypervisorSockets;
    private final Integer hypervisorCores;
    private final ServiceLevel serviceLevel;
    private final Usage usage;
    private final boolean hasUnlimitedUsage;

    private SubCapSpec(
        String sku,
        String productName,
        Integer sockets,
        Integer cores,
        Integer hypervisorSockets,
        Integer hypervisorCores,
        ServiceLevel serviceLevel,
        Usage usage,
        boolean hasUnlimitedUsage,
        Sub sub) {
      this.sku = sku;
      this.productName = productName;
      this.sockets = sockets;
      this.cores = cores;
      this.hypervisorSockets = hypervisorSockets;
      this.hypervisorCores = hypervisorCores;
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
        Integer sockets,
        Integer cores,
        Integer hypervisorSockets,
        Integer hypervisorCores,
        ServiceLevel serviceLevel,
        Usage usage,
        Boolean hasUnlimitedUsage) {
      return new SubCapSpec(
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

    /*
    Returns a new Subscription Capacity Specification but with the subscription information added.
     */
    public SubCapSpec withSub(Sub sub) {
      return new SubCapSpec(
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

    public Subscription createSubscription(Org org) {
      return Subscription.builder()
          .orgId(org.orgId())
          .subscriptionId(sub.id)
          .quantity(sub.quantity)
          .subscriptionNumber(sub.number)
          .billingProvider(sub.billingProvider)
          .startDate(sub.start)
          .endDate(sub.end)
          .offering(toOffering())
          .build();
    }

    private Offering toOffering() {
      return Offering.builder()
          .sku(this.sku)
          .productName(this.productName)
          .serviceLevel(this.serviceLevel)
          .usage(this.usage)
          .hasUnlimitedUsage(this.hasUnlimitedUsage)
          .description(this.sku + OFFERING_DESCRIPTION_SUFFIX)
          .build();
    }
  }
}
