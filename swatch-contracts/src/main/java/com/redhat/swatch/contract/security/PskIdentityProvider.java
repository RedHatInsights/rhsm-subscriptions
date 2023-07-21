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

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * IdentityProvider that checks the provided PSK against the configured PSK.
 *
 * @see PskPrincipal
 */
@Slf4j
@ApplicationScoped
public class PskIdentityProvider implements IdentityProvider<PskAuthenticationRequest> {
  @ConfigProperty(name = "SWATCH_SELF_PSK")
  String psk;

  @Override
  public Class<PskAuthenticationRequest> getRequestType() {
    return PskAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      PskAuthenticationRequest request, AuthenticationRequestContext context) {
    if (!Objects.equals(psk, request.getPsk())) {
      log.error("PSK auth attempted, but failed because it was incorrect");
      return Uni.createFrom()
          .failure(new AuthenticationFailedException("x-rh-swatch-psk specified but incorrect"));
    }
    log.trace("PSK auth succeeded");
    QuarkusSecurityIdentity securityIdentity =
        QuarkusSecurityIdentity.builder().setPrincipal(new PskPrincipal()).build();
    return Uni.createFrom().item(securityIdentity);
  }
}
