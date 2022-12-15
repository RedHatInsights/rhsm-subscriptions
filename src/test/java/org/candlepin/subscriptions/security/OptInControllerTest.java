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
package org.candlepin.subscriptions.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.util.TimeZone;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.user.AccountService;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataAccount;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataOrg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@ActiveProfiles("test")
class OptInControllerTest {

  @Autowired private AccountConfigRepository accountRepo;

  @Autowired private OrgConfigRepository orgRepo;

  @Autowired private AccountService accountService;

  private OptInController controller;
  private ApplicationClock clock;

  @BeforeEach
  void setupTest() {
    clock = new FixedClockConfiguration().fixedClock();
    TimeZone.setDefault(TimeZone.getTimeZone(clock.getClock().getZone()));
    controller = new OptInController(clock, accountRepo, orgRepo, accountService);
  }

  @Test
  void testOptInWithNewEntity() {
    String expectedAccount = "my-account";
    String expectedOrg = "my-org";

    OptInConfig saved = controller.optIn("my-account", "my-org", OptInType.API);
    assertNotNull(saved);
    assertNotNull(saved.getData());
    assertNotNull(saved.getMeta());
    assertTrue(saved.getData().getOptInComplete());
    assertNotNull(saved.getData().getAccount());
    assertNotNull(saved.getData().getOrg());

    assertEquals(expectedAccount, saved.getMeta().getAccountNumber());
    assertEquals(expectedOrg, saved.getMeta().getOrgId());

    OptInConfigDataAccount accountConig = saved.getData().getAccount();
    assertNotNull(accountConig);
    assertEquals(expectedAccount, accountConig.getAccountNumber());
    assertEquals(clock.now(), accountConig.getCreated());
    assertEquals(clock.now(), accountConig.getLastUpdated());
    assertEquals(OptInType.API.name(), accountConig.getOptInType());

    OptInConfigDataOrg orgConfig = saved.getData().getOrg();
    assertNotNull(orgConfig);
    assertEquals(expectedOrg, orgConfig.getOrgId());
    assertEquals(clock.now(), orgConfig.getCreated());
    assertEquals(clock.now(), orgConfig.getLastUpdated());
    assertEquals(OptInType.API.name(), orgConfig.getOptInType());
  }

  @Test
  void testOptInWithExistingEntity() {
    AccountConfig existingAccountConfig = setupExistingAccountConfig();
    OrgConfig existingOrgConfig = setupExistingOrgConfig("TEST_ORG1");

    OptInConfig modified =
        controller.optIn(
            existingAccountConfig.getAccountNumber(), existingOrgConfig.getOrgId(), OptInType.API);

    assertNotNull(modified);
    assertNotNull(modified.getData());
    assertNotNull(modified.getMeta());
    assertTrue(modified.getData().getOptInComplete());
    assertNotNull(modified.getData().getAccount());
    assertNotNull(modified.getData().getOrg());

    assertEquals(existingAccountConfig.getAccountNumber(), modified.getMeta().getAccountNumber());
    assertEquals(existingOrgConfig.getOrgId(), modified.getMeta().getOrgId());

    OptInConfigDataAccount accountConig = modified.getData().getAccount();
    assertNotNull(accountConig);
    assertEquals(existingAccountConfig.getAccountNumber(), accountConig.getAccountNumber());
    // Created date should not change
    assertEquals(clock.now().minusDays(1), accountConig.getCreated());
    // Updated date should be changed
    assertEquals(clock.now(), accountConig.getLastUpdated());
    // Type should not change as we want to track the initial creation type
    assertEquals(OptInType.DB.name(), accountConig.getOptInType());

    OptInConfigDataOrg orgConfig = modified.getData().getOrg();
    assertNotNull(orgConfig);
    assertEquals(existingOrgConfig.getOrgId(), orgConfig.getOrgId());
    assertEquals(clock.now().minusDays(1), orgConfig.getCreated());
    assertEquals(clock.now(), orgConfig.getLastUpdated());
    assertEquals(OptInType.DB.name(), orgConfig.getOptInType());
  }

