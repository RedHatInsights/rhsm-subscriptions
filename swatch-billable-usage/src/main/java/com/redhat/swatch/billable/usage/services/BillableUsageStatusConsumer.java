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

import static java.util.Optional.ofNullable;
import static org.candlepin.subscriptions.billable.usage.BillableUsage.ErrorCode.SUBSCRIPTION_NOT_FOUND;
import static org.candlepin.subscriptions.billable.usage.BillableUsage.Status.FAILED;

import com.redhat.swatch.billable.usage.configuration.Channels;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregateKey;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class BillableUsageStatusConsumer {

  private final BillableUsageRemittanceRepository remittanceRepository;
  private final BillableUsageDeadLetterProducer billableUsageDeadLetterProducer;

  @Incoming(Channels.BILLABLE_USAGE_STATUS)
  @Transactional
  public void process(BillableUsageAggregate billableUsageAggregate) {
    if (Objects.isNull(billableUsageAggregate.getStatus())) {
      log.error(
          "Billable Usage Status update received with no status, {}:", billableUsageAggregate);
      return;
    }

    updateStatus(billableUsageAggregate);
    if (billableUsageAggregate.getStatus() == FAILED
        && billableUsageAggregate.getErrorCode() == SUBSCRIPTION_NOT_FOUND) {
      sendToDeadLetterTopic(billableUsageAggregate);
    }
  }

  private void sendToDeadLetterTopic(BillableUsageAggregate billableUsageAggregate) {
    if (billableUsageAggregate.getAggregateKey().getBillingProvider() != null
        && billableUsageAggregate.getRemittanceUuids() != null) {
      billableUsageAggregate.getRemittanceUuids().stream()
          .map(
              uuid -> billableUsageFromAggregateKey(billableUsageAggregate.getAggregateKey(), uuid))
          .forEach(
              billableUsage -> {
                billableUsageDeadLetterProducer.send(billableUsage);
                log.warn(
                    "Skipping billable usage with id={} orgId={} because the subscription was not found. Will retry again after one hour.",
                    billableUsage.getUuid(),
                    billableUsage.getOrgId());
              });
    } else {
      log.warn("No billable usage remittance UUIDs available to retry for.");
    }
  }

  private void updateStatus(BillableUsageAggregate billableUsageAggregate) {
    var status = RemittanceStatus.fromString(billableUsageAggregate.getStatus().value());
    var errorCode =
        ofNullable(billableUsageAggregate.getErrorCode())
            .map(code -> RemittanceErrorCode.fromString(code.value()))
            .orElse(null);
    remittanceRepository.updateStatusByIdIn(
        billableUsageAggregate.getRemittanceUuids(),
        status,
        billableUsageAggregate.getBilledOn(),
        errorCode);
  }

  private BillableUsage billableUsageFromAggregateKey(BillableUsageAggregateKey key, String uuid) {
    var billableUsage = new BillableUsage();
    billableUsage.setUsage(
        ofNullable(key.getUsage())
            .map(BillableUsage.Usage::fromValue)
            .orElse(BillableUsage.Usage.__EMPTY__));
    billableUsage.setBillingAccountId(key.getBillingAccountId());
    billableUsage.setBillingProvider(
        BillableUsage.BillingProvider.fromValue(key.getBillingProvider()));
    billableUsage.setOrgId(key.getOrgId());
    billableUsage.setProductId(key.getProductId());
    billableUsage.setMetricId(key.getMetricId());
    billableUsage.setSla(
        ofNullable(key.getSla())
            .map(BillableUsage.Sla::fromValue)
            .orElse(BillableUsage.Sla.__EMPTY__));
    billableUsage.setUuid(UUID.fromString(uuid));
    return billableUsage;
  }
}
