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
package com.redhat.swatch.contract.resource;

import com.redhat.swatch.contract.exception.ContractsException;
import com.redhat.swatch.contract.openapi.model.Error;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/** ExceptionMapper that maps application exceptions into . */
@Provider
public class ContractsExceptionMapper implements ExceptionMapper<ContractsException> {
  @Override
  public Response toResponse(ContractsException exception) {
    // Using a switch expression below forces us to decide on an HTTP status every time we add a new
    // ErrorCode.
    var status =
        switch (exception.getCode()) {
          case CONTRACT_EXISTS -> Status.CONFLICT;
          case UNHANDLED_EXCEPTION, CONTRACT_UPDATE_NOT_ALLOWED -> Status.INTERNAL_SERVER_ERROR;
          case BAD_UPDATE -> Status.BAD_REQUEST;
          case CONTRACT_DOES_NOT_EXIST -> Status.NOT_FOUND;
        };
    return Response.status(status)
        .entity(
            new Error()
                .code(exception.getCode().getCode())
                .status(String.valueOf(status.getStatusCode()))
                .title(exception.getMessage())
                .detail(exception.getDetail()))
        .build();
  }
}
