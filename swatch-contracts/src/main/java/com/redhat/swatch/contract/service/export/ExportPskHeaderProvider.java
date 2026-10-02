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
package com.redhat.swatch.contract.service.export;

import com.redhat.swatch.common.security.HccAuthTokenProvider;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Selects workload Bearer authentication or the legacy Export Service PSK for every request,
 * including uploads and error callbacks. The historical class name is kept for compatibility.
 *
 * <p>Use by configuring as a provider on a rest client instance, e.g. <code>
 * quarkus.rest-client."com.redhat.swatch.clients.export.api.resources.ExportApi".providers=com.redhat.swatch.contract.service.export.ExportPskHeaderProvider
 * </code>
 */
// NOTE: without @Unremovable quarkus attempts to optimize this bean out because it's only
// referenced in application.properties
@Unremovable
@ApplicationScoped
public class ExportPskHeaderProvider implements ClientRequestFilter {

  @ConfigProperty(name = "SWATCH_EXPORT_PSK")
  Optional<String> psk;

  @ConfigProperty(name = "EXPORT_SERVICE_AUTHENTICATED", defaultValue = "false")
  boolean authenticated;

  @Inject HccAuthTokenProvider tokenProvider;

  @Override
  public void filter(ClientRequestContext requestContext) {
    var headers = requestContext.getHeaders();
    if (authenticated) {
      String authorization = tokenProvider.authorizationHeader();
      headers.remove("x-rh-exports-psk");
      headers.putSingle(HttpHeaders.AUTHORIZATION, authorization);
    } else {
      String secret =
          psk.filter(value -> !value.isBlank())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "SWATCH_EXPORT_PSK is required for legacy Export Service authentication"));
      headers.remove(HttpHeaders.AUTHORIZATION);
      headers.putSingle("x-rh-exports-psk", secret);
    }
  }
}
