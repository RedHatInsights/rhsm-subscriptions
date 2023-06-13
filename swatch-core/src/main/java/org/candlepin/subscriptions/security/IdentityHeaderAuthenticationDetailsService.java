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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public IdentityHeaderAuthenticationDetailsService(
      SecurityProperties props,
      RbacProperties rbacProps,
      Attributes2GrantedAuthoritiesMapper authMapper,
      RbacService rbacController) {
    this.rbacProps = rbacProps;
    this.authMapper = authMapper;
    this.props = props;
    this.rbacController = rbacController;

    if (props.isDevMode()) {
      log.info("Running in DEV mode. Security will be disabled.");
    }
    this.roleProvider = new RoleProvider(rbacProps.getApplicationName(), props.isDevMode());
  }

  protected Collection<String> getUserRoles() {
    return roleProvider.getRoles(props.isDevMode() ? Collections.emptyList() : getPermissions());
  }

  @Override
  public UserDetails loadUserDetails(Authentication authentication) {
    Object principal = authentication.getPrincipal();

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      // We've already populated the roles if the auth has been set in the context.
      return (UserDetails) auth.getDetails();
    }
    Collection<String> userRoles;
    if (principal instanceof InsightsUserPrincipal) {
      userRoles = getUserRoles();
    } else {
      userRoles = Collections.singleton("INTERNAL");
    }
    Collection<? extends GrantedAuthority> userGAs = authMapper.getGrantedAuthorities(userRoles);

    log.debug("Roles {} mapped to Granted Authorities: {}", userRoles, userGAs);

    return new User("N/A", "N/A", userGAs);
  }

  public void setUserRoles2GrantedAuthoritiesMapper(
      Attributes2GrantedAuthoritiesMapper authMapper) {
    this.authMapper = authMapper;
  }

  private List<String> getPermissions() {
    try {
      return rbacController.getPermissions(rbacProps.getApplicationName());
    } catch (RbacApiException e) {
      log.warn("Unable to determine roles from RBAC service.", e);
      return Collections.emptyList();
    }
  }
}
