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
package com.redhat.swatch.billable.usage.services;

import static com.redhat.swatch.billable.usage.configuration.Channels.TALLY_SUMMARY;

import com.redhat.swatch.billable.usage.model.TallySummary;
import com.redhat.swatch.faulttolerance.api.RetryWithExponentialBackoff;
import com.redhat.swatch.kafka.MessageHelper;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class TallySummaryMessageConsumer {
  public static final String RECEIVED_KEY = "kafka_receivedMessageKey";

  private final BillableUsageMapper billableUsageMapper;
  private final BillableUsageService billableUsageService;

  @Incoming(TALLY_SUMMARY)
  @Transactional
  @RetryWithExponentialBackoff(
      maxRetries = "${BILLING_PRODUCER_MAX_ATTEMPTS:1}",
      delay = "${BILLING_PRODUCER_BACK_OFF_INITIAL_INTERVAL:1s}",
      maxDelay = "${BILLING_PRODUCER_BACK_OFF_MAX_INTERVAL:60s}",
      factor = "${BILLING_PRODUCER_BACK_OFF_MULTIPLIER:2}")
  public void consume(TallySummary payload, KafkaMessageMetadata<?> metadata) {
    MessageHelper.findFirstHeaderAsString(metadata, RECEIVED_KEY)
        .ifPresent(
            kafkaMessageKey ->
                log.debug(
                    "Tally Summary received w/ key={}. Producing billable usage.",
                    kafkaMessageKey));

    billableUsageMapper
        .fromTallySummary(payload)
        .forEach(billableUsageService::submitBillableUsage);
  }
}
