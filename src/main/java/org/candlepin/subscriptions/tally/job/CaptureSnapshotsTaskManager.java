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
package org.candlepin.subscriptions.tally.job;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.tally.TallyTaskQueueConfiguration;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskManagerException;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.candlepin.subscriptions.util.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Producer of tally snapshot production tasks.
 *
 * <p>Any component that needs to enqueue tally production tasks should inject this component.
 */
@Component
@Import({TaskProducerConfiguration.class, TallyTaskQueueConfiguration.class})
public class CaptureSnapshotsTaskManager {
  private static final Logger log = LoggerFactory.getLogger(CaptureSnapshotsTaskManager.class);

  private final TaskQueueProperties taskQueueProperties;
  private final TaskQueue queue;
  private final OrgConfigRepository orgRepo;

  @Autowired
  public CaptureSnapshotsTaskManager(
      @Qualifier("tallyTaskQueueProperties") TaskQueueProperties tallyTaskQueueProperties,
      TaskQueue queue,
      OrgConfigRepository orgRepo) {

    this.taskQueueProperties = tallyTaskQueueProperties;
    this.queue = queue;
    this.orgRepo = orgRepo;
  }

  /**
   * Initiates a task that will update the snapshots for the specified org.
   *
   * @param orgId the account number in which to update.
   */
  @SuppressWarnings("indentation")
  public void updateOrgSnapshots(String orgId) {
    LogUtils.addOrgIdToMdc(orgId);
    queue.enqueue(
        TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic(), orgId)
            .setSingleValuedArg("orgs", orgId)
            .build());
    LogUtils.clearOrgIdFromMdc();
  }

  /**
   * Queue up tasks to update the snapshots for all configured orgs.
   *
   * @throws TaskManagerException
   */
  @Transactional
  public void updateSnapshotsForAllOrg() {
    try (Stream<String> orgStream = orgRepo.findSyncEnabledOrgs()) {
      log.info("Queuing all org snapshot production in batches of size one");

      AtomicInteger count = new AtomicInteger(0);
      orgStream.forEach(
          org -> {
            LogUtils.addOrgIdToMdc(org);
            queue.enqueue(
                TaskDescriptor.builder(
                        TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic(), org)
                    .setSingleValuedArg("orgs", org)
                    .build());
            count.addAndGet(1);
          });

      LogUtils.clearOrgIdFromMdc();
      log.info("Done queuing snapshot production for {} org list.", count.intValue());
    } catch (Exception e) {
      throw new TaskManagerException("Could not list org for update snapshot task generation", e);
    }
  }

  public void tallyOrgByHourly(String orgId) {
    LogUtils.addOrgIdToMdc(orgId);
    log.info("Queuing hourly snapshot production for orgId {}", orgId);
    queue.enqueue(
        TaskDescriptor.builder(
                TaskType.UPDATE_HOURLY_SNAPSHOTS, taskQueueProperties.getTopic(), orgId)
            .setSingleValuedArg("orgId", orgId)
            .build());
    LogUtils.clearOrgIdFromMdc();
  }

  @Transactional
  public void updateHourlySnapshotsForAllOrgs() {
    try (Stream<String> orgStream = orgRepo.findSyncEnabledOrgs()) {
      AtomicInteger count = new AtomicInteger(0);
      log.info("Queuing all org hourly snapshot in batches of size one");

      orgStream.forEach(
          orgId -> {
            tallyOrgByHourly(orgId);
            count.addAndGet(1);
          });

      log.info("Done queuing hourly snapshot production for {} accounts.", count.intValue());

    } catch (Exception e) {
      throw new TaskManagerException("Could not list orgs for update snapshot task generation", e);
    }
  }
}
