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

import com.redhat.swatch.clients.rbac.api.model.Access;
import com.redhat.swatch.clients.rbac.api.resources.AccessApi;
import com.redhat.swatch.clients.rbac.api.resources.ApiException;
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
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Configurable RBAC-based SecurityIdentityAugmentor for SWATCH services.
 * 
 * This component adds roles from RBAC service to customer and service account principals.
 * Can be customized per service by setting the application name.
 */
@Slf4j
@ApplicationScoped
public class SwatchRbacSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

  private static final List<String> ALL_ROLES = List.of("test", "support", "service", "customer");

  @ConfigProperty(name = "swatch.rbac.application-name", defaultValue = "subscriptions")
  String rbacApplicationName;

  @ConfigProperty(name = "RBAC_ENABLED", defaultValue = "true")
  boolean rbacEnabled;

  @Inject @RestClient AccessApi accessApi;

  @Override
  public Uni<SecurityIdentity> augment(
      SecurityIdentity identity, AuthenticationRequestContext context) {
    if (!rbacEnabled) {
      return Uni.createFrom().item(buildWithAllRoles(identity));
    }

    Principal principal = identity.getPrincipal();
    if (principal instanceof RhIdentityPrincipal rhIdentityPrincipal
        && shouldCallRbac(rhIdentityPrincipal)) {
      return context.runBlocking(() -> lookupRbacRoles(identity));
    }
    return Uni.createFrom().item(identity);
  }

  private Supplier<SecurityIdentity> buildWithAllRoles(SecurityIdentity identity) {
    // create a new builder and copy principal, attributes, credentials and roles from the original identity
    QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);

    // add all the roles here for dev/test scenarios
    ALL_ROLES.forEach(builder::addRole);
    return builder::build;
  }

  private SecurityIdentity lookupRbacRoles(SecurityIdentity identity) {
    List<String> rbacServiceRoles;
    try {
      rbacServiceRoles = getPermissions((RhIdentityPrincipal) identity.getPrincipal());
    } catch (Exception e) {
      log.warn("Error adding roles via RBAC service integration: {}", e.getMessage());
      return identity;
    }
    
    Set<String> effectiveRoles = determineEffectiveRoles(rbacServiceRoles);
    return QuarkusSecurityIdentity.builder(identity).addRoles(effectiveRoles).build();
  }

  /**
   * Determine effective roles based on RBAC permissions.
   * Override this method in subclasses for service-specific role mapping.
   */
  protected Set<String> determineEffectiveRoles(List<String> rbacServiceRoles) {
    String adminRole = String.format("%s:*:*", rbacApplicationName);
    String readerRole = String.format("%s:reports:read", rbacApplicationName);
    
    if (rbacServiceRoles.contains(adminRole) || rbacServiceRoles.contains(readerRole)) {
      return Set.of("customer");
    } else {
      return Set.of();
    }
  }

  /**
   * Check if RBAC should be called for this principal.
   * Includes both regular users and service accounts.
   */
  private static boolean shouldCallRbac(RhIdentityPrincipal principal) {
    return isCustomer(principal) || isServiceAccount(principal);
  }

  private static boolean isCustomer(RhIdentityPrincipal principal) {
    return Objects.equals("User", principal.getIdentity().getType());
  }

  private static boolean isServiceAccount(RhIdentityPrincipal principal) {
    return Objects.equals("ServiceAccount", principal.getIdentity().getType());
  }

  /**
   * Get permissions from RBAC service for the given principal.
   */
  public List<String> getPermissions(RhIdentityPrincipal principal) throws ApiException {
    // Get all permissions for the configured application name
    return accessApi
        .getPrincipalAccess(rbacApplicationName, null, principal.getHeaderValue(), null, null)
        .getData()
        .stream()
        .map(Access::getPermission)
        .toList();
  }
}