/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataAccount;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataOrg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.Optional;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
public class OptInControllerTest {

    @MockBean
    private AccountConfigRepository accountRepo;

    @MockBean
    private OrgConfigRepository orgRepo;

    private OptInController controller;
    private ApplicationClock clock;

    @BeforeEach
    public void setupTest() {
        clock = new FixedClockConfiguration().fixedClock();
        controller = new OptInController(clock, accountRepo, orgRepo);
    }

    @Test
    public void testOptInWithNewEntity() {
        when(accountRepo.save(any(AccountConfig.class))).thenAnswer(i -> i.getArguments()[0]);
        when(orgRepo.save(any(OrgConfig.class))).thenAnswer(i -> i.getArguments()[0]);

        OptInConfig saved = controller.optIn(
            "my-account", "my-org", OptInType.API, true, true, true);
        assertNotNull(saved);
        assertNotNull(saved.getData());
        assertNotNull(saved.getMeta());
        assertTrue(saved.getData().getOptInComplete());
        assertNotNull(saved.getData().getAccount());
        assertNotNull(saved.getData().getOrg());

        assertEquals("my-account", saved.getMeta().getAccountNumber());
        assertEquals("my-org", saved.getMeta().getOrgId());

        OptInConfigDataAccount accountConig = saved.getData().getAccount();
        assertNotNull(accountConig);
        assertEquals("my-account", accountConig.getAccountNumber());
        assertTrue(accountConig.getTallyReportingEnabled());
        assertTrue(accountConig.getTallySyncEnabled());
        assertEquals(clock.now(), accountConig.getCreated());
        assertEquals(clock.now(), accountConig.getLastUpdated());
        assertEquals(OptInType.API.name(), accountConig.getOptInType());

        OptInConfigDataOrg orgConfig = saved.getData().getOrg();
        assertNotNull(orgConfig);
        assertEquals("my-org", orgConfig.getOrgId());
        assertTrue(orgConfig.getConduitSyncEnabled());
        assertEquals(clock.now(), orgConfig.getCreated());
        assertEquals(clock.now(), orgConfig.getLastUpdated());
        assertEquals(OptInType.API.name(), orgConfig.getOptInType());
    }

    @Test
    public void testOptInWithExistingEntity() {
        AccountConfig existingAccountConfig = setupExistingAccountConfig();
        OrgConfig existingOrgConfig = setupExistingOrgConfig();

        when(accountRepo.save(any(AccountConfig.class))).thenAnswer(i -> i.getArguments()[0]);
        when(orgRepo.save(any(OrgConfig.class))).thenAnswer(i -> i.getArguments()[0]);

        OptInConfig modified = controller.optIn(
            existingAccountConfig.getAccountNumber(),
            existingOrgConfig.getOrgId(),
            OptInType.API,
            false,
            false,
            false);

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
        assertFalse(accountConig.getTallyReportingEnabled());
        assertFalse(accountConig.getTallySyncEnabled());
        // Created date should not change
        assertEquals(clock.now().minusDays(1), accountConig.getCreated());
        // Updated date should be changed
        assertEquals(clock.now(), accountConig.getLastUpdated());
        // Type should not change as we want to track the initial creation type
        assertEquals(OptInType.DB.name(), accountConig.getOptInType());

        OptInConfigDataOrg orgConfig = modified.getData().getOrg();
        assertNotNull(orgConfig);
        assertEquals(existingOrgConfig.getOrgId(), orgConfig.getOrgId());
        assertFalse(orgConfig.getConduitSyncEnabled());
        assertEquals(clock.now().minusDays(1), orgConfig.getCreated());
        assertEquals(clock.now(), orgConfig.getLastUpdated());
        assertEquals(OptInType.DB.name(), orgConfig.getOptInType());
    }

