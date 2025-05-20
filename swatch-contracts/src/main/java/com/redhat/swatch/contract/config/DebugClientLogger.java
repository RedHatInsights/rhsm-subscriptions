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
package com.redhat.swatch.contract.config;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** ClientLogger implementation that only logs requests for a matching URI */
@Provider
@Slf4j
public class DebugClientLogger implements ClientRequestFilter, ClientResponseFilter {

  protected static final String URI_FILTER_PROPERTY = "rest-client-debug-logging.uri-filter";
  protected static final String LOG_RESPONSES_PROPERTY = "rest-client-debug-logging.log-responses";
  private static final String EMPTY_BODY = "(empty)";
  private static final String OMIT_BODY = "(omit)";

  @ConfigProperty(name = URI_FILTER_PROPERTY)
  Pattern uriFilterPattern;

  @ConfigProperty(name = LOG_RESPONSES_PROPERTY, defaultValue = "false")
  boolean logResponse;

  @Override
  public void filter(ClientRequestContext requestContext) throws IOException {
    if (appliesTo(requestContext)) {
      log.debug(
          "Request method={} URI={}: \n{}",
          requestContext.getMethod(),
          requestContext.getUri().toString(),
          bodyToString(requestContext.getEntity()));
    }
  }

  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext)
      throws IOException {
    if (appliesTo(requestContext)) {
      String responseBody = OMIT_BODY;
      if (logResponse) {
        // InputStream can only be consumed once, so to log the response body,
        // we need to copy the content, log it, and then copy it back to a new input stream.
        InputStream originalStream = responseContext.getEntityStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        originalStream.transferTo(baos);
        byte[] entityBytes = baos.toByteArray();
        responseBody = new String(entityBytes, StandardCharsets.UTF_8);
        responseContext.setEntityStream(new ByteArrayInputStream(entityBytes));
      }

      log.debug(
          "Response method={} URI={}: \n{}",
          requestContext.getMethod(),
          requestContext.getUri().toString(),
          bodyToString(responseBody));
    }
  }

  private boolean appliesTo(ClientRequestContext requestContext) {
    String uri = requestContext.getUri().toString();
    return uriFilterPattern.matcher(uri).matches();
  }

  private String bodyToString(Object body) {
    return Optional.ofNullable(body).map(Object::toString).orElse(EMPTY_BODY);
  }
}
