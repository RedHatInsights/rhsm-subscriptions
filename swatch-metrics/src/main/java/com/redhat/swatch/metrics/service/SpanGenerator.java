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
package com.redhat.swatch.metrics.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Utilities to generate a span identifier in a standard way. The generated span ID will be
 * configured in MDC automatically.
 */
@ApplicationScoped
public class SpanGenerator {
  private static final String NAME = "metering-batch-id";

  /**
   * This method generates the span ID and puts it on MDC.
   *
   * @return the generated span ID.
   */
  public UUID generate() {
    UUID spanId = UUID.randomUUID();
    MDC.put(NAME, spanId.toString());
    return spanId;
  }
}
