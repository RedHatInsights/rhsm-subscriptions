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
package com.redhat.swatch.contract.resource;

import static com.redhat.swatch.common.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;

import com.redhat.swatch.common.security.PskPrincipal;
import com.redhat.swatch.common.security.RhIdentityPrincipalFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class OrgIdResolver {

  @Inject RhIdentityPrincipalFactory identityPrincipalFactory;

  /**
   * Resolves the orgId from the given security context and HTTP headers.
   *
   * @param securityContext the JAX-RS security context
   * @param httpHeaders the JAX-RS HTTP headers
   * @return the orgId for the current request
   */
  public String getOrgId(SecurityContext securityContext, HttpHeaders httpHeaders) {
    var principal = securityContext.getUserPrincipal();
    if (principal instanceof PskPrincipal) {
      String identityHeader = httpHeaders.getHeaderString(RH_IDENTITY_HEADER);
      if (identityHeader != null) {
        try {
          return identityPrincipalFactory.fromHeader(identityHeader).getName();
        } catch (Exception e) {
          log.warn("Failed to decode x-rh-identity header for orgId extraction", e);
        }
      }
      log.warn("PSK-authenticated request without x-rh-identity header; cannot determine orgId");
    }
    return principal.getName();
  }
}
