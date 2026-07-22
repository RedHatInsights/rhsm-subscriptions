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
package com.redhat.swatch.info;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.vertx.http.ManagementInterface;
import io.vertx.core.buffer.Buffer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/** Registers {@code GET /info} on the Quarkus management interface. */
@ApplicationScoped
public class InfoRoute {

  private static final String CONTENT_TYPE_JSON = "application/json";

  private final InfoAggregator aggregator;
  private final ObjectMapper objectMapper;

  @Inject
  public InfoRoute(InfoAggregator aggregator, ObjectMapper objectMapper) {
    this.aggregator = aggregator;
    this.objectMapper = objectMapper;
  }

  void register(@Observes ManagementInterface managementInterface) {
    managementInterface
        .router()
        .get("/info")
        .handler(
            routingContext -> {
              try {
                byte[] body = objectMapper.writeValueAsBytes(aggregator.buildInfo());
                routingContext
                    .response()
                    .putHeader("Content-Type", CONTENT_TYPE_JSON)
                    .end(Buffer.buffer(body));
              } catch (JsonProcessingException e) {
                routingContext.fail(e);
              }
            });
  }
}
