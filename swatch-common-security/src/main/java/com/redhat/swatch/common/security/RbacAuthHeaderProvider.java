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
package com.redhat.swatch.common.security;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Attached only to AccessApi, covering interactive and background RBAC permission checks. */
@Unremovable
@ApplicationScoped
public class RbacAuthHeaderProvider implements ClientRequestFilter {

  @ConfigProperty(name = "RBAC_AUTHENTICATED", defaultValue = "false")
  boolean authenticated;

  @Inject HccAuthTokenProvider tokenProvider;

  @Override
  public void filter(ClientRequestContext requestContext) {
    if (authenticated) {
      // Preserve x-rh-identity: workload authentication does not replace the user's identity.
      requestContext
          .getHeaders()
          .putSingle(HttpHeaders.AUTHORIZATION, tokenProvider.authorizationHeader());
    }
  }
}
