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

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

/**
 * Spring Security filter responsible for pulling the principal out of the x-rh-identity header
 */
public class IdentityHeaderAuthenticationFilter extends AbstractPreAuthenticatedProcessingFilter {
    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderAuthenticationFilter.class);
    public static final String RH_IDENTITY_HEADER = "x-rh-identity";
    private ObjectMapper mapper;

    public IdentityHeaderAuthenticationFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
        try {
            String identityHeader = request.getHeader(RH_IDENTITY_HEADER);

            if (StringUtils.isEmpty(identityHeader)) {
                log.debug("{} is empty", RH_IDENTITY_HEADER);
                // Give up and pass responsibility on to the next filter
                return null;
            }

            byte[] decodedHeader = Base64.getDecoder().decode(identityHeader);
            Map authObject = mapper.readValue(decodedHeader, Map.class);
            Map identity = (Map) authObject.getOrDefault("identity", Collections.emptyMap());
            String accountNumber = (String) identity.get("account_number");

            if (StringUtils.isEmpty(accountNumber)) {
                throw new PreAuthenticatedCredentialsNotFoundException(RH_IDENTITY_HEADER +
                    " contains no principal");
            }

            return accountNumber;
        }
        catch (IOException | PreAuthenticatedCredentialsNotFoundException e) {
            throw new SubscriptionsException(ErrorCode.VALIDATION_FAILED_ERROR, Response.Status.BAD_REQUEST,
                "error processing identity header", e);
        }
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