    @Test
    public void testOptInCreatesOrgConfigIfItDoesNotExist() {
        AccountConfig existingAccountConfig = setupExistingAccountConfig();

        when(accountRepo.save(any(AccountConfig.class))).thenAnswer(i -> i.getArguments()[0]);
        when(orgRepo.save(any(OrgConfig.class))).thenAnswer(i -> i.getArguments()[0]);

        OptInConfig saved = controller.optIn(
            existingAccountConfig.getAccountNumber(),
            "my-org",
            OptInType.API,
            false,
            false,
            false);

        assertNotNull(saved);
        assertNotNull(saved.getData());
        assertNotNull(saved.getMeta());
        assertTrue(saved.getData().getOptInComplete());
        assertNotNull(saved.getData().getAccount());
        assertNotNull(saved.getData().getOrg());

        assertEquals(existingAccountConfig.getAccountNumber(), saved.getMeta().getAccountNumber());
        assertEquals("my-org", saved.getMeta().getOrgId());

        OptInConfigDataAccount savedAccountConfig = saved.getData().getAccount();
        assertEquals(existingAccountConfig.getAccountNumber(), savedAccountConfig.getAccountNumber());
        assertFalse(savedAccountConfig.getTallyReportingEnabled());
        assertFalse(savedAccountConfig.getTallySyncEnabled());
        // Created date should not change
        assertEquals(clock.now().minusDays(1), savedAccountConfig.getCreated());
        // Updated date should be changed
        assertEquals(clock.now(), savedAccountConfig.getLastUpdated());
        // Type should not change as we want to track the initial creation type
        assertEquals(OptInType.DB.name(), savedAccountConfig.getOptInType());

        OptInConfigDataOrg savedOrgConfig = saved.getData().getOrg();
        assertEquals("my-org", savedOrgConfig.getOrgId());
        assertFalse(savedOrgConfig.getConduitSyncEnabled());
        assertEquals(clock.now(), savedOrgConfig.getCreated());
        assertEquals(clock.now(), savedOrgConfig.getLastUpdated());
        // OptInType expected to be API since the config didn't exist yet.
        assertEquals(OptInType.API.name(), savedOrgConfig.getOptInType());
    }

    @Test
    public void testOptInCreatesAccountConfigIfItDoesntExist() {
        OrgConfig existingOrgConfig = setupExistingOrgConfig();

        when(accountRepo.save(any(AccountConfig.class))).thenAnswer(i -> i.getArguments()[0]);
        when(orgRepo.save(any(OrgConfig.class))).thenAnswer(i -> i.getArguments()[0]);

        OptInConfig saved = controller.optIn(
            "my-account",
            existingOrgConfig.getOrgId(),
            OptInType.API,
            false,
            false,
            false);

        assertNotNull(saved);
        assertNotNull(saved.getData());
        assertNotNull(saved.getMeta());
        assertTrue(saved.getData().getOptInComplete());
        assertNotNull(saved.getData().getAccount());
        assertNotNull(saved.getData().getOrg());

        assertEquals("my-account", saved.getMeta().getAccountNumber());
        assertEquals(existingOrgConfig.getOrgId(), saved.getMeta().getOrgId());

        OptInConfigDataAccount savedAccountConfig = saved.getData().getAccount();
        assertEquals("my-account", savedAccountConfig.getAccountNumber());
        assertFalse(savedAccountConfig.getTallyReportingEnabled());
        assertFalse(savedAccountConfig.getTallySyncEnabled());
        // Created date should not change
        assertEquals(clock.now(), savedAccountConfig.getCreated());
        // Updated date should be changed
        assertEquals(clock.now(), savedAccountConfig.getLastUpdated());
        // Type should not change as we want to track the initial creation type
        assertEquals(OptInType.API.name(), savedAccountConfig.getOptInType());

        OptInConfigDataOrg savedOrgConfig = saved.getData().getOrg();
        assertEquals(existingOrgConfig.getOrgId(), savedOrgConfig.getOrgId());
        assertFalse(savedOrgConfig.getConduitSyncEnabled());
        assertEquals(clock.now().minusDays(1), savedOrgConfig.getCreated());
        assertEquals(clock.now(), savedOrgConfig.getLastUpdated());
        // OptInType expected to be API since the config didn't exist yet.
        assertEquals(OptInType.DB.name(), savedOrgConfig.getOptInType());
    }

    @Test
    public void testOptOut() {
        String expectedAccountNumber = "my-account";
        String expectedOrgId = "my-org";

        when(accountRepo.existsById(eq(expectedAccountNumber))).thenReturn(true);
        when(orgRepo.existsById(eq(expectedOrgId))).thenReturn(true);

        controller.optOut(expectedAccountNumber, expectedOrgId);
        verify(accountRepo).deleteById(eq(expectedAccountNumber));
        verify(orgRepo).deleteById(eq(expectedOrgId));
    }

