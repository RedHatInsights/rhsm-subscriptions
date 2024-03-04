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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.tally.TallyTaskQueueConfiguration;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskManagerException;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskProducerConfiguration;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.candlepin.subscriptions.task.queue.inmemory.ExecutorTaskQueue;
import org.candlepin.subscriptions.util.DateRange;
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

  private final ApplicationProperties appProperties;
  private final TaskQueueProperties taskQueueProperties;
  private final TaskQueue queue;
  private final ExecutorTaskQueue syncQueue;
  private final ApplicationClock applicationClock;
  private final OrgConfigRepository orgRepo;

  @Autowired
  public CaptureSnapshotsTaskManager(
      ApplicationProperties appProperties,
      @Qualifier("tallyTaskQueueProperties") TaskQueueProperties tallyTaskQueueProperties,
      TaskQueue queue,
      ExecutorTaskQueue syncQueue,
      ApplicationClock applicationClock,
      OrgConfigRepository orgRepo) {

    this.appProperties = appProperties;
    this.taskQueueProperties = tallyTaskQueueProperties;
    this.queue = queue;
    this.syncQueue = syncQueue;
    this.applicationClock = applicationClock;
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

  public void tallyOrgByHourly(String orgId, DateRange tallyRange, boolean sync) {
    LogUtils.addOrgIdToMdc(orgId);
    if (!applicationClock.isHourlyRange(tallyRange.getStartDate(), tallyRange.getEndDate())) {
      log.error(
          "Hourly snapshot production for orgId {} will not be queued. "
              + "Invalid start/end times specified.",
          orgId);
      throw new IllegalArgumentException(
          String.format(
              "Start/End times must be at the top of the hour: [%s -> %s]",
              tallyRange.getStartString(), tallyRange.getEndString()));
    }

    log.info(
        "Queuing hourly snapshot production for orgId {} between {} and {}",
        orgId,
        tallyRange.getStartString(),
        tallyRange.getEndString());

    var task =
        TaskDescriptor.builder(
                TaskType.UPDATE_HOURLY_SNAPSHOTS, taskQueueProperties.getTopic(), orgId)
            .setSingleValuedArg("orgId", orgId)
            .setSingleValuedArg("startDateTime", tallyRange.getStartString())
            .setSingleValuedArg("endDateTime", tallyRange.getEndString())
            .build();
    if (sync) {
      log.info("Synchronous hourly tally requested for orgId {}: {}", orgId, tallyRange);
      syncQueue.enqueue(task);
    } else {
      queue.enqueue(task);
    }

    LogUtils.clearOrgIdFromMdc();
  }

  @Transactional
  public void updateHourlySnapshotsForAllOrgs(Optional<DateRange> dateRange) {
    try (Stream<String> orgStream = orgRepo.findSyncEnabledOrgs()) {
      AtomicInteger count = new AtomicInteger(0);

      OffsetDateTime startDateTime;
      OffsetDateTime endDateTime;
      if (dateRange.isEmpty()) {
        // Default to NOW.
        Duration metricRange = appProperties.getMetricLookupRangeDuration();
        Duration prometheusLatencyDuration = appProperties.getPrometheusLatencyDuration();
        Duration hourlyTallyOffsetMinutes = appProperties.getHourlyTallyOffset();

        endDateTime =
            adjustTimeForLatency(
                applicationClock.startOfHour(
                    applicationClock.now().minus(hourlyTallyOffsetMinutes)),
                prometheusLatencyDuration);
        startDateTime = applicationClock.startOfHour(endDateTime.minus(metricRange));
      } else {
        startDateTime = applicationClock.startOfHour(dateRange.get().getStartDate());
        endDateTime = applicationClock.startOfHour(dateRange.get().getEndDate());
      }

      log.info("Queuing all org hourly snapshot in batches of size one");

      orgStream.forEach(
          orgId -> {
            tallyOrgByHourly(orgId, new DateRange(startDateTime, endDateTime), false);
            count.addAndGet(1);
          });

      log.info("Done queuing hourly snapshot production for {} accounts.", count.intValue());

    } catch (Exception e) {
      throw new TaskManagerException("Could not list orgs for update snapshot task generation", e);
    }
  }

  protected OffsetDateTime adjustTimeForLatency(
      OffsetDateTime dateTime, Duration adjustmentAmount) {
    // Convert to a ZonedDateTime before subtracting the duration.  A ZonedDateTime will hold the
    // offset
    // rules around a specific time zone.  If the subtracted amount crosses a change in the zone's
    // offset (e.g. Daylight Saving Time), the ZonedDateTime.minus method will handle that properly.
    return dateTime.toZonedDateTime().minus(adjustmentAmount).toOffsetDateTime();
  }
}
