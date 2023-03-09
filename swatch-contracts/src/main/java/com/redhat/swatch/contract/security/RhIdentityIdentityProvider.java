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
package com.redhat.swatch.contract.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import javax.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

/**
 * IdentityProvider that assumes the passed RhIdentity is valid.
 *
 * <p>Future work could perform validations, but we generally trust the provided identity coming
 * from 3scale.
 */
@Slf4j
@ApplicationScoped
public class RhIdentityIdentityProvider
    implements IdentityProvider<RhIdentityAuthenticationRequest> {
  @Override
  public Class<RhIdentityAuthenticationRequest> getRequestType() {
    return RhIdentityAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      RhIdentityAuthenticationRequest request, AuthenticationRequestContext context) {
    log.trace("x-rh-identity: {}", request.getIdentity());
    QuarkusSecurityIdentity securityIdentity =
        QuarkusSecurityIdentity.builder().setPrincipal(request.getIdentity()).build();
    return Uni.createFrom().item(securityIdentity);
  }
}
