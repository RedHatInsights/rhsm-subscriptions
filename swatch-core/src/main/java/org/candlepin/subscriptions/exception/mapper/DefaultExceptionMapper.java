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

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.utilization.api.v1.model.Error;
import org.springframework.stereotype.Component;

/**
 * The default exception mapper used to catch any Throwable that isn't already being mapped by
 * another mapper. This mapper will always produce a 500 HTTP response containing the message from
 * the exception.
 */
@Component
@Provider
public class DefaultExceptionMapper extends BaseExceptionMapper<Throwable> {

  @Override
  protected Error buildError(Throwable exception) {
    return new Error()
        .code(ErrorCode.UNHANDLED_EXCEPTION_ERROR.getCode())
        .status(String.valueOf(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()))
        .title("An internal server error has occurred. Check the server logs for further details.")
        .detail(exception.getMessage());
  }
}
