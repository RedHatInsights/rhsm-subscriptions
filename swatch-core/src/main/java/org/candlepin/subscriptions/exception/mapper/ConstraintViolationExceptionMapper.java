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
package org.candlepin.subscriptions.exception.mapper;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.utilization.api.v1.model.Error;
import org.springframework.stereotype.Component;

/** Exception mapper for calls to a resource method that fail to pass validation. */
@Component
@Provider
public class ConstraintViolationExceptionMapper
    extends BaseExceptionMapper<ConstraintViolationException> {
  public static final String ERROR_TITLE = ErrorCode.VALIDATION_FAILED_ERROR.getDescription();

  @Override
  protected Error buildError(ConstraintViolationException exception) {
    return new Error()
        .code(ErrorCode.VALIDATION_FAILED_ERROR.getCode())
        .status(String.valueOf(Status.BAD_REQUEST.getStatusCode()))
        .title(ERROR_TITLE)
        .detail(exception.getMessage());
  }
}
