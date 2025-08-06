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
package com.redhat.swatch.hbi.resources;

import com.redhat.swatch.hbi.api.DefaultApi;
import com.redhat.swatch.hbi.model.FlushResponse;
import com.redhat.swatch.hbi.model.OutboxRecord;
import com.redhat.swatch.hbi.model.OutboxRecordRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class InternalResource implements DefaultApi {

  @Override
  public List<OutboxRecord> fetchAllOutboxRecords() {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }

  @Override
  public List<OutboxRecord> fetchOutboxRecordsByOrgId(String orgId) {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }

  @Override
  public OutboxRecord createOutboxRecord(OutboxRecordRequest outboxRecordRequest) {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
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
  public FlushResponse flushOutbox(Boolean async) {
    throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
  }
}
