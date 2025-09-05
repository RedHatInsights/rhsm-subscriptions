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
import com.redhat.swatch.hbi.model.OutboxRecord;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.context.ManagedExecutor;

@Slf4j
@ApplicationScoped
public class InternalResource implements DefaultApi {

  public static final String SYNCHRONOUS_REQUEST_HEADER = "x-rh-swatch-synchronous-request";

  @Inject ManagedExecutor executor;

  @Inject ApplicationConfiguration applicationProperties;

  @Inject HbiEventOutboxService outboxService;

  @Override
  public List<OutboxRecord> fetchAllOutboxRecords() {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }

  @Override
  public List<OutboxRecord> fetchOutboxRecordsByOrgId(String orgId) {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }

  @Override
  @RolesAllowed({"test"})
  public OutboxRecord createOutboxRecord(@Valid Event event) {
    return outboxService.createOutboxRecord(event);
  }

  @Override
  public OutboxRecord updateOutboxRecord(OutboxRecord outboxRecord) {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }

  @Override
  public void deleteOutboxRecord(UUID uuid) {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }

  @Override
  @RolesAllowed({"service", "test"})
  public FlushResponse flushOutbox(Boolean xRhSynchronousRequestHeader) {
    boolean makeSynchronousRequest = Boolean.TRUE.equals(xRhSynchronousRequestHeader);
    FlushResponse response = new FlushResponse().async(!makeSynchronousRequest);

    if (makeSynchronousRequest) {
      if (!applicationProperties.isSynchronousOperationsEnabled()) {
        throw new SynchronousRequestsNotEnabledException();
      }

      log.info("Request received to flush the outbox synchronously!");
      try {
        flush();
      } catch (Exception e) {
        throw new SynchronousOutboxFlushException(e);
      }
      response.setStatus(FlushResponse.StatusEnum.SUCCESS);
      return response;
    }

    log.info("Request received to flush the outbox asynchronously!");
    executor.runAsync(this::flush);
    response.setStatus(FlushResponse.StatusEnum.STARTED);
    return response;
  }

  private void flush() {
    log.debug(
        "Outbox flush running on vertx worker thread: {}",
        io.vertx.core.Context.isOnWorkerThread());
    log.info("Flushed {} outbox records!", outboxService.flushOutboxRecords());
  }
}
