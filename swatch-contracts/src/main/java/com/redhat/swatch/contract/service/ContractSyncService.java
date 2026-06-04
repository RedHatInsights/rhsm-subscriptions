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

import static com.redhat.swatch.contract.config.Channels.CONTRACT_SYNC;

import com.redhat.swatch.contract.model.ContractSyncTask;
import com.redhat.swatch.contract.repository.ContractRepository;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;

/**
 * Enqueues per-org contract sync tasks onto the Kafka topic. Analogous to {@code
 * CaptureSnapshotsTaskManager} in swatch-tally.
 */
@ApplicationScoped
@Slf4j
public class ContractSyncService {

  private final ContractRepository contractRepository;
  private final MutinyEmitter<ContractSyncTask> contractSyncTaskEmitter;

  @Inject
  public ContractSyncService(
      ContractRepository contractRepository,
      @Channel(CONTRACT_SYNC) MutinyEmitter<ContractSyncTask> contractSyncTaskEmitter) {
    this.contractRepository = contractRepository;
    this.contractSyncTaskEmitter = contractSyncTaskEmitter;
  }

  /**
   * Enqueues one {@link ContractSyncTask} per distinct org that has contracts in the database. The
   * stream is consumed within the transaction so the JDBC cursor remains open for its full
   * lifetime.
   *
   * @return number of orgs enqueued
   */
  @Transactional
  public long enqueueContractSyncForAllOrgs() {
    log.info("Queuing contract sync tasks for all orgs");
    AtomicLong count = new AtomicLong(0);
    try (Stream<String> orgIds = contractRepository.getDistinctOrgIds()) {
      orgIds.forEach(
          orgId -> {
            contractSyncTaskEmitter.sendAndAwait(new ContractSyncTask(orgId));
            count.incrementAndGet();
          });
    }
    log.info("Done queuing contract sync tasks for {} org(s)", count.get());
    return count.get();
  }
}
