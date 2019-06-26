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
package org.candlepin.subscriptions.resource;

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.subscriptions.exception.SubscriptionsException;

import org.junit.jupiter.api.Test;

import java.security.Principal;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

public class TallyResourceTest {

    @Test
    public void testAccountNumberMustMatchHeader() {
        TallyResource resource = new TallyResource();
        resource.securityContext = new SecurityContext() {

            @Override
            public Principal getUserPrincipal() {
                return () -> "123456";
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public boolean isSecure() {
                return false;
            }

            @Override
            public String getAuthenticationScheme() {
                return null;
            }
        };
        SubscriptionsException e = assertThrows(SubscriptionsException.class, () -> resource.getTallyReport(
            "42",
            "product1",
            "daily",
            null,
            null
        ));
        assertEquals(Response.Status.FORBIDDEN, e.getStatus());
    }
}
