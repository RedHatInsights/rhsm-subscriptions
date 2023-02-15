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
package org.candlepin.subscriptions.exception;

import javax.ws.rs.core.Response.Status;
import lombok.Getter;
import org.candlepin.subscriptions.utilization.api.model.Error;

/**
 * Application's base exception class. Provides a means to create an Error object that should be
 * typically return as part of an error response.
 */
public class MissingOfferingException extends RuntimeException {

  @Getter private final Status status;
  @Getter private final String detail;
  @Getter private final ErrorCode code;

  public MissingOfferingException(ErrorCode code, Status status, String message, String detail) {
    this(code, status, message, detail, null);
  }

  public MissingOfferingException(
      ErrorCode code, Status status, String message, String detail, Throwable e) {
    super(message, e);
    this.code = code;
    this.status = status;
    this.detail = detail;
  }

  public Error error() {
    return new Error()
        .code(this.code.getCode())
        .status(String.valueOf(status.getStatusCode()))
        .title(this.getMessage())
        .detail(this.detail);
  }
}
