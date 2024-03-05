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
package org.candlepin.subscriptions.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.candlepin.subscriptions.db.model.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(Lifecycle.PER_CLASS)
class AccountServiceInventoryRepositoryTest {

  @Autowired AccountServiceInventoryRepository repo;

  @Autowired HostRepository hostRepo;

  @Transactional
  @BeforeAll
  void setupTestData() {
    AccountServiceInventory service = new AccountServiceInventory("org123", "HBI_HOST");

    Host host = new Host();
    host.setOrgId("org123");
    host.setInstanceId("1c474d4e-c277-472c-94ab-8229a40417eb");
    host.setDisplayName("name");
    host.setInstanceType("HBI_HOST");
    service.getServiceInstances().put(host.getInstanceId(), host);

    repo.save(service);
    repo.flush();
  }

  // NOTE: this cleanup necessary because @Transactional on the setup method does *not*
  // automatically
  // rollback/remove the test data
  @Transactional
  @AfterAll
  void cleanupTestData() {
    repo.deleteAll();
  }

  @Test
  void testHbiHostCanBeLoaded() {
    assertTrue(
        repo.findById(
                AccountServiceInventoryId.builder().orgId("org123").serviceType("HBI_HOST").build())
            .isPresent());
  }

  @Test
  void testCanFetchExistingInstancesViaAccountRepository() {
    Optional<AccountServiceInventory> account =
        repo.findById(
            AccountServiceInventoryId.builder().orgId("org123").serviceType("HBI_HOST").build());

    assertTrue(account.isPresent());
    Map<String, Host> existingInstances = account.get().getServiceInstances();

    Host host = existingInstances.get("1c474d4e-c277-472c-94ab-8229a40417eb");

    Host expected = new Host();
    expected.setOrgId("org123");
    expected.setDisplayName("name");
    expected.setInstanceId("1c474d4e-c277-472c-94ab-8229a40417eb");
    expected.setInstanceType("HBI_HOST");

    // we have no idea what the generated ID is, set it so equals comparison can succeed
    expected.setId(host.getId());
    assertEquals(expected, host);
  }

  @Transactional
  @Test
  void testCanAddHostViaRepo() {
    AccountServiceInventory accountServiceInventory =
        repo.findById(
                AccountServiceInventoryId.builder().orgId("org123").serviceType("HBI_HOST").build())
            .orElseThrow();

    String instanceId = "478edb89-b105-4dfd-9a46-0f1427514b76";
    Host host = new Host();
    host.setInstanceId(instanceId);
    host.setOrgId("org123");
    host.setDisplayName("name");
    host.setInstanceType("HBI_HOST");
    HostTallyBucket bucket =
        new HostTallyBucket(
            host,
            "product",
            ServiceLevel.PREMIUM,
            Usage.PRODUCTION,
            BillingProvider._ANY,
            "_ANY",
            false,
            4,
            4,
            HardwareMeasurementType.PHYSICAL);
    host.getBuckets().add(bucket);

    accountServiceInventory.getServiceInstances().put(instanceId, host);
    var inventory = repo.save(accountServiceInventory);
    // Update with the persisted object so that the generated IDs will be present in the
    // HostBucketKeys.  See HHH-17634
    host = inventory.getServiceInstances().get(instanceId);
    repo.flush();

    AccountServiceInventory fetched =
        repo.findById(
                AccountServiceInventoryId.builder().orgId("org123").serviceType("HBI_HOST").build())
            .orElseThrow();
    assertTrue(fetched.getServiceInstances().containsKey(instanceId));
    Host fetchedInstance = fetched.getServiceInstances().get(instanceId);
    // set ID in order to compare, because JPA doesn't populate the existing object's ID
    // automatically
    host.setId(fetchedInstance.getId());
    assertEquals(host, fetchedInstance);
  }

  @Transactional
  @Test
  void testCanRemoveHostViaRepo() {
    AccountServiceInventory accountServiceInventory =
        repo.findById(
                AccountServiceInventoryId.builder().orgId("org123").serviceType("HBI_HOST").build())
            .orElseThrow();

    accountServiceInventory.getServiceInstances().clear();
    repo.save(accountServiceInventory);
    repo.flush();

    AccountServiceInventory fetched =
        repo.findById(
                AccountServiceInventoryId.builder().orgId("org123").serviceType("HBI_HOST").build())
            .orElseThrow();
    assertTrue(fetched.getServiceInstances().isEmpty());
  }
}
