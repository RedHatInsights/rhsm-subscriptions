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
package com.redhat.swatch.gcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.MetricValue;
import com.google.api.servicecontrol.v1.MetricValueSet;
import com.google.api.servicecontrol.v1.Operation;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.JsonFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import java.io.IOException;
import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;

@ApplicationScoped
public class GcpUsageService {
  private final GcpConfiguration configuration;
  private final GcpUsageContextLookup contextLookup;
  private final GcpServiceControlClient client;
  private final ObjectMapper objectMapper;

  public GcpUsageService(
      GcpConfiguration configuration,
      GcpUsageContextLookup contextLookup,
      GcpServiceControlClient client,
      ObjectMapper objectMapper) {
    this.configuration = configuration;
    this.contextLookup = contextLookup;
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public Submission submit(BillableUsageAggregate aggregate) {
    if (aggregate == null
        || aggregate.getAggregateKey() == null
        || !"gcp".equals(aggregate.getAggregateKey().getBillingProvider())
        || aggregate.getAggregateId() == null
        || aggregate.getTotalValue() == null
        || aggregate.getTotalValue().signum() < 0) {
      throw new BadRequestException(
          "Expected a GCP aggregate with aggregateId and nonnegative totalValue");
    }
    // A processing window can contain historical usage from several hours. The POC cannot split
    // its quantity back into those hours, so reject that case rather than invent usage timestamps.
    if (aggregate.getSnapshotDates() == null
        || aggregate.getSnapshotDates().isEmpty()
        || aggregate.getSnapshotDates().stream().anyMatch(Objects::isNull)) {
      throw new BadRequestException("snapshotDates must identify the actual usage hour");
    }
    var hours =
        aggregate.getSnapshotDates().stream()
            .map(d -> d.toInstant().truncatedTo(ChronoUnit.HOURS))
            .distinct()
            .toList();
    if (hours.size() != 1) {
      throw new BadRequestException(
          "POC supports aggregates containing one actual usage hour only");
    }
    String quantity;
    try {
      // Placeholder pricing metric uses INT64 units. Reject fractional/overflowing values.
      quantity = Long.toString(aggregate.getTotalValue().longValueExact());
    } catch (ArithmeticException e) {
      throw new BadRequestException(
          "POC metric requires an integer quantity within INT64 range", e);
    }
    var context = contextLookup.lookup(aggregate.getAggregateKey());
    var start = hours.getFirst();
    var operation =
        Operation.newBuilder()
            .setOperationId(aggregate.getAggregateId().toString())
            .setOperationName("Swatch Hourly Usage Report")
            .setConsumerId(context.usageReportingId())
            .setStartTime(Timestamp.newBuilder().setSeconds(start.getEpochSecond()))
            .setEndTime(
                Timestamp.newBuilder().setSeconds(start.plus(1, ChronoUnit.HOURS).getEpochSecond()))
            .addMetricValueSets(
                MetricValueSet.newBuilder()
                    .setMetricName(context.metricName())
                    .addMetricValues(
                        MetricValue.newBuilder().setInt64Value(Long.parseLong(quantity))))
            .build();
    var check =
        CheckRequest.newBuilder()
            .setServiceName(context.serviceName())
            .setOperation(operation)
            .build();
    var report =
        ReportRequest.newBuilder()
            .setServiceName(context.serviceName())
            .addOperations(operation)
            .build();
    var checkUrl = client.endpoint(context.serviceName(), "check");
    var reportUrl = client.endpoint(context.serviceName(), "report");
    if (configuration.dryRun()) {
      return new Submission("DRY_RUN", checkUrl, json(check), reportUrl, json(report));
    }
    client.checkAndReport(check, report);
    return new Submission("ACCEPTED", checkUrl, json(check), reportUrl, json(report));
  }

  private JsonNode json(MessageOrBuilder request) {
    try {
      return objectMapper.readTree(JsonFormat.printer().print(request));
    } catch (IOException e) {
      throw new IllegalStateException("Could not render SDK request", e);
    }
  }

  public record Submission(
      String status, URI checkUrl, JsonNode checkRequest, URI reportUrl, JsonNode reportRequest) {}
}
