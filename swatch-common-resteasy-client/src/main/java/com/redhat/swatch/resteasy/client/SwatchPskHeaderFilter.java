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
package com.redhat.swatch.resteasy.client;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Resteasy client filter that adds x-rh-swatch-psk to requests based on the value of the
 * SWATCH_SELF_PSK property/environment variable.
 *
 * <p>Use by configuring as a provider on a rest client instance, e.g. <code>
 * quarkus.rest-client."com.redhat.swatch.clients.swatch.internal.subscription.api.resources.InternalSubscriptionsApi".providers=com.redhat.swatch.resteasy.client.SwatchPskHeaderFilter
 * </code>
 */
@Slf4j
// NOTE: without @Unremovable quarkus attempts to optimize this bean out because it's only
// referenced in application.properties
@Unremovable
@ApplicationScoped
public class SwatchPskHeaderFilter implements ClientRequestFilter {

  @ConfigProperty(name = "SWATCH_SELF_PSK")
  String psk;

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add("x-rh-swatch-psk", psk);
  }
}
