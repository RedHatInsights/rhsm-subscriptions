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

import static com.redhat.swatch.contract.config.Channels.CONTRACT_SYNC_TASK;

import com.redhat.swatch.contract.model.ContractSyncTask;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
@Slf4j
public class ContractSyncTaskConsumer {

  private final ContractService service;

  public ContractSyncTaskConsumer(ContractService service) {
    this.service = service;
  }

  @Blocking
  @Incoming(CONTRACT_SYNC_TASK)
  public void consumeFromTopic(ContractSyncTask task) {
    String orgId = task.getOrgId();
    log.info("contract-sync-all: Contract sync triggered for orgId={}", orgId);
    try {
      var result = service.syncContractsByOrgId(orgId);
      if (ContractService.FAILURE_MESSAGE.equals(result.getStatus())) {
        log.warn(
            "contract-sync-all: Contract sync failed for orgId={}: {}", orgId, result.getMessage());
      } else {
        log.info("contract-sync-all: Contract sync succeeded for orgId={}", orgId);
      }
    } catch (Exception e) {
      log.error("contract-sync-all: Contract sync threw exception for orgId={}", orgId, e);
    }
  }
}
