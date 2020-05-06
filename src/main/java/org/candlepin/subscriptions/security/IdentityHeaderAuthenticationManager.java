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

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;
import org.springframework.util.StringUtils;


/**
 * This class is responsible for validating the principal. If a valid principal was
 * found, the request is considered authenticated.
 */
public class IdentityHeaderAuthenticationManager implements AuthenticationManager {

    /**
     * Validates the incoming principal that was extracted from the x-rh-identity header by
     * the {@link IdentityHeaderAuthenticationFilter}. The principal is considered authenticated
     * if the account_number and org_id are present as this would have come from 3Scale.
     *
     * @param authentication contains the pre-authenticated principal created from x-rh-identity
     * @return an approved Authentication object
     * @throws AuthenticationException if any part of the principal is invalid.
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        InsightsUserPrincipal principal = (InsightsUserPrincipal) authentication.getPrincipal();
        if (StringUtils.isEmpty(principal.getOwnerId())) {
            throw new PreAuthenticatedCredentialsNotFoundException(
                RH_IDENTITY_HEADER + " contains no owner ID for the principal"
            );
        }
        if (StringUtils.isEmpty(principal.getAccountNumber())) {
            throw new PreAuthenticatedCredentialsNotFoundException(
                RH_IDENTITY_HEADER + " contains no account number for the principal"
            );
        }

        PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails details =
            (PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails) authentication.getDetails();

        // Create a new token that has been authenticated, and has the granted authorities
        // that were determined in the details source applied. This is required in order
        // for the roles to be picked up by the security annotations.
        Authentication authToken =  new PreAuthenticatedAuthenticationToken(
            principal,
            authentication.getCredentials(),
            details.getGrantedAuthorities());
        authToken.setAuthenticated(true);
        return authToken;
    }
}
