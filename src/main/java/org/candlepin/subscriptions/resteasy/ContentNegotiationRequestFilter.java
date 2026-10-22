/*
 * Copyright Red Hat, Inc.
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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

/**
 * A request filter that ensures that the server follows the content negotiation rules outlined by
 * jsonapi.org ( https://jsonapi.org/format/#content-negotiation-servers ).
 */
@Component
public class ContentNegotiationRequestFilter extends OncePerRequestFilter {

  private static final String APPLICATION_TYPE = "application";
  private static final String VENDOR_JSON_SUBTYPE = "vnd.api+json";
  private static final String JSON_MEDIA_TYPE =
      String.format("%s/%s", APPLICATION_TYPE, VENDOR_JSON_SUBTYPE);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    validateApiJsonMediaType(request);
    validateApiJsonContentType(request);
    filterChain.doFilter(request, response);
  }

  private void validateApiJsonMediaType(HttpServletRequest request) {
    /*
     * IMPL NOTE:
     *
     * See https://jsonapi.org/format/#content-negotiation-servers
     *
     * Servers MUST respond with a 406 Not Acceptable status code if a request’s Accept header
     * contains the JSON:API media type and all instances of that media type are modified with
     * media type parameters.
     */
    List<MediaType> matched = findMatchedAcceptedMediaTypes(request);

    // If no accepted media types match, let the default negotiation happen.
    if (matched.isEmpty()) {
      return;
    }

    boolean validFound = matched.stream().anyMatch(x -> x.getParameters().isEmpty());
    if (!validFound) {
      throw new ResponseStatusException(
          HttpStatus.NOT_ACCEPTABLE,
          String.format(
              "Accept header ‘%s’ can not contain media type parameters.", JSON_MEDIA_TYPE));
    }
  }

  private void validateApiJsonContentType(HttpServletRequest request) {
    /*
     * IMPL NOTE:
     *
     * See https://jsonapi.org/format/#content-negotiation-servers
     *
     * Servers MUST respond with a 415 Unsupported Media Type status code if a request specifies
     * the header Content-Type: application/vnd.api+json with any media type parameters.
     */
    String contentTypeHeader = request.getHeader("Content-Type");
    if (contentTypeHeader == null || contentTypeHeader.isEmpty()) {
      // Nothing to validate if content-type was not specified.
      return;
    }

    MediaType type = MediaType.parseMediaType(contentTypeHeader);

    if (matchesApplicationJsonApiType(type) && !type.getParameters().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          String.format("Parameters not allowed for Content-Type ‘%s’", JSON_MEDIA_TYPE));
    }
  }

  private List<MediaType> findMatchedAcceptedMediaTypes(HttpServletRequest request) {
    String acceptHeader = request.getHeader("Accept");
    if (acceptHeader == null || acceptHeader.isEmpty()) {
      return Collections.emptyList();
    }

    try {
      return MediaType.parseMediaTypes(acceptHeader).stream()
          .filter(ContentNegotiationRequestFilter::matchesApplicationJsonApiType)
          .collect(Collectors.toList());
    } catch (Exception e) {
      // If Accept header is malformed, let default negotiation handle it
      return Collections.emptyList();
    }
  }

  private static boolean matchesApplicationJsonApiType(MediaType type) {
    return APPLICATION_TYPE.equalsIgnoreCase(type.getType())
        && VENDOR_JSON_SUBTYPE.equalsIgnoreCase(type.getSubtype());
  }
}
