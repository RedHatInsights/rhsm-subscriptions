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
package org.candlepin.subscriptions.tally.tasks;

import jakarta.validation.constraints.Size;
import java.util.List;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.task.Task;
import org.candlepin.subscriptions.util.LogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;

/** Updates the usage snapshots for a given org. */
@Validated
public class UpdateOrgSnapshotsTask implements Task {
  private static final Logger log = LoggerFactory.getLogger(UpdateOrgSnapshotsTask.class);

  private final List<String> orgList;
  private final TallySnapshotController snapshotController;

  public UpdateOrgSnapshotsTask(
      TallySnapshotController snapshotController, @Size(min = 1, max = 1) List<String> orgList) {
    this.snapshotController = snapshotController;
    this.orgList = orgList;
  }

  @Override
  public void execute() {
    String org = orgList.get(0);
    LogUtils.addOrgIdToMdc(org);
    log.info("Updating snapshots for org {}.", org);
    snapshotController.produceSnapshotsForOrg(org);
    LogUtils.clearOrgIdFromMdc();
  }
}
