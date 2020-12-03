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
package org.candlepin.subscriptions.resteasy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Path;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

/**
 * Hack to get URI populated by micrometer.
 *
 * This uses internal knowledge of how MVC requests are providing URI to micrometer, found by reading the
 * source.
 */
@Component
@Provider
public class MicrometerUriHackFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MicrometerUriHackFilter.class);

    @Context
    HttpServletRequest request;

    @Context
    ResourceInfo resourceInfo;

    @Context
    UriInfo uriInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = uriInfo.getAbsolutePath().getPath();
        try {
            String applicationPath = uriInfo.getBaseUri().getPath();
            String classPath = resourceInfo.getResourceClass().getAnnotation(Path.class).value();
            String methodPath = resourceInfo.getResourceMethod().getAnnotation(Path.class).value();
            path = Paths.get(applicationPath, classPath, methodPath).toString();
        }
        catch (Exception e) {
            log.debug("Unable to determine templated resource path, falling back to absolute path", e);
        }
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, path);
    }
}
