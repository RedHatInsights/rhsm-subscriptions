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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.subscriptions.ApplicationProperties;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import javax.servlet.http.HttpServletRequest;


public class IdentityHeaderAuthenticationDetailsSourceTest {

    // {"identity":{"account_number":"TEST_ACCOUNT", "user":{"is_org_admin":true}}}
    private final String ADMIN_ONLY =
        "eyJpZGVudGl0eSI6eyJhY2NvdW50X251bWJlciI6IlRFU1RfQUNDT1VOVCIsICJ1c2VyIjp7Imlz" +
        "X29yZ19hZG1pbiI6dHJ1ZX19fQo=";

    // {"identity":{"account_number":"TEST_ACCOUNT", "user":{"is_internal":true}}}
    private static final String INTERNAL_ONLY =
        "eyJpZGVudGl0eSI6eyJhY2NvdW50X251bWJlciI6IlRFU1RfQUNDT1VOVCIsICJ1c2VyIjp7Imlz" +
        "X2ludGVybmFsIjp0cnVlfX19Cg==";

    // {"identity":{"account_number":"TEST_ACCOUNT"}}
    public static final String ACCOUNT_ONLY =
        "eyJpZGVudGl0eSI6eyJhY2NvdW50X251bWJlciI6IlRFU1RfQUNDT1VOVCJ9fQo=";

    @Test
    public void testAdminRoleGranted() {
        assertRoles(ADMIN_ONLY, false, IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE);
    }

    @Test
    public void testInternalRoleGranted() {
        assertRoles(INTERNAL_ONLY, false, IdentityHeaderAuthenticationDetailsSource.INTERNAL_ROLE);
    }

    @Test
    public void testDevModeGrantsAllRoles() {
        assertRoles(ADMIN_ONLY, true, IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE,
            IdentityHeaderAuthenticationDetailsSource.INTERNAL_ROLE);
        assertRoles(INTERNAL_ONLY, true, IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE,
            IdentityHeaderAuthenticationDetailsSource.INTERNAL_ROLE);
        assertRoles(ACCOUNT_ONLY, true, IdentityHeaderAuthenticationDetailsSource.ORG_ADMIN_ROLE,
            IdentityHeaderAuthenticationDetailsSource.INTERNAL_ROLE);
    }

    private void assertRoles(String identity, boolean devMode, String ... expectedRoles) {
        HttpServletRequest context = mock(HttpServletRequest.class);
        when(context.getHeader(IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER)).thenReturn(identity);

        ApplicationProperties props = new ApplicationProperties();
        props.setDevMode(devMode);
        IdentityHeaderAuthenticationDetailsSource source = new IdentityHeaderAuthenticationDetailsSource(
            props, new ObjectMapper(), new IdentityHeaderAuthoritiesMapper()
        );
        Collection<String> roles = source.getUserRoles(context);
        assertEquals(expectedRoles.length, roles.size());
        assertThat(roles, Matchers.containsInAnyOrder(expectedRoles));
    }
}
