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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.Attributes2GrantedAuthoritiesMapper;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Class in charge of populating the security context with the users roles based on the values in the
 * x-rh-identity header.
 */
public class IdentityHeaderAuthenticationDetailsSource implements
    AuthenticationDetailsSource<HttpServletRequest,
    PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails> {

    private static final Logger log =
        LoggerFactory.getLogger(IdentityHeaderAuthenticationDetailsSource.class);

    // NB: Right now there are just two roles.  If we expand on that, we should externalize all the
    //  roles to an enum, serialize the header JSON to an object, and write an actual predicate method
    //  isUserInRole
    private static final String ORG_ADMIN = "ORG_ADMIN";
    private static final String INTERNAL = "INTERNAL";

    private ObjectMapper objectMapper;
    private Attributes2GrantedAuthoritiesMapper authMapper;

    public IdentityHeaderAuthenticationDetailsSource(ObjectMapper objectMapper,
        Attributes2GrantedAuthoritiesMapper authMapper) {
        this.objectMapper = objectMapper;
        this.authMapper = authMapper;
    }

    protected Collection<String> getUserRoles(HttpServletRequest context) {
        ArrayList<String> userRolesList = new ArrayList<>();
        try {
            String identityHeader = context.getHeader(RH_IDENTITY_HEADER);

            if (StringUtils.isEmpty(identityHeader)) {
                return userRolesList;
            }

            byte[] decodedHeader = Base64.getDecoder().decode(identityHeader);
            Map authObject = objectMapper.readValue(decodedHeader, Map.class);
            Map identity = (Map) authObject.getOrDefault("identity", Collections.emptyMap());
            Map user = (Map) identity.getOrDefault("user", Collections.emptyMap());

            if (((String) user.getOrDefault("is_org_admin", "false")).equalsIgnoreCase("true")) {
                userRolesList.add(ORG_ADMIN);
            }

            if (((String) user.getOrDefault("is_internal", "false")).equalsIgnoreCase("true")) {
                userRolesList.add(INTERNAL);
            }
        }
        catch (IOException e) {
            throw new PreAuthenticatedCredentialsNotFoundException(RH_IDENTITY_HEADER + " is not valid JSON");
        }

        return userRolesList;
    }

    @Override
    public PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails buildDetails(
        HttpServletRequest context) {
        Collection<String> userRoles = getUserRoles(context);
        Collection<? extends GrantedAuthority> userGAs = authMapper.getGrantedAuthorities(userRoles);

        log.debug("Roles {} mapped to Granted Authorities: {}", userRoles, userGAs);

        return new PreAuthenticatedGrantedAuthoritiesWebAuthenticationDetails(context, userGAs);
    }

    public void setUserRoles2GrantedAuthoritiesMapper(
        Attributes2GrantedAuthoritiesMapper authMapper) {
        this.authMapper = authMapper;
    }

}
