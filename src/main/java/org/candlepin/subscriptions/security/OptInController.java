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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.AccountConfig;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.user.AccountService;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigData;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataAccount;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigDataOrg;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Responsible for all opt-in functionality logic. Provides a means to tie both account and org
 * configuration update logic together to support the opt-in behaviour.
 */
@Component
public class OptInController {
  private static final Logger log = LoggerFactory.getLogger(OptInController.class);

  private AccountConfigRepository accountConfigRepository;
  private OrgConfigRepository orgConfigRepository;
  private ApplicationClock clock;
  private AccountService accountService;

  @Autowired
  public OptInController(
      ApplicationClock clock,
      AccountConfigRepository accountConfigRepo,
      OrgConfigRepository orgConfigRepo,
      AccountService accountService) {
    this.clock = clock;
    this.accountConfigRepository = accountConfigRepo;
    this.orgConfigRepository = orgConfigRepo;
    this.accountService = accountService;
  }

  // Separate isolated transaction needed in order to prevent opt-in errors rolling back metrics
  // updates
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public OptInConfig optIn(String accountNumber, String orgId, OptInType optInType) {
    return performOptIn(accountNumber, orgId, optInType);
  }

  private OptInConfig performOptIn(String accountNumber, String orgId, OptInType optInType) {
    OffsetDateTime now = clock.now();

    Optional<AccountConfig> accountData =
        accountConfigRepository.createOrUpdateAccountConfig(accountNumber, orgId, now, optInType);
    Optional<OrgConfig> orgData =
        orgConfigRepository.createOrUpdateOrgConfig(orgId, now, optInType);
    return buildDto(
        buildMeta(accountNumber, orgId),
        buildOptInAccountDTO(accountData),
        buildOptInOrgDTO(orgData));
  }

  // Separate isolated transaction needed in order to prevent opt-in errors rolling back metrics
  // updates
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void optInByAccountNumber(String accountNumber, OptInType optInType) {
    if (accountConfigRepository.existsByAccountNumber(accountNumber)) {
      return;
    }
    String orgId = accountService.lookupOrgId(accountNumber);
    log.info("Opting in account/orgId: {}/{}", accountNumber, orgId);
    performOptIn(accountNumber, orgId, optInType);
  }

  // Separate isolated transaction needed in order to prevent opt-in errors rolling back metrics
  // updates
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void optInByOrgId(String orgId, OptInType optInType) {
    if (!accountConfigRepository.existsById(orgId)) {
      log.info("Opting in orgId={}", orgId);
      // NOTE Passing null here should be cleaned up once account number
      // support is completely removed from opt-in.
      // https://issues.redhat.com/browse/SWATCH-662
      performOptIn(null, orgId, optInType).getData().getAccount().getAccountNumber();
    }
  }

  @Transactional
  public void optOut(String orgId) {
    if (accountConfigRepository.existsByOrgId(orgId)) {
      accountConfigRepository.deleteByOrgId(orgId);
    }

    if (orgConfigRepository.existsById(orgId)) {
      orgConfigRepository.deleteById(orgId);
    }
  }

  @Transactional
  public OptInConfig getOptInConfig(String accountNumber, String orgId) {
    Optional<AccountConfig> accountConfig =
        StringUtils.hasText(accountNumber)
            ? accountConfigRepository.findByOrgId(orgId)
            : Optional.empty();
    Optional<OrgConfig> orgConfig =
        StringUtils.hasText(orgId) ? orgConfigRepository.findById(orgId) : Optional.empty();
    return buildDto(
        buildMeta(accountNumber, orgId),
        buildOptInAccountDTO(accountConfig),
        buildOptInOrgDTO(orgConfig));
  }

  @Transactional
  public OptInConfig getOptInConfigForAccountNumber(String accountNumber) {
    return getOptInConfig(accountNumber, accountService.lookupOrgId(accountNumber));
  }

  @Transactional
  public OptInConfig getOptInConfigForOrgId(String orgId) {
    return getOptInConfig(accountService.lookupAccountNumber(orgId), orgId);
  }

  private OptInConfig buildDto(
      OptInConfigMeta meta, OptInConfigDataAccount accountData, OptInConfigDataOrg orgData) {
    return new OptInConfig()
        .data(
            new OptInConfigData()
                .account(accountData)
                .org(orgData)
                .optInComplete(Objects.nonNull(orgData)))
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
        .optInType(config.getOptInType() == null ? null : config.getOptInType().name())
        .created(config.getCreated())
        .lastUpdated(config.getUpdated());
  }
}
