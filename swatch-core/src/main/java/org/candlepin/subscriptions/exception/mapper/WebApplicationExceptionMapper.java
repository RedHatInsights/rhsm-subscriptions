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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.utilization.api.model.Error;
import org.springframework.stereotype.Component;

/**
 * An exception mapper used to override the default exception mapping done by resteasy. This
 * implementation generates and returns an Errors object as the response Json instead of redirecting
 * to an error page.
 */
@Component
@Provider
public class WebApplicationExceptionMapper extends BaseExceptionMapper<WebApplicationException> {
  @Override
  protected Error buildError(WebApplicationException wae) {
    // Because WebApplicationException is an HTTP-oriented wrapper around an exception, we handle
    // it a little differently. We assume that the message makes a good error title, and we use
    // the wrapped exception's message as detail (if a wrapped exception is present).
    Error error =
        new Error()
            .code(ErrorCode.REQUEST_PROCESSING_ERROR.getCode())
            .status(String.valueOf(wae.getResponse().getStatus()))
            .title(wae.getMessage());
    if (wae.getCause() != null) {
      error.setDetail(wae.getCause().getMessage());
    }
    return error;
  }
}
