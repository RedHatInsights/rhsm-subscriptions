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
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import java.security.Principal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Component that adds roles to principals as needed.
 * <li>service - granted to x-rh-swatch-psk authenticated principals
 * <li>support - granted to x-rh-identity authenticated principals when type is "Associate".
 * <li>test - granted to any authenticated principal when SWATCH_TEST_APIS_ENABLED is true.
 */
@Slf4j
@ApplicationScoped
public class RolesAugmentor implements SecurityIdentityAugmentor {
  @ConfigProperty(name = "SWATCH_TEST_APIS_ENABLED")
  boolean testApisEnabled;

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    Principal principal = identity.getPrincipal();
    Set<String> roles = new HashSet<>();
    if (!identity.isAnonymous() && testApisEnabled) {
      roles.add("test");
    }
    if (principal instanceof RhIdentityPrincipal && isAssociate((RhIdentityPrincipal) principal)) {
      roles.add("support");
    }
    if (principal instanceof PskPrincipal) {
      roles.add("service");
    }
    if (!identity.isAnonymous()) {
      log.debug("Granting roles {} to user: {}", roles, principal.getName());
    }
    return Uni.createFrom().item(QuarkusSecurityIdentity.builder(identity).addRoles(roles).build());
  }

  private static boolean isAssociate(RhIdentityPrincipal principal) {
    return Objects.equals("Associate", principal.getIdentity().getType());
  }
}
