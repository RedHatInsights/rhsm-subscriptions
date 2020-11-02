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

import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigData;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataAccount;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataOrg;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigMeta;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;


/**
 * Responsible for all opt-in functionality logic. Provides a means to tie both
 * account and org configuration update logic together to support the opt-in
 * behaviour.
 */
@Component
public class OptInController {

    private AccountConfigRepository accountConfigRepository;
    private OrgConfigRepository orgConfigRepository;
    private ApplicationClock clock;

    @Autowired
    public OptInController(ApplicationClock clock, AccountConfigRepository accountConfigRepo,
        OrgConfigRepository orgConfigRepo) {
        this.clock = clock;
        this.accountConfigRepository = accountConfigRepo;
        this.orgConfigRepository = orgConfigRepo;
    }

    @Transactional
    public OptInConfig optIn(String accountNumber, String orgId, OptInType optInType, boolean enableTallySync,
        boolean enableTallyReporting, boolean enableConduitSync) {
        OffsetDateTime now = clock.now();

        Optional<AccountConfig> accountData =
            createOrUpdateAccountConfig(accountNumber, now, optInType, enableTallySync, enableTallyReporting);
        Optional<OrgConfig> orgData = createOrUpdateOrgConfig(orgId, now, optInType, enableConduitSync);
        return buildDto(
            buildMeta(accountNumber, orgId),
            buildOptInAccountDTO(accountData),
            buildOptInOrgDTO(orgData)
        );
    }

    @Transactional
    public void optOut(String accountNumber, String orgId) {
        if (accountConfigRepository.existsById(accountNumber)) {
            accountConfigRepository.deleteById(accountNumber);
        }

        if (orgConfigRepository.existsById(orgId)) {
            orgConfigRepository.deleteById(orgId);
        }

    }

    @Transactional
    public OptInConfig getOptInConfig(String accountNumber, String orgId) {
        return buildDto(
            buildMeta(accountNumber, orgId),
            buildOptInAccountDTO(accountConfigRepository.findById(accountNumber)),
            buildOptInOrgDTO(orgConfigRepository.findById(orgId))
        );
    }

    private Optional<AccountConfig> createOrUpdateAccountConfig(String account, OffsetDateTime current,
        OptInType optInType, boolean enableSync, boolean enableReporting) {
        Optional<AccountConfig> found = accountConfigRepository.findById(account);
        AccountConfig accountConfig = found.orElse(new AccountConfig(account));
        if (!found.isPresent()) {
            accountConfig.setOptInType(optInType);
            accountConfig.setCreated(current);
        }
        accountConfig.setSyncEnabled(enableSync);
        accountConfig.setReportingEnabled(enableReporting);
        accountConfig.setUpdated(current);
        return Optional.of(accountConfigRepository.save(accountConfig));
    }

    private Optional<OrgConfig> createOrUpdateOrgConfig(String orgId, OffsetDateTime current,
        OptInType optInType, boolean enableSync) {
        Optional<OrgConfig> found = orgConfigRepository.findById(orgId);
        OrgConfig orgConfig = found.orElse(new OrgConfig(orgId));
        if (!found.isPresent()) {
            orgConfig.setOptInType(optInType);
            orgConfig.setCreated(current);
        }
        orgConfig.setSyncEnabled(enableSync);
        orgConfig.setUpdated(current);
        return Optional.of(orgConfigRepository.save(orgConfig));
    }

    private OptInConfig buildDto(OptInConfigMeta meta, OptInConfigDataAccount accountData,
        OptInConfigDataOrg orgData) {
        return new OptInConfig()
            .data(
                new OptInConfigData().account(accountData)
                .org(orgData)
                .optInComplete(accountData != null && orgData != null)
            )
            .meta(meta);
    }

    private OptInConfigMeta buildMeta(String accountNumber, String orgId) {
        return new OptInConfigMeta().accountNumber(accountNumber).orgId(orgId);
    }

    private OptInConfigDataAccount buildOptInAccountDTO(Optional<AccountConfig> optionalConfig) {
        if (!optionalConfig.isPresent()) {
            return null;
        }
        AccountConfig config = optionalConfig.get();
        return new OptInConfigDataAccount()
            .accountNumber(config.getAccountNumber())
            .tallySyncEnabled(config.getSyncEnabled())
            .tallyReportingEnabled(config.getReportingEnabled())
            .optInType(config.getOptInType() == null ? null : config.getOptInType().name())
            .created(config.getCreated())
            .lastUpdated(config.getUpdated());
    }

    private OptInConfigDataOrg buildOptInOrgDTO(Optional<OrgConfig> optionalConfig) {
        if (!optionalConfig.isPresent()) {
            return null;
        }

        OrgConfig config = optionalConfig.get();
        return new OptInConfigDataOrg()
            .orgId(config.getOrgId())
            .conduitSyncEnabled(config.getSyncEnabled())
            .optInType(config.getOptInType() == null ? null : config.getOptInType().name())
            .created(config.getCreated())
            .lastUpdated(config.getUpdated());
    }
}
