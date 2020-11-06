/*
 * Copyright (c) 2019 Red Hat, Inc.
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

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

/**
 * Spring Security filter responsible for pulling the principal out of the x-rh-identity header.
 *
 * Note that we don't register the filter as a bean anywhere, because if we did it would be registered as a
 * an extraneous ServletFilter in addition to its use in our SpringSecurity config. See
 * https://stackoverflow.com/a/31571715
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

    private RhIdentity.Identity createPrincipal(byte[] decodedHeader) throws IOException {
        RhIdentity.Identity identity = mapper.readValue(decodedHeader, RhIdentity.class).getIdentity();
        if (identity == null) {
            throw new SubscriptionsException(
                ErrorCode.REQUEST_PROCESSING_ERROR,
                Response.Status.BAD_REQUEST,
                RH_IDENTITY_HEADER + " parsed, but invalid.",
                RH_IDENTITY_HEADER + " was missing identity."
            );
        }
        return identity;
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
