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
package com.redhat.swatch.metrics.service;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.metrics.configuration.ApplicationConfiguration;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import com.redhat.swatch.metrics.model.MetricsTaskDescriptor;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class PrometheusMetricsTaskManager {

  private final PrometheusAccountSource accountSource;
  private final ApplicationClock clock;
  private final MetricProperties metricProperties;
  private final ApplicationConfiguration applicationConfiguration;
  private final Emitter<MetricsTaskDescriptor> emitter;

  public PrometheusMetricsTaskManager(
      PrometheusAccountSource accountSource,
      ApplicationClock clock,
      MetricProperties metricProperties,
      ApplicationConfiguration applicationConfiguration,
      @Channel("tasks-out") Emitter<MetricsTaskDescriptor> emitter) {
    this.accountSource = accountSource;
    this.clock = clock;
    this.metricProperties = metricProperties;
    this.applicationConfiguration = applicationConfiguration;
    this.emitter = emitter;
  }

  public void updateMetricsForOrgId(
      String orgId, String productTag, OffsetDateTime start, OffsetDateTime end) {
    MetricIdUtils.getMetricIdsFromConfigForTag(productTag)
        .forEach(
            metricId -> {
              log.info("Queuing {} {} metric updates for orgId={}.", productTag, metricId, orgId);
              queueMetricUpdateForOrgId(orgId, productTag, metricId, start, end);
              log.info("Done queuing updates of {} {} metric", productTag, metricId);
            });
  }

  @Transactional
  public void updateMetricsForAllAccounts(String productTag) {
    OffsetDateTime end =
        clock.startOfHour(
            clock.now().minus(applicationConfiguration.getPrometheusLatencyDuration()));
    OffsetDateTime start = end.minusMinutes(metricProperties.rangeInMinutes());
    log.info("Queuing {} metric updates for range: [{}, {})", productTag, start, end);
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(productTag);
    subDefOptional.ifPresent(
        subDef ->
            subDef
                .getMetricIds() // No null check required here since metrics will always be present
                .forEach(
                    metric ->
                        queueMetricUpdateForAllAccounts(
                            productTag, MetricId.fromString(metric), start, end)));
  }

  @Retry
  public void queueMetricUpdateForAllAccounts(
      String productTag, MetricId metric, OffsetDateTime start, OffsetDateTime end) {
    try (Stream<String> orgIdStream =
        accountSource.getMarketplaceAccounts(productTag, metric, start, end).stream()) {
      log.info("Queuing {} {} metric updates for all configured accounts.", productTag, metric);
      orgIdStream.forEach(
          orgId -> queueMetricUpdateForOrgId(orgId, productTag, metric, start, end));
      log.info("Done queuing updates of {} {} metric", productTag, metric);
    }
  }

  private void queueMetricUpdateForOrgId(
      String orgId, String productTag, MetricId metric, OffsetDateTime start, OffsetDateTime end) {
    log.info(
        "Queuing {} {} metric update for orgId={} for range [{}, {})",
        productTag,
        metric,
        orgId,
        start,
        end);
    enqueue(createMetricsTask(orgId, productTag, metric, start, end));
  }

  private MetricsTaskDescriptor createMetricsTask(
      String orgId, String productTag, MetricId metric, OffsetDateTime start, OffsetDateTime end) {
    MetricsTaskDescriptor task = new MetricsTaskDescriptor();
    task.setOrgId(orgId);
    task.setProductTag(productTag);
    task.setMetric(metric.getValue());
    task.setStart(start);
    task.setEnd(end);
    return task;
  }

  private void enqueue(MetricsTaskDescriptor task) {
    log.info("Queuing task: {}", task);
    OutgoingKafkaRecordMetadata<?> metadata =
        OutgoingKafkaRecordMetadata.builder().withKey(task.getOrgId()).build();
    emitter.send(Message.of(task).addMetadata(metadata));
  }
}
