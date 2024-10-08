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
package com.redhat.swatch.azure.service;

import static com.redhat.swatch.azure.configuration.Channels.BILLABLE_USAGE_STATUS;

import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.eclipse.microprofile.reactive.messaging.Channel;

@Slf4j
@ApplicationScoped
public class BillableUsageStatusProducer {
  private final MutinyEmitter<BillableUsageAggregate> emitter;

  public BillableUsageStatusProducer(
      @Channel(BILLABLE_USAGE_STATUS) MutinyEmitter<BillableUsageAggregate> emitter) {
    this.emitter = emitter;
  }

  public void emitStatus(BillableUsageAggregate billableUsage) {
    emitter.sendAndAwait(billableUsage);

    log.debug(
        "Queued Azure BillableUsageAggregate {} to billable-usage-status topic with status {}",
        billableUsage.getAggregateId(),
        billableUsage.getStatus());
  }
}
