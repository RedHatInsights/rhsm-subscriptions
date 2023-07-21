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

import io.quarkus.runtime.util.StringUtil;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Allows authentication attempts via x-rh-identity header.
 *
 * <p>Handles the decoding of the header; then delegates validation of the properly formed identity
 * to {@link RhIdentityIdentityProvider}.
 *
 * @see RhIdentityIdentityProvider
 * @see RhIdentityAuthenticationRequest
 */
@Slf4j
@Alternative
@Priority(1)
@ApplicationScoped
public class RhIdentityHeaderAuthenticationMechanism implements HttpAuthenticationMechanism {
  private static final String RH_IDENTITY_HEADER = "x-rh-identity";

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String xRhIdentityHeader = context.request().headers().get(RH_IDENTITY_HEADER);
    if (StringUtil.isNullOrEmpty(xRhIdentityHeader)) {
      // no authentication attempted
      return Uni.createFrom().nullItem();
    }
    try {
      RhIdentityPrincipal identity = RhIdentityPrincipal.fromHeader(xRhIdentityHeader);
      // NOTE: it is important we call identityProviderManager.authenticate rather than building a
      // SecurityIdentity directly, as identityProviderManager is responsible for invoking the
      // RolesAugmentor
      return identityProviderManager.authenticate(new RhIdentityAuthenticationRequest(identity));
    } catch (Exception e) {
      return Uni.createFrom().failure(new AuthenticationFailedException(e));
    }
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom().nullItem();
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Set.of(RhIdentityAuthenticationRequest.class);
  }
}
