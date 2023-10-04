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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.candlepin.subscriptions.rbac.RbacApi;
import org.candlepin.subscriptions.rbac.RbacApiException;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.candlepin.subscriptions.rbac.RbacService;
import org.candlepin.subscriptions.rbac.model.Access;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdentityHeaderAuthenticationDetailsServiceTest {

  @MockBean private RbacApi rbacApi;

  @Autowired private RbacService rbacService;

  @Autowired
  @Qualifier("identityHeaderAuthenticationDetailsService")
  IdentityHeaderAuthenticationDetailsService detailsService;

  @Test
  void testAdminRoleGranted() throws Exception {
    when(rbacApi.getCurrentUserAccess("subscriptions"))
        .thenReturn(Arrays.asList(new Access().permission("subscriptions:*:*")));
    assertThat(extractRoles(false), Matchers.contains(RoleProvider.SWATCH_ADMIN_ROLE));
  }

  @Test
  void testReportReaderRoleGranted() throws RbacApiException {
    when(rbacApi.getCurrentUserAccess("subscriptions"))
        .thenReturn(List.of(new Access().permission("subscriptions:reports:read")));
    assertThat(extractRoles(false), Matchers.contains(RoleProvider.SWATCH_REPORT_READER));
  }

  @Test
  void testRhAssociateGetsRhInternalRole() {
    Authentication auth = new PreAuthenticatedAuthenticationToken(new RhAssociatePrincipal(), null);
    UserDetails userDetails = detailsService.loadUserDetails(auth);
    assertEquals(
        Collections.singleton(new SimpleGrantedAuthority("ROLE_INTERNAL")),
        userDetails.getAuthorities());
    verifyNoInteractions(rbacApi);
  }

  @Test
  void testX509PrincipalGetsRhInternalRole() {
    Authentication auth = new PreAuthenticatedAuthenticationToken(new X509Principal(), null);
    UserDetails userDetails = detailsService.loadUserDetails(auth);
    assertEquals(
        Collections.singleton(new SimpleGrantedAuthority("ROLE_INTERNAL")),
        userDetails.getAuthorities());
    verifyNoInteractions(rbacApi);
  }

  @Test
  void testDevModeGrantsAllRoles() {
    assertThat(
        extractRoles(true),
        Matchers.containsInAnyOrder(RoleProvider.SWATCH_ADMIN_ROLE, RoleProvider.ROLE_INTERNAL));
  }

  private Collection<String> extractRoles(boolean devMode) {
    SecurityProperties props = new SecurityProperties();
    RbacProperties rbacProps = new RbacProperties();
    props.setDevMode(devMode);
    IdentityHeaderAuthenticationDetailsService source =
        new IdentityHeaderAuthenticationDetailsService(
            props, rbacProps, new IdentityHeaderAuthoritiesMapper(), rbacService);
    return source.getUserRoles();
  }
}
