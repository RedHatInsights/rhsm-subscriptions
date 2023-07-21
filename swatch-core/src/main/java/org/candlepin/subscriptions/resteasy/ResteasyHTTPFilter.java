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

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Provider
@Slf4j
public class ResteasyHTTPFilter implements ContainerRequestFilter, ContainerResponseFilter {

  @Value("${FILTER_ORGS:}")
  List<String> filterOrgs;

  public static final String HTTP_REQUEST_MDC_UUID_KEY = "HTTP_REQUEST_MDC_UUID_KEY";
  public static final String START_TIME = "START_TIME";

  @Context SecurityContext securityContext;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (Objects.nonNull(securityContext.getUserPrincipal())
        && filterOrgs.contains(securityContext.getUserPrincipal().getName())) {
      String token = UUID.randomUUID().toString().toUpperCase();
      MDC.put(HTTP_REQUEST_MDC_UUID_KEY, token);
      MDC.put(START_TIME, OffsetDateTime.now().toString());
      log.debug(
          "Http Request UUID {} at start time {} with URI {}",
          MDC.get(HTTP_REQUEST_MDC_UUID_KEY),
          MDC.get(START_TIME),
          requestContext.getUriInfo().getPath());

      MultivaluedMap<String, String> queryParameters =
          requestContext.getUriInfo().getQueryParameters();
      if (!queryParameters.isEmpty()) {
        log.debug(
            "Query parameters {}",
            queryParameters.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(", ")));
      }

      InputStream entityStream = requestContext.getEntityStream();

      // Buffer the content of the entityStream into a byte array
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int len;
      while ((len = entityStream.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
      byte[] content = baos.toByteArray();
      if (content.length > 0) {
        log.debug("Payload sent in request body {}", baos);
      }

      requestContext.setEntityStream(new ByteArrayInputStream(content));
    }
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    if (Objects.nonNull(securityContext.getUserPrincipal())
        && Objects.nonNull(MDC.get(HTTP_REQUEST_MDC_UUID_KEY))
        && Objects.nonNull(MDC.get(START_TIME))) {
      var delta =
          Math.abs(
              ChronoUnit.MILLIS.between(
                  OffsetDateTime.parse(MDC.get(START_TIME)), OffsetDateTime.now()));
      log.debug(
          "Http Request UUID {} took total time {} milli-seconds",
          MDC.get(HTTP_REQUEST_MDC_UUID_KEY),
          delta);
      log.debug(" After reading http response {}", responseContext.getEntity());
      MDC.remove(HTTP_REQUEST_MDC_UUID_KEY);
      MDC.remove(START_TIME);
    }
  }
}
