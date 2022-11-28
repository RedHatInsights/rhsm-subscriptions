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
package org.candlepin.subscriptions.tally;

import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createGuest;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createHypervisor;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createRhsmHost;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createSystemProfileHost;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertHypervisorTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertPhysicalTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertVirtualTotalsCalculation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostRepository;
import org.candlepin.subscriptions.db.model.*;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class InventoryAccountUsageCollectorTest {

  private static final String TEST_PRODUCT = "RHEL";
  public static final Integer TEST_PRODUCT_ID = 1;
  private static final String NON_RHEL = "OTHER PRODUCT";
  public static final Integer NON_RHEL_PRODUCT_ID = 2000;

  public static final Set<String> RHEL_PRODUCTS = new HashSet<>(Arrays.asList(TEST_PRODUCT));
  public static final Set<String> NON_RHEL_PRODUCTS = new HashSet<>(Arrays.asList(NON_RHEL));
  private static final String BILLING_ACCOUNT_ID_ANY = "_ANY";

  public static final String ACCOUNT = "foo123";
  public static final String ORG_ID = "org123";

  @MockBean private InventoryRepository inventoryRepo;
  @MockBean private HostRepository hostRepo;
  @MockBean private AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired private InventoryAccountUsageCollector collector;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void hypervisorCountsIgnoredForNonRhelProduct() {

    InventoryHostFacts hypervisor = createHypervisor(ACCOUNT, ORG_ID, NON_RHEL_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor).stream());

    Map<String, AccountUsageCalculation> calcs =
        collector.collect(NON_RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, NON_RHEL, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ACCOUNT, ORG_ID, NON_RHEL, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(NON_RHEL)).getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void hypervisorTotalsForRHEL() {
    InventoryHostFacts hypervisor = createHypervisor(ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    // no guests running RHEL means no hypervisor total...
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.HYPERVISOR));
    // hypervisor itself gets counted
    checkPhysicalTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
  }

  @Test
  void guestWithKnownHypervisorNotAddedToTotalsForRHEL() {
    InventoryHostFacts guest = createGuest("hyper-1", ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(guest.getHypervisorUuid(), guest.getHypervisorUuid());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(guest).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    UsageCalculation productCalc = calc.getCalculation(createUsageKey(TEST_PRODUCT));
    assertNull(productCalc.getTotals(HardwareMeasurementType.TOTAL));
    assertNull(productCalc.getTotals(HardwareMeasurementType.PHYSICAL));
    assertNull(productCalc.getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void guestUnknownHypervisorTotalsForRHEL() {
    InventoryHostFacts guest = createGuest(null, ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    guest.setSystemProfileCoresPerSocket(4);
    guest.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(guest.getHypervisorUuid(), null);
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(guest).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 1, 1);
    checkVirtualTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 1, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.PHYSICAL));
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.HYPERVISOR));
  }

  @Test
  void physicalSystemTotalsForRHEL() {
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);

    InventoryHostFacts host = createRhsmHost(ACCOUNT, ORG_ID, products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    mockReportedHypervisors(ORG_ID, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(host).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() {
    String account1 = "A1";
    String orgId1 = "O1";

    String account2 = "A2";
    String orgId2 = "O2";

    List<String> orgIds = List.of(orgId1, orgId2);
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);

    InventoryHostFacts host1 = createRhsmHost(account1, orgId1, products, "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(4);

    InventoryHostFacts host2 = createRhsmHost(account1, orgId1, products, "", OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(2);
    host2.setSystemProfileSockets(4);

    InventoryHostFacts host3 = createRhsmHost(account2, orgId2, products, "", OffsetDateTime.now());
    host3.setSystemProfileCoresPerSocket(3);
    host3.setSystemProfileSockets(2);

    mockReportedHypervisors(List.of(orgId1, orgId2), new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(orgId1)), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of(orgId2)), anyInt())).thenReturn(Stream.of(host3));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account1, orgId1);
    calcs.putAll(collector.collect(RHEL_PRODUCTS, account2, orgId2));
    assertEquals(2, calcs.size());
    assertThat(calcs, Matchers.hasKey(orgId1));
    assertThat(calcs, Matchers.hasKey(orgId2));

    AccountUsageCalculation a1Calc = calcs.get(orgId1);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, account1, orgId1, "RHEL", 12, 8, 2);

    AccountUsageCalculation a2Calc = calcs.get(orgId2);
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, account2, orgId2, TEST_PRODUCT, 6, 2, 1);
  }

  @Test
  void testTallyForMultipleSlas() {
    InventoryHostFacts host1 =
        createRhsmHost(
            ACCOUNT,
            ORG_ID,
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.STANDARD,
            "",
            OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(6);

    InventoryHostFacts host2 =
        createRhsmHost(
            ACCOUNT,
            ORG_ID,
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.PREMIUM,
            "",
            OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(1);
    host2.setSystemProfileSockets(10);

    mockReportedHypervisors(ORG_ID, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(host1, host2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation a1Calc = calcs.get(ORG_ID);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, ACCOUNT, ORG_ID, "RHEL", 16, 16, 2);
    checkTotalsCalculation(a1Calc, ACCOUNT, ORG_ID, "RHEL", ServiceLevel._ANY, 16, 16, 2);
    checkTotalsCalculation(a1Calc, ACCOUNT, ORG_ID, "RHEL", ServiceLevel.STANDARD, 6, 6, 1);
    checkTotalsCalculation(a1Calc, ACCOUNT, ORG_ID, "RHEL", ServiceLevel.PREMIUM, 10, 10, 1);
  }

  @Test
  void testTallyForMultipleUsages() {
    InventoryHostFacts host1 =
        createRhsmHost(
            ACCOUNT,
            ORG_ID,
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.EMPTY,
            Usage.DEVELOPMENT_TEST,
            "",
            OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(6);

    InventoryHostFacts host2 =
        createRhsmHost(
            ACCOUNT,
            ORG_ID,
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.EMPTY,
            Usage.PRODUCTION,
            "",
            OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(1);
    host2.setSystemProfileSockets(10);

    mockReportedHypervisors(ORG_ID, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(host1, host2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation a1Calc = calcs.get(ORG_ID);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, ACCOUNT, ORG_ID, "RHEL", 16, 16, 2);
    checkTotalsCalculation(
        a1Calc,
        ACCOUNT,
        ORG_ID,
        "RHEL",
        ServiceLevel.EMPTY,
        Usage._ANY,
        BillingProvider._ANY,
        BILLING_ACCOUNT_ID_ANY,
        16,
        16,
        2);
    checkTotalsCalculation(
        a1Calc,
        ACCOUNT,
        ORG_ID,
        "RHEL",
        ServiceLevel.EMPTY,
        Usage.DEVELOPMENT_TEST,
        BillingProvider._ANY,
        BILLING_ACCOUNT_ID_ANY,
        6,
        6,
        1);
    checkTotalsCalculation(
        a1Calc,
        ACCOUNT,
        ORG_ID,
        "RHEL",
        ServiceLevel.EMPTY,
        Usage.PRODUCTION,
        BillingProvider._ANY,
        BILLING_ACCOUNT_ID_ANY,
        10,
        10,
        1);
  }

  @Test
  void testTallyCoresAndSocketsOfRhelViaSystemProfileOnly() {
    String account1 = "A1";
    String orgId1 = "O1";

    String account2 = "A2";
    String orgId2 = "O2";

    List<String> orgIds = List.of(orgId1, orgId2);

    InventoryHostFacts host1 =
        createSystemProfileHost(
            account1, orgId1, Arrays.asList(TEST_PRODUCT_ID), 1, 4, OffsetDateTime.now());
    InventoryHostFacts host2 =
        createSystemProfileHost(
            account1, orgId1, Arrays.asList(TEST_PRODUCT_ID), 2, 4, OffsetDateTime.now());
    InventoryHostFacts host3 =
        createSystemProfileHost(
            account2, orgId2, Arrays.asList(TEST_PRODUCT_ID), 2, 6, OffsetDateTime.now());

    mockReportedHypervisors(List.of(orgId1, orgId2), new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(orgId1)), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of(orgId2)), anyInt())).thenReturn(Stream.of(host3));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account1, orgId1);
    calcs.putAll(collector.collect(RHEL_PRODUCTS, account2, orgId2));
    assertEquals(2, calcs.size());
    assertThat(calcs, Matchers.hasKey(orgId1));
    assertThat(calcs, Matchers.hasKey(orgId2));

    AccountUsageCalculation a1Calc = calcs.get(orgId1);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, account1, orgId1, TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = calcs.get(orgId2);
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, account2, orgId2, TEST_PRODUCT, 12, 6, 1);
  }

  @Test
  void testCalculationDoesNotIncludeHostWhenProductDoesntMatch() {
    InventoryHostFacts h1 =
        createRhsmHost(ACCOUNT, ORG_ID, Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    h1.setSystemProfileCoresPerSocket(4);
    h1.setSystemProfileSockets(2);

    InventoryHostFacts h2 =
        createRhsmHost(ACCOUNT, ORG_ID, Arrays.asList(32), "", OffsetDateTime.now());
    h2.setSystemProfileCoresPerSocket(12);
    h2.setSystemProfileSockets(14);

    mockReportedHypervisors(ORG_ID, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(h1, h2).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation accountCalc = calcs.get(ORG_ID);
    assertEquals(1, accountCalc.getProducts().size());
    checkTotalsCalculation(accountCalc, ACCOUNT, ORG_ID, TEST_PRODUCT, 8, 2, 1);
  }

  @Test
  void throwsISEOnAttemptToCalculateFactsBelongingToADifferentAccountForSameOrgId() {
    InventoryHostFacts h1 =
        createRhsmHost(
            "Account1", ORG_ID, Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    h1.setSystemProfileCoresPerSocket(1);
    h1.setSystemProfileSockets(2);

    InventoryHostFacts h2 =
        createRhsmHost(
            "Account2", ORG_ID, Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());

    mockReportedHypervisors(ORG_ID, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(h1, h2).stream());

    Throwable e =
        assertThrows(
            IllegalStateException.class, () -> collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID));

    String expectedMessage =
        String.format(
            "Attempt to set a different account for an org: %s:%s", "Account1", "Account2");
    assertEquals(expectedMessage, e.getMessage());
  }

  @Test
  void testTallyCoresAndSocketsOfRhelForPhysicalSystems() {
    String account1 = "A1";
    String orgId1 = "O1";

    String account2 = "A2";
    String orgId2 = "O2";

    List<String> orgIds = List.of(orgId1, orgId2);

    InventoryHostFacts host1 =
        createRhsmHost(account1, orgId1, Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(4);

    InventoryHostFacts host2 = createHypervisor(account1, orgId1, TEST_PRODUCT_ID);
    host2.setSystemProfileCoresPerSocket(2);
    host2.setSystemProfileSockets(4);

    InventoryHostFacts host3 =
        createRhsmHost(account2, orgId2, Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    host3.setSystemProfileCoresPerSocket(5);
    host3.setSystemProfileSockets(1);

    InventoryHostFacts host4 = createHypervisor(account2, orgId2, TEST_PRODUCT_ID);
    host4.setSystemProfileCoresPerSocket(5);
    host4.setSystemProfileSockets(1);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(host2.getSubscriptionManagerId(), null);
    expectedHypervisorMap.put(host4.getSubscriptionManagerId(), null);
    mockReportedHypervisors(orgIds, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(orgId1)), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of(orgId2)), anyInt())).thenReturn(Stream.of(host3, host4));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account1, orgId1);
    calcs.putAll(collector.collect(RHEL_PRODUCTS, account2, orgId2));
    assertEquals(2, calcs.size());
    assertThat(calcs, Matchers.hasKey(orgId1));
    assertThat(calcs, Matchers.hasKey(orgId2));

    AccountUsageCalculation a1Calc = calcs.get(orgId1);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, account1, orgId1, TEST_PRODUCT, 12, 8, 2);
    checkPhysicalTotalsCalculation(a1Calc, account1, orgId1, TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = calcs.get(orgId2);
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, account2, orgId2, TEST_PRODUCT, 10, 4, 2);
    checkPhysicalTotalsCalculation(a2Calc, account2, orgId2, TEST_PRODUCT, 10, 4, 2);
  }

  @Test
  void testHypervisorCalculationsWhenMapped() {
    InventoryHostFacts hypervisor = createHypervisor(ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    guest1.setSystemProfileCoresPerSocket(4);
    guest1.setSystemProfileSockets(3);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    guest2.setSystemProfileCoresPerSocket(4);
    guest2.setSystemProfileSockets(2);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    // odd sockets are rounded up for hypervisor.
    // hypervisor gets counted twice - once for itself, once for the guests
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 24, 8, 2);
    checkHypervisorTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
  }

  @Test
  void testHypervisorCalculationsWhenMappedWithNoProductsOnHypervisor() {
    InventoryHostFacts hypervisor = createHypervisor(ACCOUNT, ORG_ID, null);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    guest1.setSystemProfileCoresPerSocket(4);
    guest1.setSystemProfileSockets(3);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), ACCOUNT, ORG_ID, TEST_PRODUCT_ID);
    guest2.setSystemProfileCoresPerSocket(4);
    guest2.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor, guest1, guest2).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    // odd sockets are rounded up for hypervisor.
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkHypervisorTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.PHYSICAL));
  }

  @Test
  void testGuestCountIsTrackedOnHost() {
    InventoryHostFacts hypervisor = createHypervisor(ACCOUNT, ORG_ID, TEST_PRODUCT_ID);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), ACCOUNT, ORG_ID, TEST_PRODUCT_ID);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), ACCOUNT, ORG_ID, TEST_PRODUCT_ID);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    ArgumentCaptor<AccountServiceInventory> accountService =
        ArgumentCaptor.forClass(AccountServiceInventory.class);
    verify(accountServiceInventoryRepository).save(accountService.capture());

    Map<String, Host> savedGuests =
        accountService.getAllValues().stream()
            .map(AccountServiceInventory::getServiceInstances)
            .map(Map::values)
            .flatMap(Collection::stream)
            .filter(h -> h.getHypervisorUuid() != null)
            .collect(Collectors.toMap(Host::getInventoryId, host -> host));
    assertEquals(2, savedGuests.size());
    assertTrue(savedGuests.containsKey(guest1.getInventoryId().toString()));
    assertTrue(savedGuests.containsKey(guest2.getInventoryId().toString()));

    Host savedHypervisor =
        accountService.getAllValues().stream()
            .map(AccountServiceInventory::getServiceInstances)
            .map(Map::values)
            .flatMap(Collection::stream)
            .filter(h -> h.getHypervisorUuid() == null)
            .findFirst()
            .orElseThrow();
    assertEquals(hypervisor.getSubscriptionManagerId(), savedHypervisor.getSubscriptionManagerId());
    assertEquals(2, savedHypervisor.getNumOfGuests().intValue());
  }

  @Test
  void testTotalHosts() {
    Counter counter = meterRegistry.counter("rhsm-subscriptions.tally.hbi_hosts");
    double initialCount = counter.count();

    InventoryHostFacts hypervisor = createHypervisor(ACCOUNT, ORG_ID, TEST_PRODUCT_ID);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertEquals(1, counter.count() - initialCount);
  }

  @Test
  void accountsWithNullInventoryIdFiltered() {
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);
    InventoryHostFacts host = createRhsmHost(ACCOUNT, ORG_ID, products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);

    mockReportedHypervisors(ORG_ID, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Arrays.asList(host).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey(ORG_ID));

    AccountUsageCalculation calc = calcs.get(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ACCOUNT, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void removesDuplicateHostRecords() {
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);
    InventoryHostFacts host = createRhsmHost(ACCOUNT, ORG_ID, products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    Host orig =
        new Host(
            host.getInventoryId().toString(),
            "insights1",
            host.getAccount(),
            host.getOrgId(),
            null);
    orig.setInstanceId(host.getInventoryId().toString());
    Host dupe =
        new Host(
            host.getInventoryId().toString(),
            "insights2",
            host.getAccount(),
            host.getOrgId(),
            null);
    dupe.setInstanceId("i2");

    AccountServiceInventory accountServiceInventory =
        AccountServiceInventory.forOrgIdAndServiceType(ORG_ID, "HBI_HOST");
    accountServiceInventory.getServiceInstances().put(host.getInventoryId().toString(), orig);
    accountServiceInventory.getServiceInstances().put("i2", dupe);

    when(accountServiceInventoryRepository.findById(
            AccountServiceInventoryId.builder().orgId(ORG_ID).serviceType("HBI_HOST").build()))
        .thenReturn(Optional.of(accountServiceInventory));

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), any())).thenReturn(Stream.of(host));

    collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);

    assertEquals(1, accountServiceInventory.getServiceInstances().size());
  }

  private void checkTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String owner,
      String product,
      int cores,
      int sockets,
      int instances) {
    checkTotalsCalculation(
        calc, account, owner, product, ServiceLevel._ANY, cores, sockets, instances);
  }

  private void checkTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String owner,
      String product,
      ServiceLevel serviceLevel,
      int cores,
      int sockets,
      int instances) {

    checkTotalsCalculation(
        calc,
        account,
        owner,
        product,
        serviceLevel,
        Usage._ANY,
        BillingProvider._ANY,
        BILLING_ACCOUNT_ID_ANY,
        cores,
        sockets,
        instances);
  }

  private void checkTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String owner,
      String product,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      int cores,
      int sockets,
      int instances) {
    assertEquals(account, calc.getAccount());
    assertEquals(owner, calc.getOrgId());
    assertTrue(
        calc.containsCalculation(
            createUsageKey(product, serviceLevel, usage, billingProvider, billingAccountId)));

    UsageCalculation prodCalc =
        calc.getCalculation(
            createUsageKey(product, serviceLevel, usage, billingProvider, billingAccountId));

    assertEquals(product, prodCalc.getProductId());
    assertEquals(serviceLevel, prodCalc.getSla());
    assertEquals(billingProvider, prodCalc.getBillingProvider());
    assertEquals(billingAccountId, prodCalc.getBillingAccountId());
    assertTotalsCalculation(prodCalc, sockets, cores, instances);
  }

  private void checkPhysicalTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String owner,
      String product,
      int physicalCores,
      int physicalSockets,
      int physicalInstances) {
    assertEquals(account, calc.getAccount());
    assertEquals(owner, calc.getOrgId());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertPhysicalTotalsCalculation(prodCalc, physicalSockets, physicalCores, physicalInstances);
  }

  private void checkVirtualTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String orgId,
      String product,
      int cores,
      int sockets,
      int instances) {
    assertEquals(account, calc.getAccount());
    assertEquals(orgId, calc.getOrgId());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertVirtualTotalsCalculation(prodCalc, sockets, cores, instances);
  }

  private void checkHypervisorTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String owner,
      String product,
      int hypCores,
      int hypSockets,
      int hypInstances) {
    assertEquals(account, calc.getAccount());
    assertEquals(owner, calc.getOrgId());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertHypervisorTotalsCalculation(prodCalc, hypSockets, hypCores, hypInstances);
  }

  private UsageCalculation.Key createUsageKey(String product) {
    return createUsageKey(product, ServiceLevel._ANY);
  }

  private UsageCalculation.Key createUsageKey(String product, ServiceLevel sla) {
    return new UsageCalculation.Key(
        product, sla, Usage._ANY, BillingProvider._ANY, BILLING_ACCOUNT_ID_ANY);
  }

  private UsageCalculation.Key createUsageKey(
      String product,
      ServiceLevel sla,
      Usage usage,
      BillingProvider billingProvider,
      String billingAcctId) {
    return new UsageCalculation.Key(product, sla, usage, billingProvider, billingAcctId);
  }

  private void mockReportedHypervisors(String orgId, Map<String, String> expectedHypervisorMap) {
    mockReportedHypervisors(List.of(orgId), expectedHypervisorMap);
  }

  private void mockReportedHypervisors(
      List<String> orgIds, Map<String, String> expectedHypervisorMap) {
    Builder streamBuilder = Stream.builder();
    for (Entry<String, String> entry : expectedHypervisorMap.entrySet()) {
      streamBuilder.accept(new Object[] {entry.getKey(), entry.getValue()});
    }
    when(inventoryRepo.getReportedHypervisors(orgIds)).thenReturn(streamBuilder.build());
  }
}
