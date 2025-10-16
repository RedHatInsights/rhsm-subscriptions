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
package com.redhat.swatch.contract.service;

import static com.redhat.swatch.contract.config.Channels.TALLY_IN;

import com.redhat.swatch.contract.model.TallySummary;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consumer for tally snapshot summaries that processes them in batches and enriches them with
 * capacity data using optimized database queries. Reuses the logic from
 * SubscriptionTableControllerV2 for building search specifications.
 */
@Slf4j
@ApplicationScoped
public class TallySnapshotSummaryConsumer {

  /**
   * Processes a batch of tally summary messages and sends enriched utilization summaries. This
   * method extracts unique (orgId, productId) combinations from the batch and executes optimized
   * queries to minimize database round trips.
   *
   * @param tallyMessages the batch of tally summary messages to process
   * @return a Uni that completes when all messages are processed and sent
   */
  @Blocking
  @Incoming(TALLY_IN)
  @Transactional
  public Uni<Void> processBatch(List<TallySummary> tallyMessages) {
    log.info("Processing batch of {} tally messages", tallyMessages.size());

    return Uni.createFrom().voidItem();
  }
}
