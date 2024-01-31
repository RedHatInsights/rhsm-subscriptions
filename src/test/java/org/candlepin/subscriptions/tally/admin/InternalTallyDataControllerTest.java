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
package org.candlepin.subscriptions.tally.admin;

import static org.mockito.Mockito.verify;

import org.candlepin.subscriptions.tally.AccountResetService;
import org.candlepin.subscriptions.tally.TallySnapshotController;
import org.candlepin.subscriptions.tally.billing.ContractsController;
import org.candlepin.subscriptions.tally.job.CaptureSnapshotsTaskManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class InternalTallyDataControllerTest {

  private static final String ORG_ID = "org1";

  @MockBean ContractsController contractsController;
  @MockBean AccountResetService accountResetService;
  @MockBean TallySnapshotController snapshotController;
  @MockBean CaptureSnapshotsTaskManager tasks;
  @Autowired InternalTallyDataController controller;

  @Test
  void testDeleteDataAssociatedWithOrg() {
    controller.deleteDataAssociatedWithOrg(ORG_ID);
    verify(contractsController).deleteContractsWithOrg(ORG_ID);
    verify(accountResetService).deleteDataForOrg(ORG_ID);
  }

  @Test
  void testTallyOrg() {
    controller.tallyOrg(ORG_ID);
    verify(tasks).updateOrgSnapshots(ORG_ID);
  }

  @Test
  void testTallyOrgSync() {
    controller.tallyOrgSync(ORG_ID);
    verify(snapshotController).produceSnapshotsForOrg(ORG_ID);
  }
}
