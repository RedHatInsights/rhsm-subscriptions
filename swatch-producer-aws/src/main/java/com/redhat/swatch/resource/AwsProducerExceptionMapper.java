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
package com.redhat.swatch.resource;

import com.redhat.swatch.clients.swatch.internal.subscription.api.model.Error;
import com.redhat.swatch.exception.AwsProducerException;
import com.redhat.swatch.exception.ErrorCode;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AwsProducerExceptionMapper implements ExceptionMapper<AwsProducerException> {

  @Override
  public Response toResponse(AwsProducerException exception) {
    Error error = new Error().code(exception.getCode().getCode()).detail(exception.toString());
    Status status =
        exception.getCode() == ErrorCode.AWS_MANUAL_SUBMISSION_DISABLED
            ? Status.FORBIDDEN
            : Status.INTERNAL_SERVER_ERROR;
    error.setStatus(String.valueOf(status.getStatusCode()));
    error.setTitle(status.getReasonPhrase());
    return Response.status(status).entity(error).build();
  }
}
