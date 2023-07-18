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
package org.candlepin.subscriptions.metering.service.prometheus.task;

import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusAccountSource;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.task.TaskDescriptor;
import org.candlepin.subscriptions.task.TaskDescriptor.TaskDescriptorBuilder;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.task.TaskType;
import org.candlepin.subscriptions.task.queue.TaskQueue;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

/** Produces task messages related to pulling metrics back from Telemeter. */
@Component
public class PrometheusMetricsTaskManager {

  private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsTaskManager.class);
  private final AccountConfigRepository accountConfigRepository;

  private String topic;

  private TaskQueue queue;

  private PrometheusAccountSource accountSource;

  private TagProfile tagProfile;

  private ApplicationClock clock;

  private ApplicationProperties appProps;

  @Autowired
  public PrometheusMetricsTaskManager(
      TaskQueue queue,
      @Qualifier("meteringTaskQueueProperties") TaskQueueProperties queueProps,
      PrometheusAccountSource accountSource,
      AccountConfigRepository accountConfigRepository,
      TagProfile tagProfile,
      ApplicationClock clock,
      ApplicationProperties appProps) {
    this.accountConfigRepository = accountConfigRepository;
    log.info("Initializing metering manager. Topic: {}", queueProps.getTopic());
    this.queue = queue;
    this.topic = queueProps.getTopic();
    this.accountSource = accountSource;
    this.tagProfile = tagProfile;
    this.clock = clock;
    this.appProps = appProps;
  }

  public void updateMetricsForAccount(
      String account, String productTag, OffsetDateTime start, OffsetDateTime end) {
    String orgId = accountConfigRepository.findOrgByAccountNumber(account);
    if (orgId == null) {
      throw new IllegalArgumentException(
          String.format("Could not find orgId for accountNumber: %s", account));
    }
    updateMetricsForOrgId(orgId, productTag, start, end);
  }

  public void updateMetricsForOrgId(
      String orgId, String productTag, OffsetDateTime start, OffsetDateTime end) {
    tagProfile
        .getSupportedMetricsForProduct(productTag)
        .forEach(
            metric -> {
              log.info("Queuing {} {} metric updates for orgId={}.", productTag, metric, orgId);
              queueMetricUpdateForOrgId(orgId, productTag, metric, start, end);
              log.info("Done queuing updates of {} {} metric", productTag, metric);
            });
  }

  private void queueMetricUpdateForOrgId(
      String orgId, String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    log.info(
        "Queuing {} {} metric update for orgId={} for range [{}, {})",
        productTag,
        metric,
        orgId,
        start,
        end);
    this.queue.enqueue(createMetricsTask(orgId, productTag, metric, start, end));
  }

  @Transactional
  public void updateMetricsForAllAccounts(
      String productTag, OffsetDateTime start, OffsetDateTime end) {
    updateMetricsForAllAccounts(
        productTag, start, end, RetryTemplate.builder().maxAttempts(1).build());
  }

  @Transactional
  public void updateMetricsForAllAccounts(String productTag, int rangeInMinutes) {
    updateMetricsForAllAccounts(
        productTag, rangeInMinutes, RetryTemplate.builder().maxAttempts(1).build());
  }

  @Transactional
  public void updateMetricsForAllAccounts(
      String productTag, int rangeInMinutes, RetryTemplate retry) {
    OffsetDateTime end =
        clock.startOfHour(clock.now().minus(appProps.getPrometheusLatencyDuration()));
    OffsetDateTime start = end.minusMinutes(rangeInMinutes);
    log.debug("range [{}, {})", start, end);
    updateMetricsForAllAccounts(productTag, start, end, retry);
  }

  private void updateMetricsForAllAccounts(
      String productTag, OffsetDateTime start, OffsetDateTime end, RetryTemplate retry) {
    log.info("Queuing {} metric updates for range: [{}, {})", productTag, start, end);
    tagProfile
        .getSupportedMetricsForProduct(productTag)
        .forEach(
            metric ->
                retry.execute(
                    context -> {
                      queueMetricUpdateForAllAccounts(productTag, metric, start, end);
                      return null;
                    }));
  }

  private void queueMetricUpdateForAllAccounts(
      String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    try (Stream<String> orgIdStream =
        accountSource.getMarketplaceAccounts(productTag, metric, start, end).stream()) {
      log.info("Queuing {} {} metric updates for all configured accounts.", productTag, metric);
      orgIdStream.forEach(
          orgId -> queueMetricUpdateForOrgId(orgId, productTag, metric, start, end));
      log.info("Done queuing updates of {} {} metric", productTag, metric);
    }
  }

  private TaskDescriptor createMetricsTask(
      String orgId, String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    log.info(
        "ORGID: {} TAG: {} METRIC: {} START: {} END: {}", orgId, productTag, metric, start, end);
    TaskDescriptorBuilder builder =
        TaskDescriptor.builder(TaskType.METRICS_COLLECTION, topic)
            .setSingleValuedArg("orgId", orgId)
            .setSingleValuedArg("productTag", productTag)
            .setSingleValuedArg("metric", metric.value())
            .setSingleValuedArg("start", start.toString());

    if (end != null) {
      builder.setSingleValuedArg("end", end.toString());
    }
    return builder.build();
  }
}
