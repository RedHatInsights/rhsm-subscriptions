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

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureTestDatabase
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class AccountConfigRepositoryTest {

  @Autowired private AccountConfigRepository repository;
  @Autowired private ApplicationClock clock;

  @Test
  void saveAndUpdate() {
    OffsetDateTime creation = clock.now();
    OffsetDateTime expectedUpdate = creation.plusDays(1);

    String account = "test-account";
    AccountConfig config = new AccountConfig();
    config.setAccountNumber(account);
    config.setOrgId("test-og");
    config.setOptInType(OptInType.API);
    config.setCreated(creation);
    config.setUpdated(expectedUpdate);

    repository.saveAndFlush(config);

    AccountConfig found = repository.findByOrgId("test-og").orElseThrow();
    assertEquals(config, found);

    found.setOptInType(OptInType.API);
    repository.saveAndFlush(found);

    AccountConfig updated = repository.findByOrgId("test-og").orElseThrow();
    assertNotNull(updated);
    assertEquals(OptInType.API, updated.getOptInType());
  }

  @Test
  void testDelete() {
    AccountConfig config = createConfig("an-account", "an-org");
    repository.saveAndFlush(config);

    AccountConfig toDelete = repository.findByOrgId("an-org").orElseThrow();
    assertNotNull(toDelete);
    repository.delete(toDelete);
    repository.flush();

    assertEquals(0, repository.count());
  }

  @Test
  void testFindAccountsWithEnabledSync() {
    repository.saveAll(
        Arrays.asList(
            createConfig("A1", "O1"),
            createConfig("A2", "O2"),
            createConfig("A3", "O3"),
            createConfig("A4", "O4")));
    repository.flush();

    List<String> accountsWithSync =
        repository.findSyncEnabledAccounts().collect(Collectors.toList());
    assertEquals(4, accountsWithSync.size());
    assertTrue(accountsWithSync.containsAll(Arrays.asList("A1", "A2", "A3", "A4")));
  }

  @Test
  void testOptInCount() {
    OffsetDateTime begin = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC).minusSeconds(1);
    OffsetDateTime end = begin.plusDays(1);

    AccountConfig optInBefore = createConfig("A1", "O1");
    optInBefore.setCreated(begin.minusSeconds(1));

    AccountConfig optInBeginning = createConfig("A2", "O2");
    optInBeginning.setCreated(begin);

    AccountConfig optInEnd = createConfig("A3", "O3");
    optInEnd.setCreated(end);

    AccountConfig optInAfter = createConfig("A4", "O4");
    optInAfter.setCreated(end.plusSeconds(1));

    repository.saveAll(Arrays.asList(optInBefore, optInBeginning, optInEnd, optInAfter));
    repository.flush();

    int count =
        repository.getCountOfOptInsForDateRange(
            OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC), OffsetDateTime.now());

    assertEquals(2, count);
  }

  @Test
  void testLookupOrgIdByAccountNumber() {
    AccountConfig expectedConfig = createConfig("A2", "O2");
    repository.saveAll(
        Arrays.asList(
            createConfig("A1", "O1"),
            expectedConfig,
            createConfig("A3", "O3"),
            createConfig("A4", "O4")));
    repository.flush();

    assertEquals("O2", repository.findOrgByAccountNumber("A2"));
  }

  private AccountConfig createConfig(String account, String orgId) {
    AccountConfig config = new AccountConfig();
    config.setAccountNumber(account);
    config.setOrgId(orgId);
    config.setOptInType(OptInType.API);
    config.setCreated(clock.now());
    config.setUpdated(config.getCreated().plusDays(1));
    return config;
  }

  @Test
  void testFindAccountNumberByOrgId() {
    String account = repository.findAccountNumberByOrgId("12344444");
    assertNull(account);
  }
}
