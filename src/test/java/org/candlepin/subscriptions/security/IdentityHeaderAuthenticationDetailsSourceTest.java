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
import static org.mockito.Mockito.when;

import org.candlepin.insights.rbac.client.RbacApi;
import org.candlepin.insights.rbac.client.RbacServiceProperties;
import org.candlepin.insights.rbac.client.model.Access;
import org.candlepin.subscriptions.ApplicationProperties;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;

@ExtendWith(MockitoExtension.class)
public class IdentityHeaderAuthenticationDetailsSourceTest {

    @Mock
    private RbacApi rbacApi;

    @Test
    public void testAdminRoleGranted() throws Exception {
        when(rbacApi.getCurrentUserAccess(eq("subscriptions"))).thenReturn(
            Arrays.asList(new Access().permission("subscriptions:*:*"))
        );
        assertRoles(false,
            RoleProvider.ORG_ADMIN_ROLE,
            RoleProvider.REPORTING_ROLE);
    }

    @Test
    public void testDevModeGrantsAllRoles() {
        assertRoles(true,
            RoleProvider.ORG_ADMIN_ROLE,
            RoleProvider.REPORTING_ROLE);
        assertRoles(true,
            RoleProvider.ORG_ADMIN_ROLE,
            RoleProvider.REPORTING_ROLE);
        assertRoles(true,
            RoleProvider.ORG_ADMIN_ROLE,
            RoleProvider.REPORTING_ROLE);
    }

    private void assertRoles(boolean devMode, String ... expectedRoles) {
        ApplicationProperties props = new ApplicationProperties();
        props.setDevMode(devMode);
        IdentityHeaderAuthenticationDetailsSource source = new IdentityHeaderAuthenticationDetailsSource(
            props, new IdentityHeaderAuthoritiesMapper(), rbacApi,
            new RbacServiceProperties().getApplicationName()
        );
        Collection<String> roles = source.getUserRoles();
        assertEquals(expectedRoles.length, roles.size());
        assertThat(roles, Matchers.containsInAnyOrder(expectedRoles));
    }
}
