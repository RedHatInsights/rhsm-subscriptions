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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.kessel.KesselAuthorizationClient;
import io.getunleash.Unleash;
import java.util.List;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class IdentityHeaderAuthenticationDetailsServiceTest {

  @Mock RbacService rbacService;
  @Mock KesselAuthorizationClient kesselService;
  @Mock Unleash unleash;

  SecurityProperties securityProperties;
  RbacProperties rbacProperties;
  IdentityHeaderAuthoritiesMapper authMapper;

  @BeforeEach
  void setup() {
    securityProperties = new SecurityProperties();
    securityProperties.setDevMode(false);

    rbacProperties = new RbacProperties();
    rbacProperties.setApplicationName("subscriptions");

    authMapper = new IdentityHeaderAuthoritiesMapper();
  }

  private IdentityHeaderAuthenticationDetailsService createService(
      KesselAuthorizationClient kessel, Unleash unleash) {
    return new IdentityHeaderAuthenticationDetailsService(
        securityProperties, rbacProperties, authMapper, rbacService, kessel, unleash);
  }

  private PreAuthenticatedAuthenticationToken authWithPrincipal(InsightsUserPrincipal principal) {
    return new PreAuthenticatedAuthenticationToken(principal, "N/A");
  }

  private static final ObjectMapper mapper = new ObjectMapper();

  private InsightsUserPrincipal userPrincipal() {
    try {
      return mapper.readValue(
          """
          {
            "user_id": "user123",
            "internal": {"org_id": "org123"},
            "type": "User"
          }
          """,
          InsightsUserPrincipal.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private InsightsUserPrincipal userPrincipalWithoutUserId() {
    try {
      return mapper.readValue(
          """
          {
            "internal": {"org_id": "org123"},
            "type": "User"
          }
          """,
          InsightsUserPrincipal.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean hasAuthority(UserDetails userDetails, String authority) {
    return userDetails.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(a -> a.equals(authority));
  }

  @Test
  void kesselEnabledCallsKesselNotRbac() throws RbacApiException {
    when(unleash.isEnabled(IdentityHeaderAuthenticationDetailsService.KESSEL_FLAG))
        .thenReturn(true);
    when(kesselService.getPermissions("user123", "org123"))
        .thenReturn(List.of("subscriptions:reports:read"));

    var service = createService(kesselService, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    verify(kesselService).getPermissions("user123", "org123");
    verify(rbacService, never()).getPermissions(any());
    assertTrue(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_REPORT_READER"));
  }

  @Test
  void kesselDisabledCallsRbacNotKessel() throws RbacApiException {
    when(unleash.isEnabled(IdentityHeaderAuthenticationDetailsService.KESSEL_FLAG))
        .thenReturn(false);
    when(rbacService.getPermissions("subscriptions"))
        .thenReturn(List.of("subscriptions:reports:read"));

    var service = createService(kesselService, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    verify(rbacService).getPermissions("subscriptions");
    verify(kesselService, never()).getPermissions(anyString(), anyString());
    assertTrue(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_REPORT_READER"));
  }

  @Test
  void kesselServiceNullFallsBackToRbac() throws RbacApiException {
    when(unleash.isEnabled(IdentityHeaderAuthenticationDetailsService.KESSEL_FLAG))
        .thenReturn(true);
    when(rbacService.getPermissions("subscriptions")).thenReturn(List.of("subscriptions:*:*"));

    var service = createService(null, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    verify(rbacService).getPermissions("subscriptions");
    assertTrue(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_ADMIN"));
  }

  @Test
  void unleashNullFallsBackToRbac() throws RbacApiException {
    when(rbacService.getPermissions("subscriptions"))
        .thenReturn(List.of("subscriptions:reports:read"));

    var service = createService(kesselService, null);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    verify(rbacService).getPermissions("subscriptions");
    verify(kesselService, never()).getPermissions(anyString(), anyString());
    assertTrue(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_REPORT_READER"));
  }

  @Test
  void kesselExceptionReturnsNoRoles() {
    when(unleash.isEnabled(IdentityHeaderAuthenticationDetailsService.KESSEL_FLAG))
        .thenReturn(true);
    when(kesselService.getPermissions("user123", "org123"))
        .thenThrow(new RuntimeException("gRPC failure"));

    var service = createService(kesselService, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    assertFalse(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_ADMIN"));
    assertFalse(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_REPORT_READER"));
  }

  @Test
  void rbacExceptionReturnsNoRoles() throws RbacApiException {
    when(unleash.isEnabled(IdentityHeaderAuthenticationDetailsService.KESSEL_FLAG))
        .thenReturn(false);
    when(rbacService.getPermissions("subscriptions"))
        .thenThrow(new RbacApiException("API error", null));

    var service = createService(kesselService, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    assertFalse(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_ADMIN"));
    assertFalse(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_REPORT_READER"));
  }

  @Test
  void kesselEnabledButNoPrincipalIdReturnsNoRoles() {
    when(unleash.isEnabled(IdentityHeaderAuthenticationDetailsService.KESSEL_FLAG))
        .thenReturn(true);

    var service = createService(kesselService, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipalWithoutUserId()));

    verify(kesselService, never()).getPermissions(anyString(), anyString());
    assertFalse(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_ADMIN"));
  }

  @Test
  void devModeBypassesPermissionChecks() throws RbacApiException {
    securityProperties.setDevMode(true);

    var service = createService(kesselService, unleash);
    var result = service.loadUserDetails(authWithPrincipal(userPrincipal()));

    verify(kesselService, never()).getPermissions(anyString(), anyString());
    verify(rbacService, never()).getPermissions(any());
    assertTrue(hasAuthority(result, "ROLE_SUBSCRIPTION_WATCH_ADMIN"));
  }
}
