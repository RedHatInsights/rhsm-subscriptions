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

import static org.candlepin.subscriptions.tally.InventoryHostFactTestHelper.*;
import static org.candlepin.subscriptions.tally.collector.Assertions.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import org.candlepin.subscriptions.db.model.AccountServiceInventory;
import org.candlepin.subscriptions.db.model.AccountServiceInventoryId;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.InventoryRepository;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
public class InventoryAccountUsageCollectorTest {

  private static final String TEST_PRODUCT = "RHEL";
  public static final Integer TEST_PRODUCT_ID = 1;
  private static final String NON_RHEL = "OTHER PRODUCT";
  public static final Integer NON_RHEL_PRODUCT_ID = 2000;

  public static final Set<String> RHEL_PRODUCTS = new HashSet<>(Arrays.asList(TEST_PRODUCT));
  public static final Set<String> NON_RHEL_PRODUCTS = new HashSet<>(Arrays.asList(NON_RHEL));

  @MockBean private BuildProperties buildProperties;
  @MockBean private InventoryRepository inventoryRepo;
  @MockBean private HostRepository hostRepo;
  @MockBean private AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired private InventoryAccountUsageCollector collector;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  public void hypervisorCountsIgnoredForNonRhelProduct() {
    String account = "A1";

    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", NON_RHEL_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(NON_RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, "A1", "O1", NON_RHEL, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, "A1", "O1", NON_RHEL, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(NON_RHEL)).getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  public void hypervisorTotalsForRHEL() {
    String account = "A1";

    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", TEST_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    // no guests running RHEL means no hypervisor total...
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
    // hypervisor itself gets counted
    checkPhysicalTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
  }

