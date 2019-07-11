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
package org.candlepin.subscriptions.resteasy;

import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resource.OpenApiJsonResource;
import org.candlepin.subscriptions.resource.OpenApiYamlResource;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Parses x-rh-identity header and creates a {@link javax.ws.rs.core.SecurityContext} accordingly.
 */
@Component
@Priority(Priorities.AUTHENTICATION)
@Provider
public class IdentityHeaderAuthFilter implements ContainerRequestFilter {

    private final ObjectMapper mapper;

    private static final List<Class<?>> NOAUTH_RESOURCE_CLASSES = Arrays.asList(
        OpenApiJsonResource.class,
        OpenApiYamlResource.class
    );

    public IdentityHeaderAuthFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (requestContext.getUriInfo().getMatchedResources()
            .stream()
            .map(Object::getClass)
            .anyMatch(NOAUTH_RESOURCE_CLASSES::contains)) {

            return;
        }
        String identityHeader = requestContext.getHeaderString("x-rh-identity");
        if (identityHeader == null) {
            throw new SubscriptionsException(ErrorCode.VALIDATION_FAILED_ERROR, Response.Status.UNAUTHORIZED,
                "Unauthorized", "No identity header supplied");
        }

        requestContext.setSecurityContext(new IdentityHeaderSecurityContext(mapper, identityHeader));
    }
}
