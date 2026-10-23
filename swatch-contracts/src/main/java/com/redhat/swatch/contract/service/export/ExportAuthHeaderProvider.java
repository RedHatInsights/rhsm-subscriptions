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

import com.redhat.swatch.clowder.DependencyEndpoint;
import com.redhat.swatch.common.security.KesselAuthorizationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Export-service request auth, matching host-inventory Clowder V2 behavior.
 *
 * <p>{@code EXPORT_SERVICE_AUTHENTICATED=true} (from Clowder {@code authenticated} or env) attaches
 * an OAuth2 Bearer token. Otherwise sends {@code x-rh-exports-psk}. This is independent of V1/V2
 * URI selection.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class ExportAuthHeaderProvider implements ClientRequestFilter {

  private final KesselAuthorizationService kesselAuthorizationService;

  @ConfigProperty(name = "EXPORT_SERVICE_AUTHENTICATED")
  Optional<String> authenticated;

  @ConfigProperty(name = "SWATCH_EXPORT_PSK", defaultValue = "placeholder")
  String psk;

  static boolean useBearerAuth(Optional<String> authenticated) {
    Boolean flag = DependencyEndpoint.parseAuthenticated(authenticated.orElse(null));
    return Boolean.TRUE.equals(flag);
  }

  boolean usesBearerAuth() {
    return useBearerAuth(authenticated);
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    if (usesBearerAuth()) {
      String token = kesselAuthorizationService.getAccessToken();
      requestContext.getHeaders().putSingle("Authorization", "Bearer " + token);
    } else {
      requestContext.getHeaders().putSingle("x-rh-exports-psk", psk);
    }
  }
}
