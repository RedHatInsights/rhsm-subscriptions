/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.insights.jmx;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.hamcrest.MockitoHamcrest.*;

import org.candlepin.insights.FixedClockConfiguration;
import org.candlepin.insights.controller.InventoryController;
import org.candlepin.insights.orgsync.db.OrgConfigRepository;
import org.candlepin.insights.orgsync.db.model.OrgConfig;
import org.candlepin.insights.task.TaskManager;
import org.candlepin.insights.util.ApplicationClock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

@ExtendWith(MockitoExtension.class)
class RhsmConduitJmxBeanTest {
    ApplicationClock clock = new FixedClockConfiguration().fixedClock();

    @Mock
    InventoryController controller;

    @Mock
    OrgConfigRepository repo;

    @Mock
    TaskManager tasks;

    @Test
    void testHandlesCommas() {
        RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
        jmxBean.addOrgsToSyncList("1,2,3");
        verify(repo).saveAll(matchOrgs("1", "2", "3"));
    }

    @Test
    void testHandlesWhitespace() {
        RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
        jmxBean.addOrgsToSyncList("1 2\n3");
        verify(repo).saveAll(matchOrgs("1", "2", "3"));
    }

    @Test
    void testHandlesAllDelimitersTogether() {
        RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
        jmxBean.addOrgsToSyncList("1,2 3\n4");
        verify(repo).saveAll(matchOrgs("1", "2", "3", "4"));
    }

    @Test
    void testHandlesAllDelimitersTogetherInCombination() {
        RhsmConduitJmxBean jmxBean = new RhsmConduitJmxBean(controller, repo, tasks, clock);
        jmxBean.addOrgsToSyncList("1,\n2 ,3 \n4 ,\n5");
        verify(repo).saveAll(matchOrgs("1", "2", "3", "4", "5"));
    }

    private Iterable<? extends OrgConfig> matchOrgs(String... orgIds) {
        return argThat(
            containsInAnyOrder(
                Arrays.stream(orgIds)
                    .map(orgId -> OrgConfig.fromJmx(orgId, clock.now()))
                    .toArray(OrgConfig[]::new)
            )
        );
    }

}
