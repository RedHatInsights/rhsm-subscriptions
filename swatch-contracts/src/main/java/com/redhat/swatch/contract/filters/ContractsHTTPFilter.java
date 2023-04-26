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
package com.redhat.swatch.contract.filters;

import io.quarkus.logging.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.MDC;

@Provider
public class ContractsHTTPFilter
    implements ContainerRequestFilter,
        ContainerResponseFilter /*,ReaderInterceptor, WriterInterceptor*/ {

  @ConfigProperty(name = "FILTER_ORGS")
  Optional<String> filterOrgs;

  public static final String HTTP_REQUEST_MDC_UUID_KEY = "HTTP_REQUEST_MDC_UUID_KEY";
  public static final String START_TIME = "START_TIME";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    if (filterOrgs.isPresent()) {
      String token = UUID.randomUUID().toString().toUpperCase();
      MDC.put(HTTP_REQUEST_MDC_UUID_KEY, token);
      MDC.put(START_TIME, OffsetDateTime.now().toString());
      Log.debugv(
          "Http request for UUID {0} at start time {1} with URI {2}",
          MDC.get(HTTP_REQUEST_MDC_UUID_KEY),
          MDC.get(START_TIME),
          requestContext.getUriInfo().getPath());
      InputStream entityStream = requestContext.getEntityStream();

      // Buffer the content of the entityStream into a byte array
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int len;
      while ((len = entityStream.read(buffer)) != -1) {
        baos.write(buffer, 0, len);
      }
      byte[] content = baos.toByteArray();
      Log.debugv("Payload sent in request body {0}", baos.toString());

      requestContext.setEntityStream(new ByteArrayInputStream(content));
    }
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    if (filterOrgs.isPresent()
        && Objects.nonNull(MDC.get(HTTP_REQUEST_MDC_UUID_KEY))
        && Objects.nonNull(MDC.get(START_TIME))) {
      var delta =
          Math.abs(
              ChronoUnit.SECONDS.between(
                  OffsetDateTime.parse(MDC.get(START_TIME)), OffsetDateTime.now()));
      Log.debugv(
          "Http request for UUID {0} took total time {1} seconds",
          MDC.get(HTTP_REQUEST_MDC_UUID_KEY), delta);
      Log.debugv(" After reading response ", responseContext.getEntity());
      MDC.remove(HTTP_REQUEST_MDC_UUID_KEY);
      MDC.remove(START_TIME);
    }
  }

  // Option 2 logging with Interceptor
  /*@Override
  public Object aroundReadFrom(ReaderInterceptorContext context)
          throws IOException, WebApplicationException {
      System.err.println("Before reading " + context.getGenericType());
      InputStream entityStream = context.getInputStream();

      // Buffer the content of the entityStream into a byte array
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int len;
      while ((len = entityStream.read(buffer)) != -1) {
          baos.write(buffer, 0, len);
      }
      byte[] content = baos.toByteArray();

      Log.debugv("Payload sent in request body {0}", baos.toString());

      context.setInputStream(new ByteArrayInputStream(content));
      //System.err.println("After reading " + entity);
      Object entity = context.proceed();
      return entity;
  }


  */
  /**
   * @param context invocation context.
   * @throws IOException
   * @throws WebApplicationException
   */
  /*
  @Override
  public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
      //Log.debugv("Http request UUID {0} took total time {1} with response entity {2}",  MDC.get(HTTP_REQUEST_MDC_UUID_KEY), MDC.get(START_TIME), context.getEntity());
  }*/
}
