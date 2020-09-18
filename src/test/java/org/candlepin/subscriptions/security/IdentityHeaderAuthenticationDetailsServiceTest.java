/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.insights.rbac.client.RbacApi;
import org.candlepin.insights.rbac.client.RbacService;
import org.candlepin.insights.rbac.client.model.Access;
import org.candlepin.subscriptions.ApplicationProperties;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
class IdentityHeaderAuthenticationDetailsServiceTest {

    @MockBean
    private RbacApi rbacApi;

    @Autowired
    private RbacService rbacService;

    @Autowired
    IdentityHeaderAuthenticationDetailsService detailsService;

    @Test
    void testAdminRoleGranted() throws Exception {
        when(rbacApi.getCurrentUserAccess(eq("subscriptions"))).thenReturn(
            Arrays.asList(new Access().permission("subscriptions:*:*"))
        );
        assertRoles(false, RoleProvider.SWATCH_ADMIN_ROLE);
    }

    @Test
    void testRhAssociateGetsRhInternalRole() {
        Authentication auth = new PreAuthenticatedAuthenticationToken(new RhAssociatePrincipal(), null);
        UserDetails userDetails = detailsService.loadUserDetails(auth);
        assertEquals(Collections.singleton(new SimpleGrantedAuthority("ROLE_RH_INTERNAL")),
            userDetails.getAuthorities());
        verifyZeroInteractions(rbacApi);
    }

    @Test
    void testX509PrincipalGetsRhInternalRole() {
        Authentication auth = new PreAuthenticatedAuthenticationToken(new X509Principal(), null);
        UserDetails userDetails = detailsService.loadUserDetails(auth);
        assertEquals(Collections.singleton(new SimpleGrantedAuthority("ROLE_RH_INTERNAL")),
            userDetails.getAuthorities());
        verifyZeroInteractions(rbacApi);
    }

    @Test
    void testDevModeGrantsAllRoles() {
        assertRoles(true, RoleProvider.SWATCH_ADMIN_ROLE);
    }

    private void assertRoles(boolean devMode, String ... expectedRoles) {
        ApplicationProperties props = new ApplicationProperties();
        props.setDevMode(devMode);
        IdentityHeaderAuthenticationDetailsService source = new IdentityHeaderAuthenticationDetailsService(
            props, new IdentityHeaderAuthoritiesMapper(), rbacService
        );
        Collection<String> roles = source.getUserRoles();
        assertEquals(expectedRoles.length, roles.size());
        assertThat(roles, Matchers.containsInAnyOrder(expectedRoles));
    }
}
