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
package org.candlepin.subscriptions.conduit.resteasy;

import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * A ContextResolver responsible for customizing the configuration of Jackson's JsonMapper instance.
 * This is marked as a Provider so that RestEasy will use the configured JsonMapper provided by this
 * instance.
 */
@Provider
@Component
public class ObjectMapperContextResolver implements ContextResolver<JsonMapper> {

  private final JsonMapper objectMapper;

  public ObjectMapperContextResolver(JsonMapper mapper) {
    this.objectMapper = mapper;
  }

  @Override
  public JsonMapper getContext(Class<?> objectType) {
    return objectMapper;
  }
}
