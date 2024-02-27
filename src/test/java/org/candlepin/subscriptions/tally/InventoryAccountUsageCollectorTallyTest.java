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

import static org.candlepin.subscriptions.tally.InventoryAccountUsageCollector.HBI_INSTANCE_TYPE;
import static org.candlepin.subscriptions.tally.InventoryAccountUsageCollector.createKeyCombinations;
import static org.candlepin.subscriptions.tally.InventoryAccountUsageCollector.populateHostFieldsFromHbi;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createGuest;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createHypervisor;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createRhsmHost;
import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.createSystemProfileHost;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertHypervisorTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertPhysicalTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertTotalsCalculation;
import static org.candlepin.subscriptions.tally.collector.Assertions.assertVirtualTotalsCalculation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountServiceInventoryRepository;
import org.candlepin.subscriptions.db.HostTallyBucketRepository;
import org.candlepin.subscriptions.db.model.AccountBucketTally;
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.AccountServiceInventoryId;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.HostBucketKey;
import org.candlepin.subscriptions.db.model.HostTallyBucket;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.InventoryDatabaseOperations;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.collector.ProductUsageCollectorFactory;
import org.candlepin.subscriptions.tally.facts.FactNormalizer;
import org.candlepin.subscriptions.tally.facts.NormalizedFacts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class InventoryAccountUsageCollectorTallyTest {
  private static final String TEST_PRODUCT = "RHEL for x86";
  public static final Integer TEST_PRODUCT_ID = 69;
  private static final String NON_RHEL = "Satellite Server";
  public static final Integer NON_RHEL_PRODUCT_ID = 250;

  public static final Set<String> RHEL_PRODUCTS = new HashSet<>(List.of(TEST_PRODUCT));
  public static final Set<String> NON_RHEL_PRODUCTS = new HashSet<>(List.of(NON_RHEL));
  private static final String BILLING_ACCOUNT_ID_ANY = "_ANY";
  public static final String ORG_ID = "org123";

  @MockBean private InventoryRepository inventoryRepo;
  @MockBean private HostTallyBucketRepository hostBucketRepository;
  @MockBean private AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired private InventoryAccountUsageCollector collector;
  @Autowired private InventoryDatabaseOperations inventory;
  @Autowired private FactNormalizer factNormalizer;
  @Autowired private ApplicationProperties props;

  @Test
  void hypervisorCountsIgnoredForNonRhelProduct() {
    InventoryHostFacts hypervisor = createHypervisor(ORG_ID, NON_RHEL_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(hypervisor));
    whenProcessHost(NON_RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ORG_ID, NON_RHEL, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ORG_ID, NON_RHEL, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(NON_RHEL)).getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void hypervisorTotalsForRHEL() {
    InventoryHostFacts hypervisor = createHypervisor(ORG_ID, TEST_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(hypervisor));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    // no guests running RHEL means no hypervisor total...
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.HYPERVISOR));
    // hypervisor itself gets counted
    checkPhysicalTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
  }

  @Test
  void guestWithKnownHypervisorNotAddedToTotalsForRHEL() {
    InventoryHostFacts guest = createGuest("hyper-1", ORG_ID, TEST_PRODUCT_ID);
    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(guest.getHypervisorUuid(), guest.getHypervisorUuid());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(guest));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // Calculation expected to be empty because there were no buckets created during collection.
    assertEquals(0, calc.getProducts().size());
    assertEquals(0, calc.getKeys().size());
  }

  @Test
  void guestUnknownHypervisorTotalsForRHEL() {
    InventoryHostFacts guest = createGuest(null, ORG_ID, TEST_PRODUCT_ID);
    guest.setSystemProfileCoresPerSocket(4);
    guest.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(guest.getHypervisorUuid(), null);
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(guest));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    checkTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 1, 1);
    checkVirtualTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 1, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.PHYSICAL));
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.HYPERVISOR));
  }

  @Test
  void physicalSystemTotalsForRHEL() {
    List<Integer> products = List.of(TEST_PRODUCT_ID);

    InventoryHostFacts host = createRhsmHost(ORG_ID, products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    mockReportedHypervisors(ORG_ID, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(host));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() {
    String orgId1 = "O1";
    String orgId2 = "O2";

    List<Integer> products = List.of(TEST_PRODUCT_ID);

    InventoryHostFacts host1 = createRhsmHost(orgId1, products, "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(4);

    InventoryHostFacts host2 = createRhsmHost(orgId1, products, "", OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(2);
    host2.setSystemProfileSockets(4);

    InventoryHostFacts host3 = createRhsmHost(orgId2, products, "", OffsetDateTime.now());
    host3.setSystemProfileCoresPerSocket(3);
    host3.setSystemProfileSockets(2);

    mockReportedHypervisors(List.of(orgId1, orgId2), new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(orgId1)), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of(orgId2)), anyInt())).thenReturn(Stream.of(host3));

    whenProcessHost(RHEL_PRODUCTS, orgId1);
    whenProcessHost(RHEL_PRODUCTS, orgId2);

    ArgumentCaptor<AccountServiceInventory> accountService =
        ArgumentCaptor.forClass(AccountServiceInventory.class);
    verify(accountServiceInventoryRepository, times(2)).save(accountService.capture());
    Map<String, AccountServiceInventory> inventories =
        accountService.getAllValues().stream()
            .collect(Collectors.toMap(AccountServiceInventory::getOrgId, Function.identity()));
    assertTrue(inventories.containsKey(orgId1));
    assertTrue(inventories.containsKey(orgId2));

    when(hostBucketRepository.tallyHostBuckets(orgId1, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(inventories.get(orgId1)));
    when(hostBucketRepository.tallyHostBuckets(orgId2, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(inventories.get(orgId2)));

    AccountUsageCalculation a1Calc = collector.tally(orgId1);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, orgId1, TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = collector.tally(orgId2);
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, orgId2, TEST_PRODUCT, 6, 2, 1);
  }

  @Test
  void testTallyForMultipleSlas() {
    InventoryHostFacts host1 =
        createRhsmHost(
            ORG_ID, TEST_PRODUCT_ID.toString(), ServiceLevel.STANDARD, "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(6);

    InventoryHostFacts host2 =
        createRhsmHost(
            ORG_ID, TEST_PRODUCT_ID.toString(), ServiceLevel.PREMIUM, "", OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(1);
    host2.setSystemProfileSockets(10);

    mockReportedHypervisors(ORG_ID, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(host1, host2));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation a1Calc = collector.tally(ORG_ID);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, ORG_ID, TEST_PRODUCT, 16, 16, 2);
    checkTotalsCalculation(a1Calc, ORG_ID, TEST_PRODUCT, ServiceLevel._ANY, 16, 16, 2);
    checkTotalsCalculation(a1Calc, ORG_ID, TEST_PRODUCT, ServiceLevel.STANDARD, 6, 6, 1);
    checkTotalsCalculation(a1Calc, ORG_ID, TEST_PRODUCT, ServiceLevel.PREMIUM, 10, 10, 1);
  }

  @Test
  void testTallyForMultipleUsages() {
    InventoryHostFacts host1 =
        createRhsmHost(
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

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation a1Calc = collector.tally(ORG_ID);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, ORG_ID, TEST_PRODUCT, 16, 16, 2);
    checkTotalsCalculation(
        a1Calc,
        ORG_ID,
        TEST_PRODUCT,
        ServiceLevel.EMPTY,
        Usage._ANY,
        BillingProvider._ANY,
        BILLING_ACCOUNT_ID_ANY,
        16,
        16,
        2);
    checkTotalsCalculation(
        a1Calc,
        ORG_ID,
        TEST_PRODUCT,
        ServiceLevel.EMPTY,
        Usage.DEVELOPMENT_TEST,
        BillingProvider._ANY,
        BILLING_ACCOUNT_ID_ANY,
        6,
        6,
        1);
    checkTotalsCalculation(
        a1Calc,
        ORG_ID,
        TEST_PRODUCT,
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
    String orgId1 = "O1";
    String orgId2 = "O2";

    InventoryHostFacts host1 =
        createSystemProfileHost(orgId1, List.of(TEST_PRODUCT_ID), 1, 4, OffsetDateTime.now());
    InventoryHostFacts host2 =
        createSystemProfileHost(orgId1, List.of(TEST_PRODUCT_ID), 2, 4, OffsetDateTime.now());
    InventoryHostFacts host3 =
        createSystemProfileHost(orgId2, List.of(TEST_PRODUCT_ID), 2, 6, OffsetDateTime.now());

    mockReportedHypervisors(List.of(orgId1, orgId2), new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(orgId1)), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of(orgId2)), anyInt())).thenReturn(Stream.of(host3));

    whenProcessHost(RHEL_PRODUCTS, orgId1);
    whenProcessHost(RHEL_PRODUCTS, orgId2);

    ArgumentCaptor<AccountServiceInventory> accountService =
        ArgumentCaptor.forClass(AccountServiceInventory.class);
    verify(accountServiceInventoryRepository, times(2)).save(accountService.capture());
    Map<String, AccountServiceInventory> inventories =
        accountService.getAllValues().stream()
            .collect(Collectors.toMap(AccountServiceInventory::getOrgId, Function.identity()));
    assertTrue(inventories.containsKey(orgId1));
    assertTrue(inventories.containsKey(orgId2));

    when(hostBucketRepository.tallyHostBuckets(orgId1, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(inventories.get(orgId1)));
    when(hostBucketRepository.tallyHostBuckets(orgId2, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(inventories.get(orgId2)));

    AccountUsageCalculation a1Calc = collector.tally(orgId1);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, orgId1, TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = collector.tally(orgId2);
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, orgId2, TEST_PRODUCT, 12, 6, 1);
  }

  @Test
  void testCalculationDoesNotIncludeHostWhenProductDoesntMatch() {
    InventoryHostFacts h1 =
        createRhsmHost(ORG_ID, List.of(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    h1.setSystemProfileCoresPerSocket(4);
    h1.setSystemProfileSockets(2);

    InventoryHostFacts h2 = createRhsmHost(ORG_ID, List.of(32), "", OffsetDateTime.now());
    h2.setSystemProfileCoresPerSocket(12);
    h2.setSystemProfileSockets(14);

    mockReportedHypervisors(ORG_ID, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(h1, h2));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation accountCalc = collector.tally(ORG_ID);
    assertEquals(1, accountCalc.getProducts().size());
    checkTotalsCalculation(accountCalc, ORG_ID, TEST_PRODUCT, 8, 2, 1);
  }

  @Test
  void testTallyCoresAndSocketsOfRhelForPhysicalSystems() {
    String orgId1 = "O1";
    String orgId2 = "O2";

    List<String> orgIds = List.of(orgId1, orgId2);

    InventoryHostFacts host1 =
        createRhsmHost(orgId1, List.of(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(4);

    InventoryHostFacts host2 = createHypervisor(orgId1, TEST_PRODUCT_ID);
    host2.setSystemProfileCoresPerSocket(2);
    host2.setSystemProfileSockets(4);

    InventoryHostFacts host3 =
        createRhsmHost(orgId2, List.of(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    host3.setSystemProfileCoresPerSocket(5);
    host3.setSystemProfileSockets(1);

    InventoryHostFacts host4 = createHypervisor(orgId2, TEST_PRODUCT_ID);
    host4.setSystemProfileCoresPerSocket(5);
    host4.setSystemProfileSockets(1);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(host2.getSubscriptionManagerId(), null);
    expectedHypervisorMap.put(host4.getSubscriptionManagerId(), null);
    mockReportedHypervisors(orgIds, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(orgId1)), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of(orgId2)), anyInt())).thenReturn(Stream.of(host3, host4));

    whenProcessHost(RHEL_PRODUCTS, orgId1);
    whenProcessHost(RHEL_PRODUCTS, orgId2);

    ArgumentCaptor<AccountServiceInventory> accountService =
        ArgumentCaptor.forClass(AccountServiceInventory.class);
    verify(accountServiceInventoryRepository, times(2)).save(accountService.capture());
    Map<String, AccountServiceInventory> inventories =
        accountService.getAllValues().stream()
            .collect(Collectors.toMap(AccountServiceInventory::getOrgId, Function.identity()));
    assertTrue(inventories.containsKey(orgId1));
    assertTrue(inventories.containsKey(orgId2));

    when(hostBucketRepository.tallyHostBuckets(orgId1, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(inventories.get(orgId1)));
    when(hostBucketRepository.tallyHostBuckets(orgId2, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(inventories.get(orgId2)));

    AccountUsageCalculation a1Calc = collector.tally(orgId1);
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, orgId1, TEST_PRODUCT, 12, 8, 2);
    checkPhysicalTotalsCalculation(a1Calc, orgId1, TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = collector.tally(orgId2);
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, orgId2, TEST_PRODUCT, 10, 4, 2);
    checkPhysicalTotalsCalculation(a2Calc, orgId2, TEST_PRODUCT, 10, 4, 2);
  }

  @Test
  void testHypervisorCalculationsWhenMapped() {
    InventoryHostFacts hypervisor = createHypervisor(ORG_ID, TEST_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), ORG_ID, TEST_PRODUCT_ID);
    guest1.setSystemProfileCoresPerSocket(4);
    guest1.setSystemProfileSockets(3);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), ORG_ID, TEST_PRODUCT_ID);
    guest2.setSystemProfileCoresPerSocket(4);
    guest2.setSystemProfileSockets(2);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // odd sockets are rounded up for hypervisor.
    // hypervisor gets counted twice - once for itself, once for the guests
    checkTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 24, 8, 2);
    checkHypervisorTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
  }

  @Test
  void testHypervisorCalculationsWhenMappedWithNoProductsOnHypervisor() {
    InventoryHostFacts hypervisor = createHypervisor(ORG_ID, null);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), ORG_ID, TEST_PRODUCT_ID);
    guest1.setSystemProfileCoresPerSocket(4);
    guest1.setSystemProfileSockets(3);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), ORG_ID, TEST_PRODUCT_ID);
    guest2.setSystemProfileCoresPerSocket(4);
    guest2.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // odd sockets are rounded up for hypervisor.
    checkTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkHypervisorTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.PHYSICAL));
  }

  @Test
  void testGuestCountIsTrackedOnHost() {
    InventoryHostFacts hypervisor = createHypervisor(ORG_ID, TEST_PRODUCT_ID);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), ORG_ID, TEST_PRODUCT_ID);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), ORG_ID, TEST_PRODUCT_ID);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(ORG_ID, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);

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
  void accountsWithNullInventoryIdFiltered() {
    List<Integer> products = List.of(TEST_PRODUCT_ID);
    InventoryHostFacts host = createRhsmHost(ORG_ID, products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);

    mockReportedHypervisors(ORG_ID, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(host));

    whenProcessHost(RHEL_PRODUCTS, ORG_ID);
    mockBucketRepositoryFromAccountService();

    AccountUsageCalculation calc = collector.tally(ORG_ID);
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, ORG_ID, TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void ensureStaleHostsAreDeleted() {
    List<Integer> products = List.of(TEST_PRODUCT_ID);
    InventoryHostFacts host = createRhsmHost(ORG_ID, products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    Host orig = new Host(host.getInventoryId().toString(), "insights1", host.getOrgId(), null);
    orig.setInstanceId(host.getInventoryId().toString());
    Host noLongerReported = new Host("i2-inventory-id", "insights2", host.getOrgId(), null);
    noLongerReported.setInstanceId("i2");

    AccountServiceInventory accountServiceInventory =
        new AccountServiceInventory(ORG_ID, "HBI_HOST");
    accountServiceInventory.getServiceInstances().put(host.getInventoryId().toString(), orig);
    accountServiceInventory.getServiceInstances().put("i2", noLongerReported);

    when(accountServiceInventoryRepository.findById(
            AccountServiceInventoryId.builder().orgId(ORG_ID).serviceType("HBI_HOST").build()))
        .thenReturn(Optional.of(accountServiceInventory));

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), any())).thenReturn(Stream.of(host));

    whenProcessHost(accountServiceInventory, RHEL_PRODUCTS, ORG_ID);

    assertEquals(1, accountServiceInventory.getServiceInstances().size());
  }

  private void checkTotalsCalculation(
      AccountUsageCalculation calc,
      String owner,
      String product,
      int cores,
      int sockets,
      int instances) {
    checkTotalsCalculation(calc, owner, product, ServiceLevel._ANY, cores, sockets, instances);
  }

  private void checkTotalsCalculation(
      AccountUsageCalculation calc,
      String owner,
      String product,
      ServiceLevel serviceLevel,
      int cores,
      int sockets,
      int instances) {

    checkTotalsCalculation(
        calc,
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
      String owner,
      String product,
      ServiceLevel serviceLevel,
      Usage usage,
      BillingProvider billingProvider,
      String billingAccountId,
      int cores,
      int sockets,
      int instances) {
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
      String owner,
      String product,
      int physicalCores,
      int physicalSockets,
      int physicalInstances) {
    assertEquals(owner, calc.getOrgId());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertPhysicalTotalsCalculation(prodCalc, physicalSockets, physicalCores, physicalInstances);
  }

  private void checkVirtualTotalsCalculation(
      AccountUsageCalculation calc,
      String orgId,
      String product,
      int cores,
      int sockets,
      int instances) {
    assertEquals(orgId, calc.getOrgId());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertVirtualTotalsCalculation(prodCalc, sockets, cores, instances);
  }

  private void checkHypervisorTotalsCalculation(
      AccountUsageCalculation calc,
      String owner,
      String product,
      int hypCores,
      int hypSockets,
      int hypInstances) {
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
    Builder<Object[]> streamBuilder = Stream.builder();
    for (Entry<String, String> entry : expectedHypervisorMap.entrySet()) {
      streamBuilder.accept(new Object[] {entry.getKey(), entry.getValue()});
    }
    when(inventoryRepo.getReportedHypervisors(orgIds)).thenReturn(streamBuilder.build());
  }

  // Set up the HostBucketRepository with data that is created from the saved Host's
  // Buckets to simulate buckets that exist in the DB.
  private void mockBucketRepositoryFromAccountService() {
    ArgumentCaptor<AccountServiceInventory> accountService =
        ArgumentCaptor.forClass(AccountServiceInventory.class);
    verify(accountServiceInventoryRepository).save(accountService.capture());
    assertNotNull(accountService.getValue());
    when(hostBucketRepository.tallyHostBuckets(ORG_ID, HBI_INSTANCE_TYPE))
        .thenReturn(getTalliesFromAccountService(accountService.getValue()));
  }

  private Stream<AccountBucketTally> getTalliesFromAccountService(
      AccountServiceInventory inventory) {
    // Simulate what the tally query will return.
    Collection<Host> hosts = inventory.getServiceInstances().values();

    Map<UsageCalculation.Key, Map<HardwareMeasurementType, AccountBucketTally>> tallyResults =
        new HashMap<>();

    for (Host host : hosts) {
      for (HostTallyBucket bucket : host.getBuckets()) {
        HostBucketKey bucketKey = bucket.getKey();
        UsageCalculation.Key usageKey =
            new UsageCalculation.Key(
                bucketKey.getProductId(),
                bucketKey.getSla(),
                bucketKey.getUsage(),
                bucketKey.getBillingProvider(),
                bucketKey.getBillingAccountId());
        Map<HardwareMeasurementType, AccountBucketTally> bucketTallyMap =
            tallyResults.computeIfAbsent(usageKey, k -> new EnumMap(HardwareMeasurementType.class));

        TestAccountBucketTally tally =
            (TestAccountBucketTally)
                bucketTallyMap.computeIfAbsent(
                    bucket.getMeasurementType(),
                    k ->
                        new TestAccountBucketTally(
                            bucketKey.getProductId(),
                            bucket.getMeasurementType(),
                            bucketKey.getSla(),
                            bucketKey.getUsage(),
                            bucketKey.getBillingProvider(),
                            bucketKey.getBillingAccountId(),
                            0.0,
                            0.0,
                            0.0));
        tally.setCores(tally.getCores() + bucket.getCores());
        tally.setSockets(tally.getSockets() + bucket.getSockets());
        tally.setInstances(tally.getInstances() + 1.0);
      }
    }

    return tallyResults.values().stream().map(Map::values).flatMap(Collection::stream);
  }

  private AccountServiceInventory givenAccountServiceInventory(String orgId) {
    return new AccountServiceInventory(
        AccountServiceInventoryId.builder().orgId(orgId).serviceType(HBI_INSTANCE_TYPE).build());
  }

  private void whenProcessHost(Set<String> products, String orgId) {
    whenProcessHost(givenAccountServiceInventory(orgId), products, orgId);
  }

  private void whenProcessHost(
      AccountServiceInventory accountServiceInventory, Set<String> products, String orgId) {
    Map<String, Host> inventoryHostMap =
        accountServiceInventory.getServiceInstances().values().stream()
            .filter(host -> host.getInventoryId() != null)
            .collect(Collectors.toMap(Host::getInventoryId, Function.identity()));
    OrgHostsData orgHostsData = new OrgHostsData(orgId);
    Map<String, Set<HostBucketKey>> hostSeenBucketKeysLookup = new HashMap<>();

    orgHostsData.addReportedHypervisors(inventory);

    inventory.processHost(
        orgId,
        props.getCullingOffsetDays(),
        hostFacts -> {
          NormalizedFacts facts = factNormalizer.normalize(hostFacts, orgHostsData);
          Host existingHost = inventoryHostMap.remove(hostFacts.getInventoryId().toString());
          Host host;

          if (existingHost == null) {
            host = new Host();
            host.setInstanceType(HBI_INSTANCE_TYPE);
            populateHostFieldsFromHbi(host, hostFacts, facts);
          } else {
            host = existingHost;
            populateHostFieldsFromHbi(host, hostFacts, facts);
          }
          orgHostsData.addHostWithNormalizedFacts(host, facts);

          Set<HostBucketKey> seenBucketKeys =
              hostSeenBucketKeysLookup.computeIfAbsent(host.getInstanceId(), h -> new HashSet<>());

          if (facts.isHypervisor()) {
            orgHostsData.addHypervisorFacts(hostFacts.getSubscriptionManagerId(), facts);
            orgHostsData.addHostToHypervisor(hostFacts.getSubscriptionManagerId(), host);
          } else if (facts.isVirtual() && StringUtils.hasText(facts.getHypervisorUuid())) {
            orgHostsData.incrementGuestCount(host.getHypervisorUuid());
          }

          Set<UsageCalculation.Key> usageKeys =
              createKeyCombinations(
                  products,
                  Set.of(facts.getSla(), ServiceLevel._ANY),
                  Set.of(facts.getUsage(), Usage._ANY),
                  Set.of(BillingProvider._ANY),
                  Set.of("_ANY"));

          // Calculate for each UsageKey
          // review current implementation of default values, and determine if fact normalizer needs
          // to handle billingAcctId & BillingProvider
          for (UsageCalculation.Key key : usageKeys) {
            var product = key.getProductId();
            if (!facts.getProducts().contains(product)) {
              continue;
            }

            try {
              String hypervisorUuid = facts.getHypervisorUuid();
              if (hypervisorUuid != null) {
                orgHostsData.addHypervisorKey(hypervisorUuid, key);
              }
              Optional<HostTallyBucket> appliedBucket =
                  ProductUsageCollectorFactory.get(product).buildBucket(key, facts);
              appliedBucket.ifPresent(
                  bucket -> {
                    // host.addBucket changes bucket.key.hostId, so we do that first; to
                    // avoid mutating the item in the set
                    host.addBucket(bucket);
                    seenBucketKeys.add(bucket.getKey());
                  });
            } catch (Exception e) {
              fail("Unable to collect usage data for host", e);
            }
          }
          // Save the host now that the buckets have been determined. Hypervisor hosts will
          // be persisted once all potential guests have been processed.
          if (!facts.isHypervisor()) {
            accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
          }
        });
    inventoryHostMap.values().stream()
        .map(Host::getInstanceId)
        .forEach(accountServiceInventory.getServiceInstances()::remove);

    // apply data from guests to hypervisor records
    orgHostsData.collectGuestData(hostSeenBucketKeysLookup);
    for (Host host : accountServiceInventory.getServiceInstances().values()) {
      Set<HostBucketKey> seenBucketKeys =
          hostSeenBucketKeysLookup.computeIfAbsent(host.getInstanceId(), h -> new HashSet<>());
      host.getBuckets().removeIf(b -> !seenBucketKeys.contains(b.getKey()));
    }

    var hypervisorHostMap = orgHostsData.hypervisorHostMap();
    if (!hypervisorHostMap.isEmpty()) {
      for (Host host : hypervisorHostMap.values()) {
        accountServiceInventory.getServiceInstances().put(host.getInstanceId(), host);
      }
    }
    accountServiceInventory.setOrgId(orgId);
    accountServiceInventoryRepository.save(accountServiceInventory);
  }
}
