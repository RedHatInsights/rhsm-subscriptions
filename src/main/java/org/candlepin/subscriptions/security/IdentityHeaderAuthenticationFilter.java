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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

/**
 * Spring Security filter responsible for pulling the principal out of the x-rh-identity header
 */
public class IdentityHeaderAuthenticationFilter extends AbstractPreAuthenticatedProcessingFilter {
    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderAuthenticationFilter.class);
    public static final String RH_IDENTITY_HEADER = "x-rh-identity";

    private final ObjectMapper mapper;

    public IdentityHeaderAuthenticationFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        String identityHeader = request.getHeader(RH_IDENTITY_HEADER);

        // If the header is missing it will be passed down the chain.
        if (StringUtils.isEmpty(identityHeader)) {
            log.debug("{} is empty", RH_IDENTITY_HEADER);
            return null;
        }

        try {
            return createPrincipal(Base64.getDecoder().decode(identityHeader));
        }
        catch (Exception e) {
            log.error(RH_IDENTITY_HEADER + " was not valid.", e);
            // Initialize an empty principal. The IdentityHeaderAuthenticationManager will validate it.
            return new InsightsUserPrincipal();
        }

    }

    private InsightsUserPrincipal createPrincipal(byte[] decodedHeader) throws Exception {
        // TODO The identity header should eventually get deserialized into an Object.
        Map authObject = mapper.readValue(decodedHeader, Map.class);
        Map identity = (Map) authObject.getOrDefault("identity", Collections.emptyMap());
        String accountNumber = (String) identity.getOrDefault("account_number", "");

        Map internal = (Map) identity.getOrDefault("internal", Collections.emptyMap());
        String orgId = (String) internal.getOrDefault("org_id", "");

        return new InsightsUserPrincipal(orgId, accountNumber);
    }


    /**
     * Credentials are not applicable in this case, so we return a dummy value.
     * @param request the servlet request
     * @return a dummy value
     */
    @Override
    protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
        return "N/A";
    }
}
