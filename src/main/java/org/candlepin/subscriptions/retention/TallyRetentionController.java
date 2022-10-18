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
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cleans up stale tally snapshots for an account. */
@Component
public class TallyRetentionController {
  private static final Logger log = LoggerFactory.getLogger(TallyRetentionController.class);

  private final TallySnapshotRepository tallySnapshotRepository;
  private final EventRecordRepository eventRecordRepository;
  private final AccountConfigRepository accountConfigRepository;
  private final TallyRetentionPolicy policy;
  private final EventRecordsRetentionProperties eventRecordsRetentionProperties;

  @Autowired
  public TallyRetentionController(
      TallySnapshotRepository tallySnapshotRepository,
      EventRecordRepository eventRecordRepository,
      AccountConfigRepository accountConfigRepository,
      TallyRetentionPolicy policy,
      EventRecordsRetentionProperties eventRecordsRetentionProperties) {
    this.tallySnapshotRepository = tallySnapshotRepository;
    this.eventRecordRepository = eventRecordRepository;
    this.accountConfigRepository = accountConfigRepository;
    this.policy = policy;
    this.eventRecordsRetentionProperties = eventRecordsRetentionProperties;
  }

  @Transactional
  public void purgeSnapshots() {
    try (Stream<String> orgList = accountConfigRepository.findSyncEnabledOrgs()) {
      orgList.forEach(this::cleanStaleSnapshotsForOwnerId);
    }
  }

  public void cleanStaleSnapshotsForOwnerId(String orgId) {
    for (Granularity granularity : Granularity.values()) {
      OffsetDateTime cutoffDate = policy.getCutoffDate(granularity);
      if (cutoffDate == null) {
        continue;
      }
      tallySnapshotRepository.deleteAllByOwnerIdAndGranularityAndSnapshotDateBefore(
          orgId, granularity, cutoffDate);
    }
  }

  public void purgeOldEventRecords() {
    var eventRetentionDuration = eventRecordsRetentionProperties.getEventRetentionDuration();

    OffsetDateTime cutoffDate =
        OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minus(eventRetentionDuration);

    log.info("Purging event records older than Duration {}", cutoffDate);

    eventRecordRepository.deleteEventRecordsByTimestampBefore(cutoffDate);
  }
}
