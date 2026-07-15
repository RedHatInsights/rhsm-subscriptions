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
package org.candlepin.subscriptions.security;

import io.getunleash.Unleash;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.rbac.KesselService;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.Attributes2GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Class in charge of populating the security context with the users roles based on the values in
 * the x-rh-identity header.
 */
public class IdentityHeaderAuthenticationDetailsService
    implements AuthenticationUserDetailsService<Authentication> {

  private static final Logger log =
      LoggerFactory.getLogger(IdentityHeaderAuthenticationDetailsService.class);

  private Attributes2GrantedAuthoritiesMapper authMapper;
  private SecurityProperties props;
  private RbacProperties rbacProps;
  private RoleProvider roleProvider;
  private RbacService rbacController;
  private KesselService kesselService;
  private Unleash unleash;

  static final String KESSEL_FLAG = "swatch.common-security.use-kessel-rbac";

  public IdentityHeaderAuthenticationDetailsService(
      SecurityProperties props,
      RbacProperties rbacProps,
      Attributes2GrantedAuthoritiesMapper authMapper,
      RbacService rbacController) {
    this(props, rbacProps, authMapper, rbacController, null, null);
  }

  public IdentityHeaderAuthenticationDetailsService(
      SecurityProperties props,
      RbacProperties rbacProps,
      Attributes2GrantedAuthoritiesMapper authMapper,
      RbacService rbacController,
      KesselService kesselService,
      Unleash unleash) {
    this.rbacProps = rbacProps;
    this.authMapper = authMapper;
    this.props = props;
    this.rbacController = rbacController;
    this.kesselService = kesselService;
    this.unleash = unleash;

    log.debug(
        "Auth permission source: kesselService={}, unleash={}",
        kesselService != null ? "present" : "absent",
        unleash != null ? "present" : "absent");

    if (props.isDevMode()) {
      log.info("Running in DEV mode. Security will be disabled.");
    }
    this.roleProvider = new RoleProvider(rbacProps.getApplicationName(), props.isDevMode());
  }

  protected Collection<String> getUserRoles() {
    return roleProvider.getRoles(
        props.isDevMode() ? Collections.emptyList() : getPermissions(null));
  }

  private Collection<String> getUserRoles(InsightsUserPrincipal user) {
    return roleProvider.getRoles(
        props.isDevMode() ? Collections.emptyList() : getPermissions(user));
  }

  @Override
  public UserDetails loadUserDetails(Authentication authentication) {
    Object principal = authentication.getPrincipal();

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)
        && auth.getDetails() instanceof UserDetails userDetails) {
      log.debug(
          "Reusing roles from existing SecurityContext authentication (type={})",
          auth.getClass().getSimpleName());
      return userDetails;
    }

    log.debug(
        "Loading user roles for principal type={}, devMode={}",
        principal.getClass().getSimpleName(),
        props.isDevMode());

    Collection<String> userRoles;
    if (principal instanceof InsightsUserPrincipal insightsUserPrincipal) {
      userRoles = getUserRoles(insightsUserPrincipal);
      log.debug("Resolved roles for orgId={}: {}", insightsUserPrincipal.getOrgId(), userRoles);
    } else {
      userRoles = Collections.singleton("INTERNAL");
      log.debug(
          "Assigned INTERNAL role for principal type={}", principal.getClass().getSimpleName());
    }
    Collection<? extends GrantedAuthority> userGAs = authMapper.getGrantedAuthorities(userRoles);

    log.debug("Mapped roles {} to granted authorities: {}", userRoles, userGAs);

    return new User("N/A", "N/A", userGAs);
  }

  public void setUserRoles2GrantedAuthoritiesMapper(
      Attributes2GrantedAuthoritiesMapper authMapper) {
    this.authMapper = authMapper;
  }

  private List<String> getPermissions(InsightsUserPrincipal user) {
    if (isKesselEnabled()) {
      if (user == null) {
        log.warn("Kessel enabled but no InsightsUserPrincipal available; denying access");
        return Collections.emptyList();
      }
      List<String> permissions = getKesselPermissions(user);
      log.debug("Permissions from Kessel: {}", permissions);
      return permissions;
    }
    log.debug("Fetching permissions from RBAC application={}", rbacProps.getApplicationName());
    try {
      List<String> permissions = rbacController.getPermissions(rbacProps.getApplicationName());
      log.debug("Permissions from RBAC: {}", permissions);
      return permissions;
    } catch (RbacApiException e) {
      log.warn("Unable to determine roles from RBAC service.", e);
      return Collections.emptyList();
    }
  }

  private boolean isKesselEnabled() {
    boolean flagEnabled = unleash != null && unleash.isEnabled(KESSEL_FLAG);
    boolean enabled = kesselService != null && flagEnabled;
    log.debug(
        "Kessel RBAC enabled: {} (kesselServicePresent={}, unleashPresent={}, flag={})",
        enabled,
        kesselService != null,
        unleash != null,
        flagEnabled);
    return enabled;
  }

  private List<String> getKesselPermissions(InsightsUserPrincipal user) {
    try {
      return user.getKesselPrincipalId()
          .map(
              principalId -> {
                log.debug(
                    "Fetching Kessel permissions for orgId={}, principalId={}",
                    user.getOrgId(),
                    principalId);
                return kesselService.getPermissions(principalId);
              })
          .orElseGet(
              () -> {
                log.warn(
                    "Cannot determine Kessel principal id for orgId={}; denying access",
                    user.getOrgId());
                return Collections.emptyList();
              });
    } catch (Exception e) {
      log.warn("Unable to determine roles from Kessel service.", e);
      return Collections.emptyList();
    }
  }
}
