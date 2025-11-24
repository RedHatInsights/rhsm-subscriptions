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

import static com.redhat.swatch.billable.usage.configuration.Channels.BILLABLE_USAGE_OUT;

import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.billable.usage.BillableUsage;
import org.eclipse.microprofile.reactive.messaging.Channel;

/**
 * Component that produces BillableUsage messages based on TallySnapshots.
 *
 * <p>NOTE: We are currently just forwarding TallySummary messages, but will transition to sending
 * BillableUsage.
 */
@Slf4j
@ApplicationScoped
public class BillingProducer {

  private final MutinyEmitter<BillableUsage> emitter;

  public BillingProducer(@Channel(BILLABLE_USAGE_OUT) MutinyEmitter<BillableUsage> emitter) {
    this.emitter = emitter;
  }

  public void produce(BillableUsage usage) {
    if (usage == null) {
      log.debug("Skipping billable usage; see previous errors/warnings.");
      return;
    }
    emitter.sendAndAwait(usage);
  }
}
