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

import static com.redhat.swatch.billable.usage.configuration.Channels.BILLABLE_USAGE_DLT;

import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceFilter;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.kafka.MessageHelper;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class BillableUsageDeadLetterConsumer {

  public static final String RETRY_AFTER_HEADER = "retryAfter";

  private final BillableUsageRemittanceRepository billableUsageRemittanceRepository;

  @Incoming(BILLABLE_USAGE_DLT)
  @Transactional
  public void consume(BillableUsage payload, KafkaMessageMetadata<?> metadata) {
    var retryAfterHeader = MessageHelper.findFirstHeaderAsString(metadata, RETRY_AFTER_HEADER);
    if (retryAfterHeader.isPresent()) {
      var retryAfter = OffsetDateTime.parse(retryAfterHeader.get());
      updateBillableUsageRemittanceWithRetryAfter(payload, retryAfter);
    } else {
      log.debug("Message received with no retryAfter: {}", payload);
    }
  }

  private void updateBillableUsageRemittanceWithRetryAfter(
      BillableUsage billableUsage, OffsetDateTime retryAfter) {
    var billableUsageRemittance =
        billableUsageRemittanceRepository.findOne(
            BillableUsageRemittanceFilter.fromUsage(billableUsage));
    if (billableUsageRemittance.isPresent()) {
      billableUsageRemittance.get().setRetryAfter(retryAfter);
      billableUsageRemittanceRepository.persist(billableUsageRemittance.get());
    } else {
      log.warn(
          "Unable to find billable usage to update retry_after. BillableUsage: {}", billableUsage);
    }
  }
}
