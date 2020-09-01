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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
class IdentityHeaderAuthenticationProviderTest {

    @MockBean
    PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails details;

    @MockBean
    IdentityHeaderAuthenticationDetailsService detailsService;

    @Autowired
    AuthenticationProvider manager;

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
        assertEquals("x-rh-identity contains no account number for the principal", e.getCause().getMessage());
    }

    @Test
    void validPrincipalIsAuthenticated() {
        when(detailsService.loadUserDetails(any())).thenReturn(new User("N/A", "N/A",
            Collections.emptyList()));
        Authentication result = manager.authenticate(token("123", "acct"));
        assertTrue(result.isAuthenticated());
    }

    private PreAuthenticatedAuthenticationToken token(String org, String account) {
        PreAuthenticatedAuthenticationToken token =
            new PreAuthenticatedAuthenticationToken(new InsightsUserPrincipal(org, account), "N/A");
        token.setDetails(details);
        return token;
    }
}
