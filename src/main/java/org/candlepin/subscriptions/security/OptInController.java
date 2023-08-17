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
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.utilization.api.model.OptInConfig;
import org.candlepin.subscriptions.utilization.api.model.OptInConfigData;
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

  private OrgConfigRepository orgConfigRepository;
  private ApplicationClock clock;

  @Autowired
  public OptInController(ApplicationClock clock, OrgConfigRepository orgConfigRepo) {
    this.clock = clock;
    this.orgConfigRepository = orgConfigRepo;
  }

  // Separate isolated transaction needed in order to prevent opt-in errors rolling back metrics
  // updates
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public OptInConfig optIn(String orgId, OptInType optInType) {
    return performOptIn(orgId, optInType);
  }

  private OptInConfig performOptIn(String orgId, OptInType optInType) {
    OffsetDateTime now = clock.now();
    Optional<OrgConfig> orgData =
        orgConfigRepository.createOrUpdateOrgConfig(orgId, now, optInType);
    return buildDto(buildMeta(orgId), buildOptInOrgDTO(orgData));
  }

  // Separate isolated transaction needed in order to prevent opt-in errors rolling back metrics
  // updates
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void optInByOrgId(String orgId, OptInType optInType) {
    if (Boolean.FALSE.equals(orgConfigRepository.existsByOrgId(orgId))) {
      log.info("Opting in orgId={}", orgId);
      // NOTE Passing null here should be cleaned up once account number
      // support is completely removed from opt-in.
      // https://issues.redhat.com/browse/SWATCH-662
      performOptIn(orgId, optInType);
    }
  }

  @Transactional
  public void optOut(String orgId) {
    if (orgConfigRepository.existsById(orgId)) {
      orgConfigRepository.deleteById(orgId);
    }
  }

  @Transactional
  public OptInConfig getOptInConfig(String orgId) {
    Optional<OrgConfig> orgConfig =
        StringUtils.hasText(orgId) ? orgConfigRepository.findById(orgId) : Optional.empty();
    return buildDto(buildMeta(orgId), buildOptInOrgDTO(orgConfig));
  }

  @Transactional
  public OptInConfig getOptInConfigForOrgId(String orgId) {
    return getOptInConfig(orgId);
  }

  private OptInConfig buildDto(OptInConfigMeta meta, OptInConfigDataOrg orgData) {
    return new OptInConfig()
        .data(new OptInConfigData().org(orgData).optInComplete(Objects.nonNull(orgData)))
        .meta(meta);
  }

  private OptInConfigMeta buildMeta(String orgId) {
    return new OptInConfigMeta().orgId(orgId);
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