  @Test
  public void guestWithKnownHypervisorNotAddedToTotalsForRHEL() {
    String account = "A1";

    InventoryHostFacts guest = createGuest("hyper-1", "A1", "O1", TEST_PRODUCT_ID);
    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(guest.getHypervisorUuid(), guest.getHypervisorUuid());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(guest).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    UsageCalculation productCalc = calc.getCalculation(createUsageKey(TEST_PRODUCT));
    assertNull(productCalc.getTotals(HardwareMeasurementType.TOTAL));
    assertNull(productCalc.getTotals(HardwareMeasurementType.PHYSICAL));
    assertNull(productCalc.getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  public void guestUnknownHypervisorTotalsForRHEL() {
    String account = "A1";
    InventoryHostFacts guest = createGuest(null, "A1", "O1", TEST_PRODUCT_ID);
    guest.setSystemProfileCoresPerSocket(4);
    guest.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(guest.getHypervisorUuid(), null);
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(guest).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 1, 1);
    checkHypervisorTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 1, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.PHYSICAL));
  }

  @Test
  public void physicalSystemTotalsForRHEL() {
    String account = "A1";
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);

    InventoryHostFacts host = createRhsmHost("A1", "O1", products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);
    mockReportedHypervisors(account, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(host).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  public void testTallyCoresAndSocketsOfRhelWhenInventoryFoundForAccount() throws Exception {
    List<String> accounts = List.of("A1", "A2");
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);

    InventoryHostFacts host1 = createRhsmHost("A1", "O1", products, "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(4);

    InventoryHostFacts host2 = createRhsmHost("A1", "O1", products, "", OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(2);
    host2.setSystemProfileSockets(4);

    InventoryHostFacts host3 = createRhsmHost("A2", "O2", products, "", OffsetDateTime.now());
    host3.setSystemProfileCoresPerSocket(3);
    host3.setSystemProfileSockets(2);

    mockReportedHypervisors(accounts, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of("A1")), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of("A2")), anyInt())).thenReturn(Stream.of(host3));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, "A1");
    calcs.putAll(collector.collect(RHEL_PRODUCTS, "A2"));
    assertEquals(2, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));
    assertThat(calcs, Matchers.hasKey("A2"));

    AccountUsageCalculation a1Calc = calcs.get("A1");
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", 12, 8, 2);

    AccountUsageCalculation a2Calc = calcs.get("A2");
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 6, 2, 1);
  }

  @Test
  void testTallyForMultipleSlas() {
    String account = "A1";
    List<Integer> products = Collections.singletonList(TEST_PRODUCT_ID);

    InventoryHostFacts host1 =
        createRhsmHost(
            "A1",
            "O1",
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.STANDARD,
            "",
            OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(6);

    InventoryHostFacts host2 =
        createRhsmHost(
            "A1", "O1", TEST_PRODUCT_ID.toString(), ServiceLevel.PREMIUM, "", OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(1);
    host2.setSystemProfileSockets(10);

    mockReportedHypervisors(account, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Stream.of(host1, host2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation a1Calc = calcs.get("A1");
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", 16, 16, 2);
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", ServiceLevel._ANY, 16, 16, 2);
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", ServiceLevel.STANDARD, 6, 6, 1);
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", ServiceLevel.PREMIUM, 10, 10, 1);
  }

  @Test
  void testTallyForMultipleUsages() {
    String account = "A1";

    InventoryHostFacts host1 =
        createRhsmHost(
            "A1",
            "O1",
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.EMPTY,
            Usage.DEVELOPMENT_TEST,
            "",
            OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(6);

    InventoryHostFacts host2 =
        createRhsmHost(
            "A1",
            "O1",
            TEST_PRODUCT_ID.toString(),
            ServiceLevel.EMPTY,
            Usage.PRODUCTION,
            "",
            OffsetDateTime.now());
    host2.setSystemProfileCoresPerSocket(1);
    host2.setSystemProfileSockets(10);

    mockReportedHypervisors(account, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Stream.of(host1, host2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation a1Calc = calcs.get("A1");
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", 16, 16, 2);
    checkTotalsCalculation(a1Calc, "A1", "O1", "RHEL", ServiceLevel.EMPTY, Usage._ANY, 16, 16, 2);
    checkTotalsCalculation(
        a1Calc, "A1", "O1", "RHEL", ServiceLevel.EMPTY, Usage.DEVELOPMENT_TEST, 6, 6, 1);
    checkTotalsCalculation(
        a1Calc, "A1", "O1", "RHEL", ServiceLevel.EMPTY, Usage.PRODUCTION, 10, 10, 1);
  }

  @Test
  void testTallyCoresAndSocketsOfRhelViaSystemProfileOnly() throws Exception {
    List<String> accounts = List.of("A1", "A2");

    InventoryHostFacts host1 =
        createSystemProfileHost(
            "A1", "O1", Arrays.asList(TEST_PRODUCT_ID), 1, 4, OffsetDateTime.now());
    InventoryHostFacts host2 =
        createSystemProfileHost(
            "A1", "O1", Arrays.asList(TEST_PRODUCT_ID), 2, 4, OffsetDateTime.now());
    InventoryHostFacts host3 =
        createSystemProfileHost(
            "A2", "O2", Arrays.asList(TEST_PRODUCT_ID), 2, 6, OffsetDateTime.now());

    mockReportedHypervisors(accounts, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of("A1")), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of("A2")), anyInt())).thenReturn(Stream.of(host3));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, "A1");
    calcs.putAll(collector.collect(RHEL_PRODUCTS, "A2"));
    assertEquals(2, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));
    assertThat(calcs, Matchers.hasKey("A2"));

    AccountUsageCalculation a1Calc = calcs.get("A1");
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = calcs.get("A2");
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 12, 6, 1);
  }

  @Test
  public void testCalculationDoesNotIncludeHostWhenProductDoesntMatch() throws IOException {
    String account = "A1";

    InventoryHostFacts h1 =
        createRhsmHost("A1", "Owner1", Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    h1.setSystemProfileCoresPerSocket(4);
    h1.setSystemProfileSockets(2);

    InventoryHostFacts h2 =
        createRhsmHost("A1", "Owner1", Arrays.asList(32), "", OffsetDateTime.now());
    h2.setSystemProfileCoresPerSocket(12);
    h2.setSystemProfileSockets(14);

    mockReportedHypervisors(account, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(h1, h2).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation accountCalc = calcs.get("A1");
    assertEquals(1, accountCalc.getProducts().size());
    checkTotalsCalculation(accountCalc, "A1", "Owner1", TEST_PRODUCT, 8, 2, 1);
  }

  @Test
  public void throwsISEOnAttemptToCalculateFactsBelongingToADifferentOwnerForSameAccount()
      throws IOException {
    String account = "A1";

    InventoryHostFacts h1 =
        createRhsmHost("A1", "Owner1", Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    h1.setSystemProfileCoresPerSocket(1);
    h1.setSystemProfileSockets(2);

    InventoryHostFacts h2 =
        createRhsmHost("A1", "Owner2", Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());

    mockReportedHypervisors(account, new HashMap<>());
    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(h1, h2).stream());

    Throwable e =
        assertThrows(IllegalStateException.class, () -> collector.collect(RHEL_PRODUCTS, account));

    String expectedMessage =
        String.format("Attempt to set a different owner for an account: %s:%s", "Owner1", "Owner2");
    assertEquals(expectedMessage, e.getMessage());
  }

  @Test
  public void testTallyCoresAndSocketsOfRhelForPhysicalSystems() {
    List<String> accounts = List.of("A1", "A2");
    InventoryHostFacts host1 =
        createRhsmHost("A1", "O1", Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    host1.setSystemProfileCoresPerSocket(1);
    host1.setSystemProfileSockets(4);

    InventoryHostFacts host2 = createHypervisor("A1", "O1", TEST_PRODUCT_ID);
    host2.setSystemProfileCoresPerSocket(2);
    host2.setSystemProfileSockets(4);

    InventoryHostFacts host3 =
        createRhsmHost("A2", "O2", Arrays.asList(TEST_PRODUCT_ID), "", OffsetDateTime.now());
    host3.setSystemProfileCoresPerSocket(5);
    host3.setSystemProfileSockets(1);

    InventoryHostFacts host4 = createHypervisor("A2", "O2", TEST_PRODUCT_ID);
    host4.setSystemProfileCoresPerSocket(5);
    host4.setSystemProfileSockets(1);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(host2.getSubscriptionManagerId(), null);
    expectedHypervisorMap.put(host4.getSubscriptionManagerId(), null);
    mockReportedHypervisors(accounts, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of("A1")), anyInt())).thenReturn(Stream.of(host1, host2));
    when(inventoryRepo.getFacts(eq(List.of("A2")), anyInt())).thenReturn(Stream.of(host3, host4));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, "A1");
    calcs.putAll(collector.collect(RHEL_PRODUCTS, "A2"));
    assertEquals(2, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));
    assertThat(calcs, Matchers.hasKey("A2"));

    AccountUsageCalculation a1Calc = calcs.get("A1");
    assertEquals(1, a1Calc.getProducts().size());
    checkTotalsCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);
    checkPhysicalTotalsCalculation(a1Calc, "A1", "O1", TEST_PRODUCT, 12, 8, 2);

    AccountUsageCalculation a2Calc = calcs.get("A2");
    assertEquals(1, a2Calc.getProducts().size());
    checkTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 10, 4, 2);
    checkPhysicalTotalsCalculation(a2Calc, "A2", "O2", TEST_PRODUCT, 10, 4, 2);
  }

  @Test
  public void testHypervisorCalculationsWhenMapped() {
    String account = "A1";

    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", TEST_PRODUCT_ID);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), "A1", "O1", TEST_PRODUCT_ID);
    guest1.setSystemProfileCoresPerSocket(4);
    guest1.setSystemProfileSockets(3);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), "A1", "O1", TEST_PRODUCT_ID);
    guest2.setSystemProfileCoresPerSocket(4);
    guest2.setSystemProfileSockets(2);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    // odd sockets are rounded up for hypervisor.
    // hypervisor gets counted twice - once for itself, once for the guests
    checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 24, 8, 2);
    checkHypervisorTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
  }

  @Test
  public void testHypervisorCalculationsWhenMappedWithNoProductsOnHypervisor() {
    String account = "A1";

    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", null);
    hypervisor.setSystemProfileCoresPerSocket(4);
    hypervisor.setSystemProfileSockets(3);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), "A1", "O1", TEST_PRODUCT_ID);
    guest1.setSystemProfileCoresPerSocket(4);
    guest1.setSystemProfileSockets(3);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), "A1", "O1", TEST_PRODUCT_ID);
    guest2.setSystemProfileCoresPerSocket(4);
    guest2.setSystemProfileSockets(3);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor, guest1, guest2).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    // odd sockets are rounded up for hypervisor.
    checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    checkHypervisorTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.PHYSICAL));
  }

  @Test
  void testGuestCountIsTrackedOnHost() {
    String account = "A1";

    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", TEST_PRODUCT_ID);

    // Guests should not end up in the total since only the hypervisor should be counted.
    InventoryHostFacts guest1 =
        createGuest(hypervisor.getSubscriptionManagerId(), "A1", "O1", TEST_PRODUCT_ID);

    InventoryHostFacts guest2 =
        createGuest(hypervisor.getSubscriptionManagerId(), "A1", "O1", TEST_PRODUCT_ID);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Stream.of(hypervisor, guest1, guest2));

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

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
    String account = "A1";
    Counter counter = meterRegistry.counter("rhsm-subscriptions.tally.hbi_hosts");
    double initialCount = counter.count();

    InventoryHostFacts hypervisor = createHypervisor("A1", "O1", TEST_PRODUCT_ID);

    Map<String, String> expectedHypervisorMap = new HashMap<>();
    expectedHypervisorMap.put(
        hypervisor.getSubscriptionManagerId(), hypervisor.getSubscriptionManagerId());
    mockReportedHypervisors(account, expectedHypervisorMap);

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(hypervisor).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertEquals(1, counter.count() - initialCount);
  }

  @Test
  void accountsWithNullInventoryIdFiltered() {
    String account = "A1";
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);
    InventoryHostFacts host = createRhsmHost(account, "O1", products, "", OffsetDateTime.now());
    host.setSystemProfileCoresPerSocket(4);
    host.setSystemProfileSockets(3);

    when(hostRepo.findByAccountNumber("A1"))
        .thenReturn(
            List.of(
                new Host(null, "insights1", host.getAccount(), host.getOrgId(), null),
                new Host(null, "insights2", host.getAccount(), host.getOrgId(), null)));

    mockReportedHypervisors(account, new HashMap<>());

    when(inventoryRepo.getFacts(eq(List.of(account)), anyInt()))
        .thenReturn(Arrays.asList(host).stream());

    Map<String, AccountUsageCalculation> calcs = collector.collect(RHEL_PRODUCTS, account);
    assertEquals(1, calcs.size());
    assertThat(calcs, Matchers.hasKey("A1"));

    AccountUsageCalculation calc = calcs.get("A1");
    // odd sockets are rounded up.
    checkTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    checkPhysicalTotalsCalculation(calc, "A1", "O1", TEST_PRODUCT, 12, 4, 1);
    assertNull(
        calc.getCalculation(createUsageKey(TEST_PRODUCT))
            .getTotals(HardwareMeasurementType.VIRTUAL));
  }

  @Test
  void removesDuplicateHostRecords() {
    String account = "A1";
    List<Integer> products = Arrays.asList(TEST_PRODUCT_ID);
    InventoryHostFacts host = createRhsmHost(account, "O1", products, "", OffsetDateTime.now());
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

    AccountServiceInventory accountServiceInventory = new AccountServiceInventory("A1", "HBI_HOST");
    accountServiceInventory.getServiceInstances().put(host.getInventoryId().toString(), orig);
    accountServiceInventory.getServiceInstances().put("i2", dupe);

    when(accountServiceInventoryRepository.findById(
            new AccountServiceInventoryId("A1", "HBI_HOST")))
        .thenReturn(Optional.of(accountServiceInventory));

    when(inventoryRepo.getFacts(eq(List.of("A1")), any())).thenReturn(Stream.of(host));

    collector.collect(RHEL_PRODUCTS, account);

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
        calc, account, owner, product, serviceLevel, Usage._ANY, cores, sockets, instances);
  }

  private void checkTotalsCalculation(
      AccountUsageCalculation calc,
      String account,
      String owner,
      String product,
      ServiceLevel serviceLevel,
      Usage usage,
      int cores,
      int sockets,
      int instances) {
    assertEquals(account, calc.getAccount());
    assertEquals(owner, calc.getOwner());
    assertTrue(calc.containsCalculation(createUsageKey(product, serviceLevel, usage)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product, serviceLevel, usage));

    assertEquals(product, prodCalc.getProductId());
    assertEquals(serviceLevel, prodCalc.getSla());
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
    assertEquals(owner, calc.getOwner());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertPhysicalTotalsCalculation(prodCalc, physicalSockets, physicalCores, physicalInstances);
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
    assertEquals(owner, calc.getOwner());
    assertTrue(calc.containsCalculation(createUsageKey(product)));

    UsageCalculation prodCalc = calc.getCalculation(createUsageKey(product));
    assertEquals(product, prodCalc.getProductId());
    assertHypervisorTotalsCalculation(prodCalc, hypSockets, hypCores, hypInstances);
  }

  private UsageCalculation.Key createUsageKey(String product) {
    return createUsageKey(product, ServiceLevel._ANY);
  }

  private UsageCalculation.Key createUsageKey(String product, ServiceLevel sla) {
    return new UsageCalculation.Key(product, sla, Usage._ANY);
  }

  private UsageCalculation.Key createUsageKey(String product, ServiceLevel sla, Usage usage) {
    return new UsageCalculation.Key(product, sla, usage);
  }

  private void mockReportedHypervisors(String account, Map<String, String> expectedHypervisorMap) {
    mockReportedHypervisors(List.of(account), expectedHypervisorMap);
  }

  private void mockReportedHypervisors(
      List<String> accounts, Map<String, String> expectedHypervisorMap) {
    Builder streamBuilder = Stream.builder();
    for (Entry<String, String> entry : expectedHypervisorMap.entrySet()) {
      streamBuilder.accept(new Object[] {entry.getKey(), entry.getValue()});
    }
    when(inventoryRepo.getReportedHypervisors(eq(accounts))).thenReturn(streamBuilder.build());
  }
}
