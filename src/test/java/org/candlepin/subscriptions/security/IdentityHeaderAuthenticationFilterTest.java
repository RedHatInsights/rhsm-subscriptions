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

import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.RH_IDENTITY_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import javax.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class IdentityHeaderAuthenticationFilterTest {

    private ObjectMapper mapper = new ObjectMapper();

    @Mock
    private HttpServletRequest request;

    @Test
    public void testEmptyHeaderReturnsNullPrincipal() {
        when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn(null);
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        assertNull(filter.getPreAuthenticatedPrincipal(request));
    }

    @Test
    public void defaultEmptyPrincipalReturnedWhenExceptionOccursWhileProcessingHeader() {
        when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn("arandomheadervalue");
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        assertPrincipal(filter.getPreAuthenticatedPrincipal(request), null, null);
    }

    @Test
    public void missingIdentityResultsInEmptyOrgAndAccount() {
        // {}
        String emptyJson = Base64.getEncoder().encodeToString("{}".getBytes());
        when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn(emptyJson);
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        assertPrincipal(filter.getPreAuthenticatedPrincipal(request), "", "");
    }

    @Test
    public void testMissingInternalProperty() {
        String missingInternal = Base64.getEncoder().encodeToString(
            "{\"identity\":{\"account_number\":\"myaccount\"}}".getBytes());
        when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn(missingInternal);
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        assertPrincipal(filter.getPreAuthenticatedPrincipal(request), "myaccount", "");
    }

    @Test
    public void missingOrgInHeaderResultsInEmptyValueInPrincipal() {
        String missingOrgId = Base64.getEncoder().encodeToString(
            "{\"identity\":{\"account_number\":\"myaccount\", \"internal\":{\"org_id\":\"\"}}}".getBytes());
        when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn(missingOrgId);
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        assertPrincipal(filter.getPreAuthenticatedPrincipal(request), "myaccount", "");
    }

    @Test
    public void missingAccountInHeaderResultsInEmptyValueInPrincipal() {
        //
        String missingAccount = Base64.getEncoder().encodeToString(
            "{\"identity\":{\"internal\":{\"org_id\":\"myorg\"}}}".getBytes());
        when(request.getHeader(RH_IDENTITY_HEADER)).thenReturn(missingAccount);
        IdentityHeaderAuthenticationFilter filter = new IdentityHeaderAuthenticationFilter(mapper);
        assertPrincipal(filter.getPreAuthenticatedPrincipal(request), "", "myorg");
    }

    private void assertPrincipal(Object preAuthPrincipal, String expAccountNumber, String expOrgId) {
        assertTrue(preAuthPrincipal instanceof InsightsUserPrincipal);

        InsightsUserPrincipal principal = (InsightsUserPrincipal) preAuthPrincipal;
        assertEquals(expOrgId, principal.getOwnerId());
        assertEquals(expAccountNumber, principal.getAccountNumber());
    }

}
