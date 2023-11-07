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
import com.redhat.swatch.metrics.model.MetricsTaskDescriptor;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@Slf4j
@ApplicationScoped
public class MetricsProducer {

  @ConfigProperty(name = "rhsm-subscriptions.prometheus-latency-duration")
  Duration prometheusLatencyDuration;

  @Inject
  @Channel("tasks-out")
  Emitter<MetricsTaskDescriptor> emitter;

  public void queueMetricUpdateForOrgId(
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
