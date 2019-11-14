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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * This class is in charge of deserializing the bytes from the x-rh-identity header and extracting the
 * account number from them.
 */
public class IdentityHeaderAuthenticationManager implements AuthenticationManager {

    private final ObjectMapper mapper;

    public IdentityHeaderAuthenticationManager(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * This method is a little odd.  Since the value of the x-rh-identity header is JSON, there is the
     * potential for a JSON parsing error.  However, if we parse the JSON in
     * {@link IdentityHeaderAuthenticationFilter}, when we throw an exception on a parse error, it is not
     * handled very nicely.  By handling the parsing in an AuthenticationManager we follow the conventions
     * that Spring Security is expecting, but at the expense of taking the Authentication instance, reading
     * from it, and then creating a totally new instance. Example of the decoded header:
     *
     * <pre><code>
     * {
     *   "identity": {
     *     "account_number": "0369233",
     *     "type": "User",
     *     "user" : {
     *       "username": "jdoe",
     *       "email": "jdoe@acme.com",
     *       "first_name": "John",
     *       "last_name": "Doe",
     *       "is_active": true,
     *       "is_org_admin": false,
     *       "is_internal": false,
     *       "locale": "en_US"
     *     },
     *     "internal" : {
     *       "org_id": "3340851",
     *       "auth_type": "basic-auth",
     *       "auth_time": 6300
     *      }
     *   }
     * }
     * </code></pre>
     *
     * @param authentication a byte[] of base64-decoded data from x-rh-identity
     * @return an approved Authentication object
     * @throws AuthenticationException if the JSON can not be parsed
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        PreAuthenticatedAuthenticationToken token = (PreAuthenticatedAuthenticationToken) authentication;
        byte[] decodedHeader = (byte[]) token.getPrincipal();
        PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails details =
            (PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails) authentication.getDetails();
        try {
            Map authObject = mapper.readValue(decodedHeader, Map.class);
            Map identity = (Map) authObject.getOrDefault("identity", Collections.emptyMap());
            String accountNumber = (String) identity.get("account_number");
            Map internal = (Map) identity.getOrDefault("internal", Collections.emptyMap());
            String orgId = (String) internal.get("org_id");

            if (StringUtils.isEmpty(orgId)) {
                throw new PreAuthenticatedCredentialsNotFoundException(RH_IDENTITY_HEADER +
                    " contains no owner ID for the principal");
            }
            if (StringUtils.isEmpty(accountNumber)) {
                throw new PreAuthenticatedCredentialsNotFoundException(RH_IDENTITY_HEADER +
                    " contains no principal");
            }

            token = new PreAuthenticatedAuthenticationToken(new InsightsUserPrincipal(orgId, accountNumber),
                token.getCredentials(), details.getGrantedAuthorities());
            token.setAuthenticated(true);
            return token;

        }
        catch (IOException e) {
            throw new PreAuthenticatedCredentialsNotFoundException(RH_IDENTITY_HEADER + " is not valid JSON");
        }
    }
}