  @Test
  void testOptInCreatesAccountConfigIfItDoesntExist() {
    OrgConfig existingOrgConfig = setupExistingOrgConfig("TEST_ORG3");

    OptInConfig saved =
        controller.optIn("TEST_ACCOUNT3", existingOrgConfig.getOrgId(), OptInType.API);

    assertNotNull(saved);
    assertNotNull(saved.getData());
    assertNotNull(saved.getMeta());
    assertTrue(saved.getData().getOptInComplete());
    assertNotNull(saved.getData().getAccount());
    assertNotNull(saved.getData().getOrg());

    assertEquals("TEST_ACCOUNT3", saved.getMeta().getAccountNumber());
    assertEquals(existingOrgConfig.getOrgId(), saved.getMeta().getOrgId());

    OptInConfigDataAccount savedAccountConfig = saved.getData().getAccount();
    assertEquals("TEST_ACCOUNT3", savedAccountConfig.getAccountNumber());
    // Created date should not change
    assertEquals(clock.now(), savedAccountConfig.getCreated());
    // Updated date should be changed
    assertEquals(clock.now(), savedAccountConfig.getLastUpdated());
    // Type should not change as we want to track the initial creation type
    assertEquals(OptInType.API.name(), savedAccountConfig.getOptInType());

    OptInConfigDataOrg savedOrgConfig = saved.getData().getOrg();
    assertEquals(existingOrgConfig.getOrgId(), savedOrgConfig.getOrgId());
    assertEquals(clock.now().minusDays(1), savedOrgConfig.getCreated());
    assertEquals(clock.now(), savedOrgConfig.getLastUpdated());
    // OptInType expected to be API since the config didn't exist yet.
    assertEquals(OptInType.DB.name(), savedOrgConfig.getOptInType());
  }

  @Test
  void testOptInViaAccountNumber() {
    controller.optInByAccountNumber("account123", OptInType.API);

    assertTrue(orgRepo.existsByOrgId("org123"));
    assertTrue(accountRepo.existsByAccountNumber("account123"));
  }

  @Test
  void testOptInViaAccountNumberDoesNotUseApiIfOptInExists() {
    AccountConfig accountConfig = new AccountConfig();
    accountConfig.setAccountNumber("account123");
    accountConfig.setOrgId("org123");
    accountConfig.setOptInType(OptInType.API);
    accountConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    accountConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    accountRepo.save(accountConfig);

    AccountService mockAccountService = mock(AccountService.class);
    OptInController controllerWithMockApi =
        new OptInController(clock, accountRepo, orgRepo, mockAccountService);
    controllerWithMockApi.optInByAccountNumber("account123", OptInType.API);

    verifyNoInteractions(mockAccountService);
    assertTrue(accountRepo.existsByAccountNumber("account123"));
  }

  @Test
  void testOptInViaOrgIdOnly() {
    controller.optInByOrgId("org123", OptInType.API);
    assertTrue(orgRepo.existsByOrgId("org123"));
  }

  @Test
  void testOptInViaOrgIdDoesNotUseApiIfOptInExists() {
    OrgConfig orgConfig = new OrgConfig("org123");
    orgConfig.setOptInType(OptInType.API);
    orgConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgRepo.save(orgConfig);

    AccountService mockAccountService = mock(AccountService.class);
    OptInController controllerWithMockApi =
        new OptInController(clock, accountRepo, orgRepo, mockAccountService);
    controllerWithMockApi.optInByOrgId("org123", OptInType.API);

    verifyNoInteractions(mockAccountService);
    assertTrue(orgRepo.existsById("org123"));
  }

  @Test
  void testOptOut() {
    String expectedAccountNumber = "my-account";
    String expectedOrgId = "my-org";

    controller.optOut(expectedOrgId);
    assertTrue(accountRepo.findById(expectedAccountNumber).isEmpty());
    assertTrue(orgRepo.findById(expectedOrgId).isEmpty());
  }

