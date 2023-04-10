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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class InventoryAccountUsageCollectorCollectTest {

  private static final String TEST_PRODUCT = "RHEL";
  public static final Integer TEST_PRODUCT_ID = 1;
  private static final String NON_RHEL = "OTHER PRODUCT";
  public static final Integer NON_RHEL_PRODUCT_ID = 2000;

  public static final Set<String> RHEL_PRODUCTS = new HashSet<>(List.of(TEST_PRODUCT));
  public static final Set<String> NON_RHEL_PRODUCTS = new HashSet<>(List.of(NON_RHEL));
  private static final String BILLING_ACCOUNT_ID_ANY = "_ANY";

  public static final String ACCOUNT = "foo123";
  public static final String ORG_ID = "org123";

  @MockBean private InventoryRepository inventoryRepo;
  @MockBean private HostRepository hostRepo;
  @MockBean private AccountServiceInventoryRepository accountServiceInventoryRepository;
  @Autowired private InventoryAccountUsageCollector collector;
  @Autowired private MeterRegistry meterRegistry;

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

    collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);

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

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), anyInt())).thenReturn(Stream.of(hypervisor));

    collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);
    assertEquals(1, counter.count() - initialCount);
  }

  @Test
  void removesDuplicateHostRecords() {
    List<Integer> products = List.of(TEST_PRODUCT_ID);
    InventoryHostFacts host =
        createRhsmHost(ACCOUNT, ORG_ID, products, "", "", OffsetDateTime.now());
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
        new AccountServiceInventory(ORG_ID, "HBI_HOST");
    accountServiceInventory.getServiceInstances().put(host.getInventoryId().toString(), orig);
    accountServiceInventory.getServiceInstances().put("i2", dupe);

    when(accountServiceInventoryRepository.findById(
            AccountServiceInventoryId.builder().orgId(ORG_ID).serviceType("HBI_HOST").build()))
        .thenReturn(Optional.of(accountServiceInventory));

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), any())).thenReturn(Stream.of(host));

    collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);

    assertEquals(1, accountServiceInventory.getServiceInstances().size());
  }

  @Test
  void ensureStaleHostsAreDeleted() {
    List<Integer> products = List.of(TEST_PRODUCT_ID);
    InventoryHostFacts host =
        createRhsmHost(ACCOUNT, ORG_ID, products, "", "", OffsetDateTime.now());
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
    Host noLongerReported =
        new Host("i2-inventory-id", "insights2", host.getAccount(), host.getOrgId(), null);
    noLongerReported.setInstanceId("i2");

    AccountServiceInventory accountServiceInventory =
        new AccountServiceInventory(ORG_ID, "HBI_HOST");
    accountServiceInventory.getServiceInstances().put(host.getInventoryId().toString(), orig);
    accountServiceInventory.getServiceInstances().put("i2", noLongerReported);

    when(accountServiceInventoryRepository.findById(
            AccountServiceInventoryId.builder().orgId(ORG_ID).serviceType("HBI_HOST").build()))
        .thenReturn(Optional.of(accountServiceInventory));

    when(inventoryRepo.getFacts(eq(List.of(ORG_ID)), any())).thenReturn(Stream.of(host));

    collector.collect(RHEL_PRODUCTS, ACCOUNT, ORG_ID);

    assertEquals(1, accountServiceInventory.getServiceInstances().size());
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
}
