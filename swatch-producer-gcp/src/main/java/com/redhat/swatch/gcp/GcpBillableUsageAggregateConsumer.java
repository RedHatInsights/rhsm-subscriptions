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

import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@Slf4j
@ApplicationScoped
public class GcpBillableUsageAggregateConsumer {
  private final GcpUsageService service;
  private final MutinyEmitter<BillableUsageAggregate> statuses;

  public GcpBillableUsageAggregateConsumer(
      GcpUsageService service,
      @Channel("billable-usage-status-out") MutinyEmitter<BillableUsageAggregate> statuses) {
    this.service = service;
    this.statuses = statuses;
  }

  @Incoming("billable-usage-hourly-aggregate-in")
  @Blocking
  public void process(BillableUsageAggregate aggregate) {
    if (aggregate == null
        || aggregate.getAggregateKey() == null
        || !"gcp".equals(aggregate.getAggregateKey().getBillingProvider())) {
      return;
    }
    try {
      var result = service.submit(aggregate);
      if ("DRY_RUN".equals(result.status())) {
        log.info(
            "[DRY RUN] aggregateId={} check={} report={}",
            aggregate.getAggregateId(),
            result.checkRequest(),
            result.reportRequest());
        // Previewing must not mark real SWATCH remittances as billed.
        return;
      }
      aggregate.setStatus(BillableUsage.Status.SUCCEEDED);
      aggregate.setErrorCode(null);
      aggregate.setBilledOn(OffsetDateTime.now());
    } catch (RuntimeException e) {
      log.error("GCP submission failed for aggregateId={}", aggregate.getAggregateId(), e);
      aggregate.setStatus(BillableUsage.Status.FAILED);
      aggregate.setErrorCode(BillableUsage.ErrorCode.UNKNOWN);
      aggregate.setBilledOn(null);
    }
    statuses.sendAndAwait(aggregate);
  }
}
