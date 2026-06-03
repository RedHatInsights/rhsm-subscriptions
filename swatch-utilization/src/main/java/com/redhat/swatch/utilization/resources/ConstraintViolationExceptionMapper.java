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
package com.redhat.swatch.utilization.resources;

import com.redhat.swatch.utilization.openapi.model.Error;
import com.redhat.swatch.utilization.openapi.model.Errors;
import jakarta.annotation.Priority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
@Priority(Priorities.USER)
public class ConstraintViolationExceptionMapper
    implements ExceptionMapper<ConstraintViolationException> {

  static final String ERROR_TITLE = "Client request failed validation.";

  @Override
  public Response toResponse(ConstraintViolationException exception) {
    String detail = formatDetail(exception);
    log.warn("Client request failed validation: {}", detail);

    Error error =
        new Error()
            .status(String.valueOf(Status.BAD_REQUEST.getStatusCode()))
            .title(ERROR_TITLE)
            .detail(detail);

    return Response.status(Status.BAD_REQUEST)
        .entity(new Errors().errors(List.of(error)))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  private static String formatDetail(ConstraintViolationException exception) {
    if (exception.getConstraintViolations() == null
        || exception.getConstraintViolations().isEmpty()) {
      return exception.getMessage();
    }
    return exception.getConstraintViolations().stream()
        .map(ConstraintViolationExceptionMapper::formatViolation)
        .collect(Collectors.joining("; "));
  }

  private static String formatViolation(ConstraintViolation<?> violation) {
    String path = violation.getPropertyPath().toString();
    String message = violation.getMessage();
    Object invalidValue = violation.getInvalidValue();
    if (invalidValue != null) {
      return path + ": rejected value " + invalidValue + " (" + message + ")";
    }
    return path + ": " + message;
  }
}
