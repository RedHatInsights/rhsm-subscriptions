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
package org.candlepin.subscriptions.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/** Filter that prevents CSRF by verifying for a valid Origin or Referer header. */
public class AntiCsrfFilter extends OncePerRequestFilter {

  protected static final List<String> MODIFYING_VERBS =
      Arrays.asList("POST", "PUT", "DELETE", "PATCH");
  private static Logger log = LoggerFactory.getLogger(AntiCsrfFilter.class);

  private final boolean disabled;
  private final int port;
  private final String domainSuffix;
  private final String domainAndPortSuffix;

  public AntiCsrfFilter(SecurityProperties props) {
    disabled = props.isDevMode();
    port = props.getAntiCsrfPort();
    domainSuffix = props.getAntiCsrfDomainSuffix();
    domainAndPortSuffix = String.join(":", domainSuffix, Integer.toString(port));
    if (disabled) {
      log.info("Origin & Referer checking (anti-csrf) disabled.");
    } else {
      log.info("Origin & Referer checking (anti-csrf) enabled for {}.", domainAndPortSuffix);
    }
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    /* origin comes first as it is much faster to parse when present */
    if (disabled
        || requestVerbAllowed(request)
        || originMatches(request)
        || refererMatches(request)) {
      filterChain.doFilter(request, response);
    } else {
      response.sendError(403, "Origin & Referer both bad. Cross origin requests not allowed.");
    }
  }

  protected boolean requestVerbAllowed(HttpServletRequest request) {
    return !MODIFYING_VERBS.contains(request.getMethod());
  }

  private boolean refererMatches(HttpServletRequest request) {
    // Note that the official HTTP header is "Referer" which is misspelled.
    // See https://en.wikipedia.org/wiki/HTTP_referer
    String referrer = request.getHeader("Referer");
    if (referrer == null) {
      return false;
    }
    URI uri = URI.create(referrer);
    boolean referrerMatch =
        uri.getHost().endsWith(domainSuffix) && (uri.getPort() == -1 || uri.getPort() == port);
    log.debug("Referrer {} match: {}", referrer, referrerMatch);
    return referrerMatch;
  }

  private boolean originMatches(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    boolean originMatch =
        origin != null && (origin.endsWith(domainSuffix) || origin.endsWith(domainAndPortSuffix));
    log.debug("Origin {} match: {}", origin, originMatch);
    return originMatch;
  }
}
