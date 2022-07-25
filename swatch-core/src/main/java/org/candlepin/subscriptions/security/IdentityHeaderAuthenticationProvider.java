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

import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER;
import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_PSK_HEADER;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.Attributes2GrantedAuthoritiesMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * This class is responsible for validating the principal. If a valid principal is found, the
 * authenticate method will build a new Authentication object that is marked as being successfully
 * authenticated ("blessed") and with the granted authorities from the Authentication.
 *
 * <p>Heavily inspired by {@link PreAuthenticatedAuthenticationProvider}.
 */
public class IdentityHeaderAuthenticationProvider implements AuthenticationProvider, Ordered {
  private static final Logger log =
      LoggerFactory.getLogger(IdentityHeaderAuthenticationProvider.class);

  private static final String ROLE_INTERNAL = "ROLE_INTERNAL";

  private int order = 2; // default: same as non-ordered

  private final IdentityHeaderAuthenticationDetailsService userDetailsService;

  private Attributes2GrantedAuthoritiesMapper authMapper;

  private final Map<String, String> pskAppMap;

  public IdentityHeaderAuthenticationProvider(
      IdentityHeaderAuthenticationDetailsService userDetailsService,
      Attributes2GrantedAuthoritiesMapper authMapper,
      AuthProperties authProperties) {
    this.userDetailsService = userDetailsService;
    this.authMapper = authMapper;
    this.pskAppMap = invertPskAppMap(authProperties.getSwatchPsks());
  }

  /**
   * In our Spring configuration, we want something like <code>
   * rhsm-subscriptions.auth.swatch-psk.user=123</code>. But in the application, we want the reverse
   * so we can see what application the user's key is issued to.
   */
  private Map<String, String> invertPskAppMap(Map<String, String> swatchPsks) {
    Map<String, String> invertedMap = new HashMap<>(swatchPsks.size());

    for (Map.Entry<String, String> e : swatchPsks.entrySet()) {
      if (invertedMap.containsKey(e.getValue())) {
        throw new IllegalStateException(
            "The same PSK is affiliated with two applications. This is a configuration error");
      }
      invertedMap.put(e.getValue(), e.getKey());
    }
    return invertedMap;
  }

  /**
   * Validates the incoming principal that was extracted from the x-rh-identity or x-rh-swatch-psk
   * header by the {@link IdentityHeaderAuthenticationFilter}. The principal is considered
   * authenticated if the account_number and org_id are present as this would have come from 3Scale
   * or if the PSK exists for a client.
   *
   * @param authentication contains the pre-authenticated principal created from x-rh-identity or
   *     x-rh-swatch-psk
   * @return an approved Authentication object
   * @throws AuthenticationException if any part of the principal is invalid.
   */
  @Override
  public Authentication authenticate(Authentication authentication) {
    if (!supports(authentication.getClass())) {
      return null;
    }

    log.debug("PreAuthenticated authentication request: {}", authentication);

    Object principal = authentication.getPrincipal();

    if (principal instanceof PskClientPrincipal) {
      return authenticatePsk(authentication, (PskClientPrincipal) principal);
    } else if (principal instanceof InsightsUserPrincipal) {
      validateUserPrincipal(authentication, (InsightsUserPrincipal) principal);
    }

    // load roles from the header and/or RBAC
    UserDetails userDetails = userDetailsService.loadUserDetails(authentication);

    PreAuthenticatedAuthenticationToken result =
        new PreAuthenticatedAuthenticationToken(
            principal, authentication.getCredentials(), userDetails.getAuthorities());
    result.setAuthenticated(true); // this is actually done in the constructor but explicit is good
    result.setDetails(authentication.getDetails());

    return result;
  }

  private Authentication authenticatePsk(
      Authentication authentication, PskClientPrincipal pskClientPrincipal) {
    if (!StringUtils.hasText(pskClientPrincipal.getPreSharedKey())
        || !pskAppMap.containsKey(pskClientPrincipal.getPreSharedKey())) {
      throw new PreAuthenticatedCredentialsNotFoundException(RH_PSK_HEADER + " is invalid");
    }
    var pskClient = pskAppMap.get(pskClientPrincipal.getPreSharedKey());
    var roles = Collections.singleton(ROLE_INTERNAL);
    Collection<? extends GrantedAuthority> clientGAs = authMapper.getGrantedAuthorities(roles);
    PreAuthenticatedAuthenticationToken result =
        new PreAuthenticatedAuthenticationToken(
            pskClient, authentication.getCredentials(), clientGAs);
    result.setAuthenticated(true);
    result.setDetails(authentication.getDetails());
    return result;
  }

  private void validateUserPrincipal(
      Authentication authentication, InsightsUserPrincipal principal) {
    try {
      Assert.notNull(
          authentication.getPrincipal(), "No pre-authenticated principal found in request.");
      Assert.hasText(
          principal.getOwnerId(), RH_IDENTITY_HEADER + " contains no owner ID for the principal");
      Assert.notNull(authentication.getDetails(), RH_IDENTITY_HEADER + "contains no roles");
    } catch (IllegalArgumentException e) {
      throw new PreAuthenticatedCredentialsNotFoundException(
          RH_IDENTITY_HEADER + " is missing required data", e);
    }
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
  }

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int i) {
    this.order = i;
  }
}
