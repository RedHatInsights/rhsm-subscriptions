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

import io.micrometer.core.annotation.Timed;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Cleans up stale tally snapshots for an account. */
@Slf4j
@Component
@AllArgsConstructor
public class TallyRetentionController {

  private final TallySnapshotRepository tallySnapshotRepository;
  private final OrgConfigRepository orgConfigRepository;
  private final TallyRetentionPolicy policy;

  @Timed("rhsm-subscriptions.snapshots.purge")
  @Async("purgeTallySnapshotsJobExecutor")
  public void purgeSnapshotsAsync() {
    try {
      log.info("Starting tally snapshot purge.");
      PageRequest pageRequest = PageRequest.ofSize(policy.getOrganizationsBatchLimit());
      Page<OrgConfig> page = orgConfigRepository.findAll(pageRequest);
      while (!page.isEmpty()) {
        page.getContent().forEach(org -> purgeSnapshotsByOrg(org.getOrgId()));

        pageRequest = pageRequest.next();
        page = orgConfigRepository.findAll(pageRequest);
      }

      log.info("Tally snapshot purge completed successfully.");
    } catch (Exception e) {
      log.error("Unable to purge tally snapshots: {}", e.getMessage());
    }
  }

  private void purgeSnapshotsByOrg(String orgId) {
    log.debug("Running tally snapshot purge for orgId {}", orgId);
    for (Granularity granularity : Granularity.values()) {
      OffsetDateTime cutoffDate = policy.getCutoffDate(granularity);
      if (cutoffDate == null) {
        continue;
      }

      long count =
          tallySnapshotRepository.countAllByGranularityAndSnapshotDateBefore(
              orgId, granularity, cutoffDate);
      while (count > 0) {
        tallySnapshotRepository.deleteAllByGranularityAndSnapshotDateBefore(
            orgId, granularity.name(), cutoffDate, policy.getSnapshotsToDeleteInBatches());
        count -= policy.getSnapshotsToDeleteInBatches();
      }
    }
  }
}