    @Test
    public void testGetOptInConfig() {
        String expectedAccount = "account123456";
        String expectedOrg = "owner123456";
        Boolean expectedSyncEnabled = Boolean.TRUE;
        Boolean expectedReportingEnabled = Boolean.FALSE;
        OptInType expectedOptIn = OptInType.API;
        OffsetDateTime expectedOptInDate = clock.now();
        OffsetDateTime expectedUpdatedDate = expectedOptInDate.plusDays(1);

        AccountConfig accountConfig = new AccountConfig(expectedAccount);
        accountConfig.setSyncEnabled(expectedSyncEnabled);
        accountConfig.setReportingEnabled(expectedReportingEnabled);
        accountConfig.setOptInType(expectedOptIn);
        accountConfig.setCreated(expectedOptInDate);
        accountConfig.setUpdated(expectedUpdatedDate);
        when(accountRepo.findById(eq(expectedAccount))).thenReturn(Optional.of(accountConfig));

        OrgConfig orgConfig = new OrgConfig(expectedOrg);
        orgConfig.setSyncEnabled(expectedSyncEnabled);
        orgConfig.setOptInType(expectedOptIn);
        orgConfig.setCreated(expectedOptInDate);
        orgConfig.setUpdated(expectedUpdatedDate);
        when(orgRepo.findById(eq(expectedOrg))).thenReturn(Optional.of(orgConfig));

        OptInConfig dto = controller.getOptInConfig(expectedAccount, expectedOrg);
        assertNotNull(dto.getData());
        assertNotNull(dto.getMeta());
        assertNotNull(dto.getData().getAccount());
        assertNotNull(dto.getData().getOrg());

        assertEquals(expectedAccount, dto.getMeta().getAccountNumber());
        assertEquals(expectedOrg, dto.getMeta().getOrgId());

        OptInConfigDataAccount accountDto = dto.getData().getAccount();
        assertEquals(accountDto.getAccountNumber(), expectedAccount);
        assertEquals(accountDto.getTallySyncEnabled(), expectedSyncEnabled);
        assertEquals(accountDto.getTallyReportingEnabled(), expectedReportingEnabled);
        assertEquals(accountDto.getOptInType(), expectedOptIn.name());
        assertEquals(accountDto.getCreated(), expectedOptInDate);
        assertEquals(accountDto.getLastUpdated(), expectedUpdatedDate);

        OptInConfigDataOrg orgDto = dto.getData().getOrg();
        assertEquals(orgDto.getOrgId(), expectedOrg);
        assertEquals(orgDto.getConduitSyncEnabled(), expectedSyncEnabled);
        assertEquals(orgDto.getOptInType(), expectedOptIn.name());
        assertEquals(orgDto.getCreated(), expectedOptInDate);
        assertEquals(orgDto.getLastUpdated(), expectedUpdatedDate);

        assertTrue(dto.getData().getOptInComplete());
    }

    @Test
    public void optInConfigNotCompleteWhenOnlyAccountConfigExists() {
        String expectedAccountNumber = "account123456";
        String expectedOrgId = "owner123456";
        AccountConfig accountConfig = new AccountConfig(expectedAccountNumber);
        when(accountRepo.findById(eq(expectedAccountNumber))).thenReturn(Optional.of(accountConfig));
        when(orgRepo.findById(eq(expectedOrgId))).thenReturn(Optional.empty());

        OptInConfig dto = controller.getOptInConfig(expectedAccountNumber, expectedOrgId);
        assertNotNull(dto.getData().getAccount());
        assertNull(dto.getData().getOrg());
        assertFalse(dto.getData().getOptInComplete());
    }

    @Test
    public void optInConfigNotCompleteWhenOnlyOrgConfigExists() {
        String expectedAccountNumber = "account123456";
        String expectedOrgId = "owner123456";

        OrgConfig orgConfig = new OrgConfig(expectedOrgId);
        when(accountRepo.findById(eq(expectedAccountNumber))).thenReturn(Optional.empty());
        when(orgRepo.findById(eq(expectedOrgId))).thenReturn(Optional.of(orgConfig));

        OptInConfig dto = controller.getOptInConfig(expectedAccountNumber, expectedOrgId);
        assertNull(dto.getData().getAccount());
        assertNotNull(dto.getData().getOrg());
        assertFalse(dto.getData().getOptInComplete());
    }

    private AccountConfig setupExistingAccountConfig() {
        AccountConfig config = new AccountConfig("my-account");
        config.setReportingEnabled(true);
        config.setSyncEnabled(true);
        config.setCreated(clock.now().minusDays(1));
        config.setUpdated(clock.now().minusDays(1));
        config.setOptInType(OptInType.DB);
        when(accountRepo.findById(eq("my-account"))).thenReturn(Optional.of(config));
        return config;
    }

    private OrgConfig setupExistingOrgConfig() {
        OrgConfig config = new OrgConfig("my-org");
        config.setSyncEnabled(true);
        config.setUpdated(clock.now().minusDays(1));
        config.setCreated(clock.now().minusDays(1));
        config.setOptInType(OptInType.DB);
        when(orgRepo.findById(eq("my-org"))).thenReturn(Optional.of(config));
        return config;
    }
}
