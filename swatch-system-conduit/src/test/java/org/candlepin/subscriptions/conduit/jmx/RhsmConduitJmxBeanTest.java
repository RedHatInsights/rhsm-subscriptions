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
package org.candlepin.subscriptions.conduit.jmx;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import java.util.Arrays;
import org.candlepin.subscriptions.FixedClockConfiguration;
import org.candlepin.subscriptions.conduit.InventoryController;
import org.candlepin.subscriptions.conduit.job.OrgSyncTaskManager;
import org.candlepin.subscriptions.conduit.rhsm.client.ApiException;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.candlepin.subscriptions.exception.MissingAccountNumberException;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RhsmConduitJmxBeanTest {
  ApplicationClock clock = new FixedClockConfiguration().fixedClock();

  @Mock InventoryController controller;

  @Mock OrgConfigRepository repo;

  @Mock OrgSyncTaskManager tasks;

  @Test
  void testHandlesCommas() throws RhsmJmxException {
    RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
    jmxBean.addOrgsToSyncList("1,2,3");
    verify(repo).saveAll(matchOrgs("1", "2", "3"));
  }

  @Test
  void testHandlesWhitespace() throws RhsmJmxException {
    RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
    jmxBean.addOrgsToSyncList("1 2\n3");
    verify(repo).saveAll(matchOrgs("1", "2", "3"));
  }

  @Test
  void testHandlesAllDelimitersTogether() throws RhsmJmxException {
    RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
    jmxBean.addOrgsToSyncList("1,2 3\n4");
    verify(repo).saveAll(matchOrgs("1", "2", "3", "4"));
  }

  @Test
  void testHandlesAllDelimitersTogetherInCombination() throws RhsmJmxException {
    RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
    jmxBean.addOrgsToSyncList("1,\n2 ,3 \n4 ,\n5");
    verify(repo).saveAll(matchOrgs("1", "2", "3", "4", "5"));
  }

  @Test
  void testGetInventoryCallsInventoryController()
      throws MissingAccountNumberException, ApiException, RhsmJmxException {
    RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
    jmxBean.getInventoryForOrg("org-1234", null);
    Mockito.verify(controller).getInventoryForOrg("org-1234", null);
  }

  @Test
  void testUpdateInventoryDelegatesToTask()
      throws RhsmJmxException, MissingAccountNumberException, ApiException {
    RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
    jmxBean.syncOrg("org-1234");
    Mockito.verify(controller).updateInventoryForOrg("org-1234");
  }

  private Iterable<? extends OrgConfig> matchOrgs(String... orgIds) {
    return argThat(
        containsInAnyOrder(
            Arrays.stream(orgIds)
                .map(orgId -> OrgConfig.fromJmx(orgId, clock.now()))
                .toArray(OrgConfig[]::new)));
  }
}
