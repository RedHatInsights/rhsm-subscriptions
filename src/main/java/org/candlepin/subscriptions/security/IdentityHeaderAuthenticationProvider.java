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

import static org.candlepin.subscriptions.security.IdentityHeaderAuthenticationFilter.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.GrantedAuthoritiesContainer;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.util.Assert;

/**
 * This class is responsible for validating the principal. If a valid principal is found, the
 * authenticate method will build a new Authentication object that is marked as being successfully
 * authenticated ("blessed") and with the granted authorities from the Authentication.
 *
 * Heavily inspired by {@link PreAuthenticatedAuthenticationProvider}.  The major difference is that we
 * don't call out to a UserDetailsService.  The Authentication object carries a principal, credentials,
 * and details field all of type Object.  The UserDetailsService is meant to take the Authentication and
 * sensibly convert all those Object fields into typed fields (String, String, and
 * Collection<GrantedAuthorities> respectively by default).  We don't need to do that.  The principal and
 * details already in the unblessed Authentication.
 */
public class IdentityHeaderAuthenticationProvider implements AuthenticationProvider, Ordered {
    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderAuthenticationProvider.class);

    private int order = -1; // default: same as non-ordered

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

        if (!supports(authentication.getClass())) {
            return null;
        }

        log.debug("PreAuthenticated authentication request: {}", authentication);

        try {
            Assert.notNull(authentication.getPrincipal(), "No pre-authenticated principal found in request.");
            Assert.hasText(principal.getOwnerId(),
                RH_IDENTITY_HEADER + " contains no owner ID for the principal");
            Assert.hasText(principal.getAccountNumber(),
                RH_IDENTITY_HEADER + " contains no account number for the principal");
            Assert.notNull(authentication.getDetails(), RH_IDENTITY_HEADER + "contains no roles");
        }
        catch (IllegalArgumentException e) {
            throw new PreAuthenticatedCredentialsNotFoundException(RH_IDENTITY_HEADER + " is missing " +
                "required data", e);
        }

        /* NB: normally this is where we would call out to an implementation of
         * AuthenticationUserDetailsService to load a UserDetails object from the Authentication.
         * The PreAuthenticatedGrantedAuthoritiesUserDetailsService class for example.  We are not doing
         * that here.  The UserDetails interface contains a lot of methods that aren't applicable to us
         * such as isAccountNonExpired() or isCredentialsNonExpired().  Instead, we reuse the
         * InsightsUserPrincipal which does not implement UserDetails.  In the future, we may wish to
         * implement a UserDetailsService that can populate a UserDetails object with information like
         * whether the user has opted-in or not. */
        GrantedAuthoritiesContainer details = (GrantedAuthoritiesContainer) authentication.getDetails();

        PreAuthenticatedAuthenticationToken result = new PreAuthenticatedAuthenticationToken(
            principal, authentication.getCredentials(), details.getGrantedAuthorities());
        result.setAuthenticated(true);  // this is actually done in the constructor but explicit is good
        result.setDetails(authentication.getDetails());

        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PreAuthenticatedAuthenticationToken.class.isAssignableFrom(authentication);
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int i) {
        this.order = i;
    }
}
