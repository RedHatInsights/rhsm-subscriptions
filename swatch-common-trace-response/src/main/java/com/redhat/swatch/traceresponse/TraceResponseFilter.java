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
package com.redhat.swatch.traceresponse;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerResponseContext;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

/** See https://w3c.github.io/trace-context/#traceresponse-header for reference and format */
@ApplicationScoped
@Slf4j
public class TraceResponseFilter {

  @ServerResponseFilter
  public void responseBasicHeaderFilter(ContainerResponseContext responseContext) {
    SpanContext spanContext = Span.current().getSpanContext();
    responseContext
        .getHeaders()
        .putSingle(
            "traceresponse",
            String.format(
                "00-%s-%s-%s  ",
                spanContext.getTraceId(), spanContext.getSpanId(), spanContext.getTraceFlags()));
  }

  @ServerResponseFilter
  public void responseLoggingFilter(ContainerResponseContext responseContext) {
    log.debug(
        "Sent: [traceresponse: {}] {} {}",
        responseContext.getHeaderString("traceresponse"),
        responseContext.getStatusInfo(),
        responseContext.getEntity());
  }
}
