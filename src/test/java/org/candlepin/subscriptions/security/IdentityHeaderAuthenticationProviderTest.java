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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdentityHeaderAuthenticationProviderTest {

  @MockBean PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails details;

  @MockBean
  @Qualifier("identityHeaderAuthenticationDetailsService")
  IdentityHeaderAuthenticationDetailsService detailsService;

  @Autowired
  @Qualifier("identityHeaderAuthenticationProvider")
  AuthenticationProvider manager;

  IdentityHeaderAuthoritiesMapper authoritiesMapper = new IdentityHeaderAuthoritiesMapper();

  @Test
  void testMissingOrgId() {
    Authentication auth = token(null, "account");
    AuthenticationException e =
        assertThrows(AuthenticationException.class, () -> manager.authenticate(auth));
    assertEquals("x-rh-identity contains no owner ID for the principal", e.getCause().getMessage());
  }

  @Test
  void testMissingAccountNumber() {
    Authentication auth = token("123", null);
    AuthenticationException e =
        assertThrows(AuthenticationException.class, () -> manager.authenticate(auth));
    assertEquals(
        "x-rh-identity contains no account number for the principal", e.getCause().getMessage());
  }

  @Test
  void validPrincipalIsAuthenticated() {
    when(detailsService.loadUserDetails(any()))
        .thenReturn(new User("N/A", "N/A", Collections.emptyList()));
    Authentication result = manager.authenticate(token("123", "acct"));
    assertTrue(result.isAuthenticated());
  }

  @Test
  void validPskPrincipalIsAuthenticated() {
    AuthProperties authProperties = new AuthProperties();
    PskClientPrincipal principal = new PskClientPrincipal();
    principal.setPreSharedKey("c9a98753-2092-4617-b226-5c2653330b3d");
    authProperties.setSwatchPsks(Map.of("source", "c9a98753-2092-4617-b226-5c2653330b3d"));
    IdentityHeaderAuthenticationProvider provider =
        new IdentityHeaderAuthenticationProvider(detailsService, authoritiesMapper, authProperties);
    Authentication authentication = new PreAuthenticatedAuthenticationToken(principal, null, null);
    var result = provider.authenticate(authentication);
    assertTrue(result.isAuthenticated());
    assertEquals("source", result.getPrincipal());
  }

  @Test
  void invalidPskPrincipalThrowsException() {
    AuthProperties authProperties = new AuthProperties();
    String pskEncoded = "{\"source\":\"c9a98753-2092-4617-b226-5c2653330b3d\"}";
    PskClientPrincipal principal = new PskClientPrincipal();
    principal.setPreSharedKey("e88bc49f-e395-48c0-8665-c87b251c30cf");
    authProperties.setSwatchPsks(Map.of("source", "c9a98753-2092-4617-b226-5c2653330b3d"));
    IdentityHeaderAuthenticationProvider provider =
        new IdentityHeaderAuthenticationProvider(detailsService, authoritiesMapper, authProperties);
    Authentication authentication = new PreAuthenticatedAuthenticationToken(principal, null, null);

    assertThrows(
        PreAuthenticatedCredentialsNotFoundException.class,
        () -> {
          provider.authenticate(authentication);
        });
  }

  @Test
  void emptyPskPrincipalThrowsException() {
    AuthProperties authProperties = new AuthProperties();
    String pskEncoded = "{\"source\":\"c9a98753-2092-4617-b226-5c2653330b3d\"}";
    PskClientPrincipal principal = new PskClientPrincipal();
    authProperties.setSwatchPsks(Map.of("source", "c9a98753-2092-4617-b226-5c2653330b3d"));
    IdentityHeaderAuthenticationProvider provider =
        new IdentityHeaderAuthenticationProvider(detailsService, authoritiesMapper, authProperties);
    Authentication authentication = new PreAuthenticatedAuthenticationToken(principal, null, null);

    assertThrows(
        PreAuthenticatedCredentialsNotFoundException.class,
        () -> {
          provider.authenticate(authentication);
        });
  }

  private PreAuthenticatedAuthenticationToken token(String org, String account) {
    PreAuthenticatedAuthenticationToken token =
        new PreAuthenticatedAuthenticationToken(new InsightsUserPrincipal(org, account), "N/A");
    token.setDetails(details);
    return token;
  }
}