  @Test
  void testGetOptInConfig() {
    String expectedAccount = "account123456";
    String expectedOrg = "owner123456";
    OptInType expectedOptIn = OptInType.API;
    OffsetDateTime expectedOptInDate = clock.now();
    OffsetDateTime expectedUpdatedDate = expectedOptInDate.plusDays(1);

    AccountConfig accountConfig = new AccountConfig();
    accountConfig.setAccountNumber(expectedAccount);
    accountConfig.setOrgId(expectedOrg);
    accountConfig.setOptInType(expectedOptIn);
    accountConfig.setCreated(expectedOptInDate);
    accountConfig.setUpdated(expectedUpdatedDate);
    accountRepo.save(accountConfig);

    OrgConfig orgConfig = new OrgConfig(expectedOrg);
    orgConfig.setOptInType(expectedOptIn);
    orgConfig.setCreated(expectedOptInDate);
    orgConfig.setUpdated(expectedUpdatedDate);
    orgRepo.save(orgConfig);

    OptInConfig dto = controller.getOptInConfig(expectedAccount, expectedOrg);
    assertNotNull(dto.getData());
    assertNotNull(dto.getMeta());
    assertNotNull(dto.getData().getAccount());
    assertNotNull(dto.getData().getOrg());

    assertEquals(expectedAccount, dto.getMeta().getAccountNumber());
    assertEquals(expectedOrg, dto.getMeta().getOrgId());

    OptInConfigDataAccount accountDto = dto.getData().getAccount();
    assertEquals(accountDto.getAccountNumber(), expectedAccount);
    assertEquals(accountDto.getOptInType(), expectedOptIn.name());
    assertEquals(accountDto.getCreated(), expectedOptInDate);
    assertEquals(accountDto.getLastUpdated(), expectedUpdatedDate);

    OptInConfigDataOrg orgDto = dto.getData().getOrg();
    assertEquals(orgDto.getOrgId(), expectedOrg);
    assertEquals(orgDto.getOptInType(), expectedOptIn.name());
    assertEquals(orgDto.getCreated(), expectedOptInDate);
    assertEquals(orgDto.getLastUpdated(), expectedUpdatedDate);

    assertTrue(dto.getData().getOptInComplete());
  }

  @Test
  void testGetOptInConfigForAccountNumber() {
    AccountConfig accountConfig = new AccountConfig();
    accountConfig.setAccountNumber("account123");
    accountConfig.setOrgId("org123");
    accountConfig.setOptInType(OptInType.API);
    accountConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    accountConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    accountRepo.save(accountConfig);

    OrgConfig orgConfig = new OrgConfig("org123");
    orgConfig.setOptInType(OptInType.API);
    orgConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgRepo.save(orgConfig);

    OptInConfig dto = controller.getOptInConfigForAccountNumber("account123");
    assertNotNull(dto.getData().getAccount());
    assertEquals("account123", dto.getData().getAccount().getAccountNumber());
    assertNotNull(dto.getData().getOrg());
    assertEquals("org123", dto.getData().getOrg().getOrgId());
  }

  @Test
  void testGetOptInConfigForOrgId() {
    AccountConfig accountConfig = new AccountConfig();
    accountConfig.setAccountNumber("account123");
    accountConfig.setOrgId("org123");
    accountConfig.setOptInType(OptInType.API);
    accountConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    accountConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    accountRepo.save(accountConfig);

    OrgConfig orgConfig = new OrgConfig("org123");
    orgConfig.setOptInType(OptInType.API);
    orgConfig.setCreated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgConfig.setUpdated(OffsetDateTime.parse("2021-04-06T00:00:00Z"));
    orgRepo.save(orgConfig);

    OptInConfig dto = controller.getOptInConfigForOrgId("org123");
    assertNotNull(dto.getData().getAccount());
    assertEquals("account123", dto.getData().getAccount().getAccountNumber());
    assertNotNull(dto.getData().getOrg());
    assertEquals("org123", dto.getData().getOrg().getOrgId());
  }

  private AccountConfig setupExistingAccountConfig() {
    AccountConfig config = new AccountConfig();
    config.setAccountNumber("TEST_ACCOUNT1");
    config.setOrgId("TEST_ORG1");
    config.setCreated(clock.now().minusDays(1));
    config.setUpdated(clock.now());
    config.setOptInType(OptInType.DB);
    return accountRepo.save(config);
  }

  private OrgConfig setupExistingOrgConfig(String org) {
    OrgConfig config = new OrgConfig(org);
    config.setCreated(clock.now().minusDays(1));
    config.setUpdated(clock.now());
    config.setOptInType(OptInType.DB);
    return orgRepo.save(config);
  }
}
