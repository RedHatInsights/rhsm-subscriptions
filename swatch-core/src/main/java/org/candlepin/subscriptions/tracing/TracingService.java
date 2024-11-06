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
package org.candlepin.subscriptions.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class TracingService {
  private final Tracer tracer;

  /**
   * Create a new span programmatically. Documentation: <a
   * href="https://docs.micrometer.io/tracing/reference/api.html"/>.
   */
  public void executeWithNewSpan(Runnable runnable) {
    // Create a span. If there was a span present in this thread it will become
    // the `newSpan`'s parent.
    Span newSpan = this.tracer.nextSpan();
    // Start a span and put it in scope. Putting in scope means putting the span
    // in thread local
    // and, if configured, adjust the MDC to contain tracing information
    try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan.start())) {
      runnable.run();
    } finally {
      // Once done remember to end the span. This will allow collecting
      // the span to send it to a distributed tracing system e.g. Zipkin
      newSpan.end();
    }
  }

  public static String getCurrentTraceId() {
    return io.opentelemetry.api.trace.Span.current().getSpanContext().getTraceId();
  }
}
