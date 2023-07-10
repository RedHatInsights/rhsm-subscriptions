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
package org.candlepin.subscriptions.retention;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.BillableUsageRemittanceRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cleans up stale tally snapshots for an account. */
@Component
public class RemittanceRetentionController {
  private static final Logger log = LoggerFactory.getLogger(RemittanceRetentionController.class);

  private final BillableUsageRemittanceRepository remittanceRepository;
  private final AccountConfigRepository accountConfigRepository;
  private final RemittanceRetentionPolicy policy;

  private final List<Granularity> granulartiesToPurge =
      List.of(Granularity.DAILY, Granularity.HOURLY);

  @Autowired
  public RemittanceRetentionController(
      BillableUsageRemittanceRepository remittanceRepository,
      AccountConfigRepository accountConfigRepository,
      RemittanceRetentionPolicy policy) {
    this.remittanceRepository = remittanceRepository;
    this.accountConfigRepository = accountConfigRepository;
    this.policy = policy;
  }

  @Async("purgeRemittancesJobExecutor")
  @Transactional
  public void purgeRemittancesAsync() {
    try {
      log.info("Starting remittances purge.");
      try (Stream<String> orgList = accountConfigRepository.findSyncEnabledOrgs()) {
        orgList.forEach(this::cleanStaleRemittancesForOrgId);
      }
      log.info("Remittance purge completed successfully.");
    } catch (Exception e) {
      log.error("Unable to purge remittances: {}", e.getMessage());
    }
  }

  public void cleanStaleRemittancesForOrgId(String orgId) {
    for (Granularity granularity : granulartiesToPurge) {
      OffsetDateTime cutoffDate = policy.getCutoffDate(granularity);
      if (cutoffDate == null) {
        continue;
      }
      remittanceRepository.deleteAllByKeyOrgIdAndKeyGranularityAndKeyRemittancePendingDateBefore(
          orgId, granularity, cutoffDate);
    }
  }
}
