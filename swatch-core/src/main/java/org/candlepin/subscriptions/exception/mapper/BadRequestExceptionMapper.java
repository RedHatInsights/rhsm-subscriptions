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
import org.candlepin.subscriptions.utilization.api.model.Error;
import org.jboss.resteasy.spi.BadRequestException;
import org.springframework.stereotype.Component;

/**
 * This handler catches RESTEasy BadRequestException. Note that {@link
 * jakarta.ws.rs.BadRequestException} (which is what our application code generally throws) is
 * <em>not</em> handled by this mapper but instead by the WebApplicationExceptionMapper.
 */
@Component
@Provider
public class BadRequestExceptionMapper extends BaseExceptionMapper<BadRequestException> {
  @Override
  protected Error buildError(BadRequestException exception) {
    return new Error()
        .code(ErrorCode.REQUEST_PROCESSING_ERROR.getCode())
        .status(String.valueOf(Response.Status.BAD_REQUEST.getStatusCode()))
        .title("Bad Request")
        .detail(exception.getCause().getMessage());
  }
}
