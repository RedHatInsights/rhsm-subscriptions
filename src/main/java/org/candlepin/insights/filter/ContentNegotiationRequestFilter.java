/*
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.insights.filter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Provider;


/**
 * A request filter that ensures that the server follows the content negotiation rules outlined
 * by jsonapi.org ( https://jsonapi.org/format/#content-negotiation-servers ).
 */
@Component
@Provider
@PreMatching
public class ContentNegotiationRequestFilter implements ContainerRequestFilter {

    private static final String APPLICATION_TYPE = "application";
    private static final String VENDOR_JSON_SUBTYPE = "vnd.api+json";
    private static final String JSON_MEDIA_TYPE =
        String.format("%s/%s", APPLICATION_TYPE, VENDOR_JSON_SUBTYPE);

    @Override
    public void filter(ContainerRequestContext requestContext) {
        validateApiJsonMediaType(requestContext);
        validateApiJsonContentType(requestContext);
    }

    private void validateApiJsonMediaType(ContainerRequestContext requestContext) {
        /*
         * IMPL NOTE:
         *
         * See https://jsonapi.org/format/#content-negotiation-servers
         *
         * Servers MUST respond with a 406 Not Acceptable status code if a request’s Accept header
         * contains the JSON:API media type and all instances of that media type are modified with
         * media type parameters.
         */
        List<MediaType> matched = findMatchedAcceptedMediaTypes(requestContext);

        // If no accepted media types match, let the default negotiation happen.
        if (matched.isEmpty()) {
            return;
        }

        boolean validFound = matched.stream().anyMatch(x -> x.getParameters().isEmpty());
        if (!validFound) {
            throw new NotAcceptableException(
                String.format("Accept header '%s' can not contain media type parameters.", JSON_MEDIA_TYPE));
        }
    }

    private void validateApiJsonContentType(ContainerRequestContext requestContext) {
        /*
         * IMPL NOTE:
         *
         * See https://jsonapi.org/format/#content-negotiation-servers
         *
         * Servers MUST respond with a 415 Unsupported Media Type status code if a request specifies
         * the header Content-Type: application/vnd.api+json with any media type parameters.
         */
        if (!requestContext.getHeaders().containsKey("content-type")) {
            // Nothing to validate if content-type was not specified.
            return;
        }

        String contentTypeHeader = requestContext.getHeaders().getFirst("content-type");
        MediaType type = contentTypeHeader != null && !contentTypeHeader.isEmpty() ?
            MediaType.valueOf(contentTypeHeader) : null;

        if (type != null && matchesApplicationJsonApiType(type) && !type.getParameters().isEmpty()) {
            throw new NotSupportedException(
                String.format("Parameters not allowed for Content-Type '%s'", JSON_MEDIA_TYPE));
        }
    }

    private List<MediaType> findMatchedAcceptedMediaTypes(ContainerRequestContext requestContext) {
        return requestContext.getAcceptableMediaTypes().stream()
            .filter(ContentNegotiationRequestFilter::matchesApplicationJsonApiType)
            .collect(Collectors.toList());
    }

    private static boolean matchesApplicationJsonApiType(MediaType type) {
        return APPLICATION_TYPE.equalsIgnoreCase(type.getType()) &&
            VENDOR_JSON_SUBTYPE.equalsIgnoreCase(type.getSubtype());
    }
}
