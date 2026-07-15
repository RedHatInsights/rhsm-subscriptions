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

import io.getunleash.Unleash;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@IfBuildProperty(name = "swatch-common-security.include-rbac", stringValue = "true")
public class KesselRolesAugmentor implements SecurityIdentityAugmentor {

  public static final String KESSEL_FLAG = "swatch.common-security.use-kessel-rbac";

  private static final String RBAC_ADMIN_PERMISSION = "subscriptions:*:*";
  private static final String RBAC_READER_PERMISSION = "subscriptions:reports:read";

  @Inject Unleash unleash;
  @Inject KesselAuthorizationService kesselService;

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    if (!unleash.isEnabled(KESSEL_FLAG)) {
      return Uni.createFrom().item(identity);
    }

    Principal principal = identity.getPrincipal();
    if (principal instanceof RhIdentityPrincipal rhIdentityPrincipal
        && shouldCheck(rhIdentityPrincipal)) {
      return context.runBlocking(() -> lookupKesselRoles(identity));
    }
    return Uni.createFrom().item(identity);
  }

  private SecurityIdentity lookupKesselRoles(SecurityIdentity identity) {
    var principal = (RhIdentityPrincipal) identity.getPrincipal();
    List<String> permissions;
    try {
      permissions = kesselService.getPermissions(principal);
    } catch (Exception e) {
      log.warn(
          "Error adding roles via Kessel service integration for user={} orgId={}: {}",
          principal.getName(),
          principal.getIdentity().getOrgId(),
          e.getMessage());
      return identity;
    }

    Set<String> effectiveRoles = determineEffectiveRoles(permissions);
    if (effectiveRoles.isEmpty()) {
      log.warn(
          "Kessel returned no matching permissions for user={} orgId={} (permissions={}); "
              + "request will likely be denied",
          principal.getName(),
          principal.getIdentity().getOrgId(),
          permissions);
    } else {
      log.debug(
          "Kessel granted roles {} for user={} orgId={}",
          effectiveRoles,
          principal.getName(),
          principal.getIdentity().getOrgId());
    }
    return QuarkusSecurityIdentity.builder(identity).addRoles(effectiveRoles).build();
  }

  private Set<String> determineEffectiveRoles(List<String> permissions) {
    if (permissions.contains(RBAC_ADMIN_PERMISSION)
        || permissions.contains(RBAC_READER_PERMISSION)) {
      return Set.of("customer");
    }
    return Set.of();
  }

  private static boolean shouldCheck(RhIdentityPrincipal principal) {
    return Objects.equals("User", principal.getIdentity().getType())
        || Objects.equals("ServiceAccount", principal.getIdentity().getType());
  }
}
