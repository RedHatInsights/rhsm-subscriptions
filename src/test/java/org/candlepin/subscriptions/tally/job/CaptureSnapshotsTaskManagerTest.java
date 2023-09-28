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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Arrays;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskManagerException;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueue;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueueConsumerFactory;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestClockConfiguration.class)
@ActiveProfiles({"worker", "test"})
class CaptureSnapshotsTaskManagerTest {

  @MockBean ExecutorTaskQueue queue;

  @MockBean ExecutorTaskQueueConsumerFactory consumerFactory;

  @Autowired private CaptureSnapshotsTaskManager manager;

  @Autowired private TaskQueueProperties taskQueueProperties;

  @Autowired private ApplicationProperties appProperties;

  @Autowired ApplicationClock applicationClock;

  @MockBean private OrgConfigRepository orgRepo;

  public static final String ORG_ID = "org123";
  public static final String ACCOUNT = "foo123";

  @Test
  void testUpdateForSingleOrg() {
    manager.updateOrgSnapshots(ORG_ID);
    verify(queue).enqueue(createDescriptorOrg(ORG_ID));
  }

  @Test
  void ensureUpdateIsRunForEachOrg() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2");
    when(orgRepo.findSyncEnabledOrgs()).thenReturn(expectedOrgList.stream());

    manager.updateSnapshotsForAllOrg();

    verify(queue, times(1)).enqueue(createDescriptorOrg("o1"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o2"));
  }

  @Test
  void ensureOrgListIsPartitionedWhenSendingTaskMessages() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2", "o3", "o4");
    when(orgRepo.findSyncEnabledOrgs()).thenReturn(expectedOrgList.stream());

    manager.updateSnapshotsForAllOrg();

    // NOTE: Partition size is defined in test.properties
    verify(queue, times(1)).enqueue(createDescriptorOrg("o1"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o2"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o3"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o4"));
  }

  @Test
  void ensureLastOrgListPartitionIsIncludedWhenSendingTaskMessages() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2", "o3", "o4", "o5");
    when(orgRepo.findSyncEnabledOrgs()).thenReturn(expectedOrgList.stream());

    manager.updateSnapshotsForAllOrg();

    // NOTE: Partition size is defined in test.properties
    verify(queue, times(1)).enqueue(createDescriptorOrg("o1"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o2"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o3"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o4"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o5"));
  }

  @Test
  void ensureErrorOnUpdateContinuesWithoutFailure() throws Exception {
    List<String> expectedOrgList = Arrays.asList("o1", "o2", "o3", "o4", "o5", "o6");
    when(orgRepo.findSyncEnabledOrgs()).thenReturn(expectedOrgList.stream());

    doThrow(new RuntimeException("Forced!"))
        .when(queue)
        .enqueue(createDescriptorAccount(Arrays.asList("o3", "o4")));

    manager.updateSnapshotsForAllOrg();

    verify(queue, times(1)).enqueue(createDescriptorOrg("o1"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o2"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o3"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o4"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o5"));
    verify(queue, times(1)).enqueue(createDescriptorOrg("o6"));
  }

  @Test
  void ensureNoUpdatesWhenOrgListCanNotBeRetreived() throws Exception {
    doThrow(new RuntimeException()).when(orgRepo).findSyncEnabledOrgs();

    assertThrows(
        TaskManagerException.class,
        () -> {
          manager.updateSnapshotsForAllOrg();
        });

    verify(queue, never()).enqueue(any());
  }

  @Test
  void testHourlySnapshotForAllAccounts() throws Exception {
    List<String> expectedOrgs = Arrays.asList("o1", "o2");
    when(orgRepo.findSyncEnabledOrgs()).thenReturn(expectedOrgs.stream());

    manager.updateHourlySnapshotsForAllOrgs();

    expectedOrgs.forEach(
        orgId -> {
          verify(queue, times(1))
              .enqueue(
                  TaskDescriptor.builder(
                          TaskType.UPDATE_HOURLY_SNAPSHOTS, taskQueueProperties.getTopic(), null)
                      .setSingleValuedArg("orgId", orgId)
                      .build());
        });
  }

  private TaskDescriptor createDescriptorAccount(String account) {
    return createDescriptorAccount(List.of(account));
  }

  private TaskDescriptor createDescriptorAccount(List<String> accounts) {
    return TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic(), null)
        .setArg("accounts", accounts)
        .build();
  }

  private TaskDescriptor createDescriptorOrg(String org) {
    return createDescriptorOrg(List.of(org));
  }

  private TaskDescriptor createDescriptorOrg(List<String> orgs) {
    return TaskDescriptor.builder(TaskType.UPDATE_SNAPSHOTS, taskQueueProperties.getTopic(), null)
        .setArg("orgs", orgs)
        .build();
  }
}
