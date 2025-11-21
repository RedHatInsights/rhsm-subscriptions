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
package com.redhat.swatch.hbi.events.resources;

import com.redhat.swatch.hbi.api.DefaultApi;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.exception.api.SynchronousOutboxFlushException;
import com.redhat.swatch.hbi.events.exception.api.SynchronousRequestsNotEnabledException;
import com.redhat.swatch.hbi.events.services.HbiEventOutboxService;
import com.redhat.swatch.hbi.model.FlushResponse;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.context.ManagedExecutor;

@Slf4j
@ApplicationScoped
public class InternalResource implements DefaultApi {

  public static final String SYNCHRONOUS_REQUEST_HEADER = "x-rh-swatch-synchronous-request";
  private final AtomicBoolean flushInProgress = new AtomicBoolean(false);

  @Inject ManagedExecutor executor;

  @Inject ApplicationConfiguration applicationProperties;

  @Inject HbiEventOutboxService outboxService;

  @Override
  @RolesAllowed({"service", "test"})
  public FlushResponse flushOutbox(Boolean xRhSynchronousRequestHeader) {
    boolean makeSynchronousRequest = Boolean.TRUE.equals(xRhSynchronousRequestHeader);
    FlushResponse response = new FlushResponse().async(!makeSynchronousRequest);

    if (!flushInProgress.compareAndSet(false, true)) {
      log.warn("Flush already in progress, cannot start another");
      response.setStatus(FlushResponse.StatusEnum.ALREADY_RUNNING);
      return response;
    }

    if (makeSynchronousRequest) {
      if (!applicationProperties.isSynchronousOperationsEnabled()) {
        throw new SynchronousRequestsNotEnabledException();
      }

      log.info("Request received to flush the outbox synchronously!");
      try {
        long count = flush();
        response.setStatus(FlushResponse.StatusEnum.SUCCESS);
        response.setCount(count);
        return response;
      } catch (Exception e) {
        throw new SynchronousOutboxFlushException(e);
      }
    }

    log.info("Request received to flush the outbox asynchronously!");
    executor.runAsync(this::flush);
    response.setStatus(FlushResponse.StatusEnum.STARTED);
    return response;
  }

  private long flush() {
    log.debug(
        "Outbox flush running on vertx worker thread: {}",
        io.vertx.core.Context.isOnWorkerThread());
    try {
      log.info("Flushing outbox records!");
      long count = outboxService.flushOutboxRecords();
      log.info("Flushed {} outbox records!", count);
      return count;
    } finally {
      flushInProgress.set(false);
    }
  }
}
