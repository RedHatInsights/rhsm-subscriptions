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

import com.redhat.swatch.billable.usage.configuration.Channels;
import com.redhat.swatch.billable.usage.data.BillableUsageRemittanceRepository;
import com.redhat.swatch.billable.usage.data.RemittanceErrorCode;
import com.redhat.swatch.billable.usage.data.RemittanceStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
@AllArgsConstructor
public class BillableUsageStatusConsumer {

  private final BillableUsageRemittanceRepository remittanceRepository;

  @Incoming(Channels.BILLABLE_USAGE_STATUS)
  @Transactional
  public void process(BillableUsageAggregate billableUsageAggregate) {
    if (Objects.isNull(billableUsageAggregate.getStatus())) {
      log.error(
          "Billable Usage Status update received with no status, {}:", billableUsageAggregate);
      return;
    }

    updateStatus(billableUsageAggregate);
  }

  private void updateStatus(BillableUsageAggregate billableUsageAggregate) {
    log.info("Updating status for aggregate: {}", billableUsageAggregate);
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
}
