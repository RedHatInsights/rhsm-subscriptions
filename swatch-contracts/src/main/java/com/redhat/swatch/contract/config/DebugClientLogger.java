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

import io.quarkus.arc.Unremovable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.client.api.ClientLogger;

/** ClientLogger implementation that only logs requests for a matching URI */
@Slf4j
@Unremovable
@ApplicationScoped
public class DebugClientLogger implements ClientLogger {

  @ConfigProperty(name = "rest-client-debug-logging.uri-filter")
  Pattern uriFilterPattern;

  @Override
  public void setBodySize(int bodySize) {
    // intentionally ignored
  }

  @Override
  public void logResponse(HttpClientResponse response, boolean redirect) {
    if (uriFilterPattern.matcher(response.request().getURI()).matches()) {
      response.bodyHandler(body -> log.debug("Response: \n{}", body.toString()));
    }
  }

  @Override
  public void logRequest(HttpClientRequest request, Buffer body, boolean omitBody) {
    if (uriFilterPattern.matcher(request.getURI()).matches()) {
      log.debug(
          "Request method={} URI={}: \n{}", request.getMethod(), request.getURI(), body.toString());
    }
  }
}
