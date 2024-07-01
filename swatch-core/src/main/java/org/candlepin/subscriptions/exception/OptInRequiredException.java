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

import jakarta.ws.rs.core.Response.Status;
import org.candlepin.subscriptions.utilization.api.v1.model.Error;
import org.springframework.security.access.AccessDeniedException;

/**
 * An exception that represents an access denied exception in cases where opt-in is required to
 * access an end-point.
 */
public class OptInRequiredException extends AccessDeniedException {
  public OptInRequiredException() {
    super("Opt-in required.");
  }

  public Error getError() {
    return new Error()
        .code(ErrorCode.OPT_IN_REQUIRED.getCode())
        .status(String.valueOf(Status.FORBIDDEN.getStatusCode()))
        .title("Access Denied")
        .detail(this.getMessage());
  }
}
