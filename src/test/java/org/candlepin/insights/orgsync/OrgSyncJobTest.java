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
package org.candlepin.insights.orgsync;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.insights.task.TaskManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


@ExtendWith(MockitoExtension.class)
public class OrgSyncJobTest {

    @Mock
    private TaskManager tasks;

    @Mock
    private OrgListStrategy orgListStrategy;

    @Test
    public void ensureUpdateIsRunForEachOrg() throws Exception {
        List<String> expectedOrgs = Arrays.asList("org_a", "org_b");
        when(orgListStrategy.getOrgsToSync()).thenReturn(expectedOrgs);

        OrgSyncJob job = new OrgSyncJob(orgListStrategy, tasks);
        job.executeInternal(null);

        verify(tasks, times(1)).updateOrgInventory(eq("org_a"));
        verify(tasks, times(1)).updateOrgInventory(eq("org_b"));
    }

    @Test
    public void ensureErrorOnUpdateContinuesWithoutFailure() throws Exception {
        List<String> expectedOrgs = Arrays.asList("org_a", "org_b");
        when(orgListStrategy.getOrgsToSync()).thenReturn(expectedOrgs);

        doThrow(new RuntimeException("Forced!")).when(tasks).updateOrgInventory(eq("org_a"));

        OrgSyncJob job = new OrgSyncJob(orgListStrategy, tasks);
        job.executeInternal(null);

        verify(tasks, times(1)).updateOrgInventory(eq("org_a"));
        verify(tasks, times(1)).updateOrgInventory(eq("org_b"));
    }

    @Test
    public void ensureNoUpdatesWhenOrgListCanNotBeRetreived() throws Exception {
        doThrow(new IOException("Forced!")).when(orgListStrategy).getOrgsToSync();

        OrgSyncJob job = new OrgSyncJob(orgListStrategy, tasks);
        assertThrows(JobExecutionException.class, () -> {
            job.executeInternal(null);
        });

        verify(tasks, never()).updateOrgInventory(any());
    }
}
