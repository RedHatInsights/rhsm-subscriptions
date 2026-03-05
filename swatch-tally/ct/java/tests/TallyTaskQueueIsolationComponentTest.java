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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.TASKS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY_HOURLY_TASKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import api.TaskMessage;
import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.TallyTestProducts;

/**
 * Verifies that hourly and nightly tally tasks are produced to separate Kafka topics so that
 * hourly runs are not backed up behind the multi-hour nightly batch (swatch-tally is Spring-based;
 * other swatch-* CTs in this repo are Quarkus-based).
 *
 * <p>In Clowder environments the main tasks topic name may be suffixed (e.g. {@code
 * ...-env-swatch-tally}). Set {@code TASKS_TOPIC} in the test environment to that name so the
 * nightly test can subscribe to the same topic the app produces to.
 */
public class TallyTaskQueueIsolationComponentTest extends BaseTallyComponentTest {

  /** Resolve tasks topic so CT works when Clowder rewrites the name. */
  private static final String TASKS_TOPIC =
      Optional.ofNullable(System.getenv("TASKS_TOPIC")).orElse(TASKS);

  @BeforeAll
  static void subscribeToTaskTopics() {
    kafkaBridge.subscribeToTopic(TALLY_HOURLY_TASKS);
    kafkaBridge.subscribeToTopic(TASKS_TOPIC);
  }

  @Test
  public void shouldProduceHourlySnapshotTasksToTallyHourlyTasksTopic() {
    // Given: An org is opted in for tally
    service.createOptInConfig(orgId);

    // When: Hourly snapshots are triggered for all orgs
    service.triggerHourlySnapshotsForAllOrgs();

    // Then: UPDATE_HOURLY_SNAPSHOTS task is produced to the tally-hourly-tasks topic
    List<TaskMessage> hourlyTasks =
        kafkaBridge.waitForKafkaMessage(
            TALLY_HOURLY_TASKS,
            new DefaultMessageValidator<>(
                msg ->
                    "UPDATE_HOURLY_SNAPSHOTS".equals(msg.getType())
                        && msg.getArgs() != null
                        && msg.getArgs().get("orgId") != null
                        && msg.getArgs().get("orgId").contains(orgId),
                TaskMessage.class),
            1);

    assertFalse(
        hourlyTasks.isEmpty(),
        "Expected at least one UPDATE_HOURLY_SNAPSHOTS task on tally-hourly-tasks topic");
    assertEquals(
        "UPDATE_HOURLY_SNAPSHOTS",
        hourlyTasks.get(0).getType(),
        "Task on tally-hourly-tasks topic should be UPDATE_HOURLY_SNAPSHOTS");
    assertEquals(
        orgId,
        hourlyTasks.get(0).getArgs().get("orgId").get(0),
        "Task should be for the opted-in org");
  }

  @Test
  public void shouldProduceNightlySnapshotTasksToMainTasksTopic() {
    // Given: Nightly tally host buckets are seeded for an org
    helpers.seedNightlyTallyHostBuckets(
        orgId,
        TallyTestProducts.RHEL_FOR_X86_ELS_UNCONVERTED.productTag(),
        UUID.randomUUID().toString(),
        service);

    // When: Async tally is triggered for the org (enqueues to main tasks topic)
    service.tallyOrgAsync(orgId);

    // Then: UPDATE_SNAPSHOTS task is produced to the main tasks topic
    List<TaskMessage> nightlyTasks =
        kafkaBridge.waitForKafkaMessage(
            TASKS_TOPIC,
            new DefaultMessageValidator<>(
                msg ->
                    "UPDATE_SNAPSHOTS".equals(msg.getType())
                        && msg.getArgs() != null
                        && msg.getArgs().get("orgs") != null
                        && msg.getArgs().get("orgs").contains(orgId),
                TaskMessage.class),
            1);

    assertFalse(
        nightlyTasks.isEmpty(), "Expected at least one UPDATE_SNAPSHOTS task on tasks topic");
    assertEquals(
        "UPDATE_SNAPSHOTS",
        nightlyTasks.get(0).getType(),
        "Task on tasks topic should be UPDATE_SNAPSHOTS");
  }
}
