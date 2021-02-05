/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.db.model.Account;
import org.candlepin.subscriptions.db.model.Host;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(Lifecycle.PER_CLASS)
class AccountRepositoryTest {

    @Autowired
    AccountRepository repo;

    @Autowired
    AccountConfigRepository accountConfigRepo;

    @Autowired
    HostRepository hostRepo;

    @Transactional
    @BeforeAll
    void setupTestData() {
        Host host = new Host();
        host.setAccountNumber("account123");
        host.setInstanceId("1c474d4e-c277-472c-94ab-8229a40417eb");
        host.setDisplayName("name");
        host.setInstanceType("Test");
        hostRepo.save(host);

        AccountConfig accountConfig = new AccountConfig();
        accountConfig.setAccountNumber("account123");
        accountConfig.setReportingEnabled(true);
        accountConfig.setSyncEnabled(true);
        accountConfig.setOptInType(OptInType.DB);
        accountConfig.setCreated(OffsetDateTime.now());
        accountConfig.setUpdated(OffsetDateTime.now());
        accountConfigRepo.save(accountConfig);

        hostRepo.flush();
        accountConfigRepo.flush();
    }

    @Test
    void testCanFetchExistingInstancesViaAccountRepository() {
        Optional<Account> account = repo.findById("account123");

        assertTrue(account.isPresent());
        Map<String, Host> existingInstances = account.get().getServiceInstances();

        Host host = existingInstances.get("1c474d4e-c277-472c-94ab-8229a40417eb");

        Host expected = new Host();
        expected.setAccountNumber("account123");
        expected.setDisplayName("name");
        expected.setInstanceId("1c474d4e-c277-472c-94ab-8229a40417eb");
        expected.setInstanceType("Test");

        // we have no idea what the generated ID is, set it so equals comparison can succeed
        expected.setId(host.getId());
        assertEquals(expected, host);
    }

    @Transactional
    @Test
    void testCanAddHostViaRepo() {
        Account account = repo.findById("account123").orElseThrow();

        String instanceId = "478edb89-b105-4dfd-9a46-0f1427514b76";
        Host host = new Host();
        host.setInstanceId(instanceId);
        host.setAccountNumber("account123");
        host.setDisplayName("name");
        host.setInstanceType("Test");

        account.getServiceInstances().put(instanceId, host);
        repo.save(account);
        repo.flush();

        Account fetched = repo.findById("account123").orElseThrow();
        assertTrue(fetched.getServiceInstances().containsKey(instanceId));
        Host fetchedInstance = fetched.getServiceInstances().get(instanceId);
        // set ID in order to compare, because JPA doesn't populate the existing object's ID automatically
        host.setId(fetchedInstance.getId());
        assertEquals(host, fetchedInstance);

    }

    @Transactional
    @Test
    void testCanRemoveHostViaRepo() {
        Account account = repo.findById("account123").orElseThrow();

        account.getServiceInstances().clear();
        repo.save(account);
        repo.flush();

        Account fetched = repo.findById("account123").orElseThrow();
        assertTrue(fetched.getServiceInstances().isEmpty());
    }
}
