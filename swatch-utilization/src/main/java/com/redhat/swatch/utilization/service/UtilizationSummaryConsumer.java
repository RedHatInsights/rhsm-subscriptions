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
package com.redhat.swatch.utilization.service;

import static com.redhat.swatch.utilization.configuration.Channels.UTILIZATION;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.utilization.model.UtilizationSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class UtilizationSummaryConsumer {

  public static final String RECEIVED_METRIC = "swatch_utilization_received";

  @Inject MeterRegistry meterRegistry;
  @Inject UtilizationSummaryValidator payloadValidator;
  @Inject CustomerOverUsageService customerOverUsageService;

  @Incoming(UTILIZATION)
  public void process(UtilizationSummary payload) {
    if (payloadValidator.isValid(payload)) {
      incrementCounter(payload);
      customerOverUsageService.check(payload);
    } else {
      log.debug("Invalid payload received: {}", payload);
    }
  }

  private void incrementCounter(UtilizationSummary payload) {
    List<String> tags = new ArrayList<>(List.of("product", payload.getProductId()));

    if (Objects.nonNull(payload.getBillingProvider())) {
      tags.addAll(List.of("billing", payload.getBillingProvider().value()));
    }

    for (var measurement : payload.getMeasurements()) {
      var tagsByMetric = new ArrayList<>(tags);
      tagsByMetric.addAll(
          List.of("metric_id", MetricId.tryGetValueFromString(measurement.getMetricId())));
      incrementCounter(tagsByMetric);
    }
  }

  private void incrementCounter(List<String> tags) {
    meterRegistry.counter(RECEIVED_METRIC, tags.toArray(new String[0])).increment();
  }
}
