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
package com.redhat.swatch.azure.resource;

import com.redhat.swatch.azure.exception.DefaultApiException;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.Errors;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

@Provider
@Slf4j
@Priority(-1)
public class DefaultApiExceptionMapper implements ResponseExceptionMapper<DefaultApiException> {

  @Override
  public boolean handles(int status, MultivaluedMap<String, Object> headers) {
    return status >= 400;
  }

  @Override
  public DefaultApiException toThrowable(Response response) {
    return new DefaultApiException(response, parseErrors(response));
  }

  private Errors parseErrors(Response response) {
    try {
      return response.readEntity(Errors.class);
    } catch (Exception e) {
      log.debug("Failed to create Errors from response.", e);
      return null;
    }
  }
}
