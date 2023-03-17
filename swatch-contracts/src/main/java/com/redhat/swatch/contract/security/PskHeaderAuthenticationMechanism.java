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
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import java.util.Set;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import lombok.extern.slf4j.Slf4j;

/**
 * Allows authentication attempts via x-rh-swatch-psk header.
 *
 * @see PskIdentityProvider
 * @see PskAuthenticationRequest
 */
@Slf4j
@Alternative
@Priority(1)
@ApplicationScoped
public class PskHeaderAuthenticationMechanism implements HttpAuthenticationMechanism {
  private static final String PSK_HEADER = "x-rh-swatch-psk";

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    String pskHeader = context.request().headers().get(PSK_HEADER);
    if (StringUtil.isNullOrEmpty(pskHeader)) {
      // no authentication attempted
      return Uni.createFrom().nullItem();
    }
    // NOTE: it is important we call identityProviderManager.authenticate rather than building a
    // SecurityIdentity directly, as identityProviderManager is responsible for invoking the
    // RolesAugmentor
    return identityProviderManager.authenticate(new PskAuthenticationRequest(pskHeader));
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom().nullItem();
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Set.of(PskAuthenticationRequest.class);
  }
}
