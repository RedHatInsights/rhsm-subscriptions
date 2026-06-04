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
package org.candlepin.subscriptions.conduit.job;

import java.util.stream.Stream;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Refactored from TaskManager */
@Component
public class OrgSyncTaskManager {
  private static final Logger log = LoggerFactory.getLogger(OrgSyncTaskManager.class);

  private final TaskQueue queue;
  private final TaskQueueProperties taskQueueProperties;
  private final OrgSyncProperties orgSyncProperties;
  private final DatabaseOrgList orgList;

  public OrgSyncTaskManager(
      TaskQueue queue,
      @Qualifier("conduitTaskQueueProperties") TaskQueueProperties taskQueueProperties,
      OrgSyncProperties orgSyncProperties,
      DatabaseOrgList orgList) {

    this.queue = queue;
    this.taskQueueProperties = taskQueueProperties;
    this.orgSyncProperties = orgSyncProperties;
    this.orgList = orgList;
  }

  /**
   * Initiates a task that will update the inventory of the specified organization's ID.
   *
   * @param orgId the ID of the org in which to update.
   */
  public void updateOrgInventory(String orgId) {
    updateOrgInventory(orgId, null);
  }

  /**
   * Initiates a task that will update the inventory of the specified organization's ID.
   *
   * @param orgId the ID of the org in which to update.
   * @param offset the offset to start at
   */
  @SuppressWarnings("indentation")
  public void updateOrgInventory(String orgId, String offset) {
    queue.enqueue(
        TaskDescriptor.builder(TaskType.UPDATE_ORG_INVENTORY, taskQueueProperties.getTopic(), orgId)
            .setSingleValuedArg("org_id", orgId)
            .setSingleValuedArg("offset", offset)
            .build());
  }

  /** Queue up tasks for each configured org. */
  @Transactional
  public void syncFullOrgList() {
    Stream<String> orgsToSync;

    orgsToSync = orgList.getOrgsToSync();

    if (orgSyncProperties.getLimit() != null) {
      Integer limit = orgSyncProperties.getLimit();
      orgsToSync = orgsToSync.limit(limit);
      log.info("Limiting orgs to sync to {}", limit);
    }
    long count =
        orgsToSync
            .map(
                org -> {
                  try {
                    updateOrgInventory(org);
                  } catch (Exception e) {
                    log.error("Could not update inventory for org: {}", org, e);
                  }
                  return null;
                })
            .count();
    log.info("Inventory update complete, synced {} orgs.", count);
  }
}
