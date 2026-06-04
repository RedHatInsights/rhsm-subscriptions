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
package org.candlepin.subscriptions.resteasy;

import io.micrometer.common.KeyValue;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerHttpObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * Augments the JAX-RS resource URI into spring boot metrics.
 *
 * <p>See <a
 * href="https://docs.spring.io/spring-framework/reference/integration/observability.html">Spring
 * o11y Docs</a>
 *
 * @see ResteasyUriExtractionFilter
 */
@Component
public class ResteasyRequestObservationConvention
    extends DefaultServerRequestObservationConvention {
  @Override
  protected KeyValue uri(ServerRequestObservationContext context) {
    String uriAttribute =
        (String) context.getCarrier().getAttribute(ResteasyUriExtractionFilter.JAXRS_URI);
    if (uriAttribute != null) {
      return KeyValue.of(LowCardinalityKeyNames.URI, uriAttribute);
    }

    // For the scenarios that the default server request observation convention does not address:
    if (context.getPathPattern() == null && !statusIs3xxOr404(context.getResponse())) {
      return KeyValue.of(LowCardinalityKeyNames.URI, context.getCarrier().getRequestURI());
    }

    return super.uri(context);
  }

  private boolean statusIs3xxOr404(HttpServletResponse response) {
    if (response == null) {
      return false;
    }

    HttpStatus status = HttpStatus.resolve(response.getStatus());
    return status != null && (status.is3xxRedirection() || status == HttpStatus.NOT_FOUND);
  }
}
