/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.subscriptions.ApplicationProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Filter that prevents CSRF by verifying for a valid Origin or Referer header.
 */
@Component
@Order(1)
public class AntiCsrfFilter extends OncePerRequestFilter {

    private static final List<String> MODIFYING_METHODS = Arrays.asList("POST", "PUT", "DELETE", "PATCH");
    private static Logger log = LoggerFactory.getLogger(AntiCsrfFilter.class);

    private final boolean disabled;
    private final int port;
    private final String domainSuffix;
    private final String domainAndPortSuffix;

    AntiCsrfFilter(ApplicationProperties props, ConfigurableEnvironment env) {
        disabled = props.isDevMode() || Arrays.asList(env.getActiveProfiles()).contains("capacity-ingress");
        port = props.getAntiCsrfPort();
        domainSuffix = props.getAntiCsrfDomainSuffix();
        domainAndPortSuffix = String.join(":", domainSuffix, Integer.toString(port));
        if (disabled) {
            log.info("Origin & Referer checking (anti-csrf) disabled.");
        }
        else {
            log.info("Origin & Referer checking (anti-csrf) enabled.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        /* origin comes first as it is much faster to parse when present */
        if (disabled || requestTypeOkay(request) || originMatches(request) || refererMatches(request)) {
            filterChain.doFilter(request, response);
        }
        else {
            response.sendError(403, "Origin & Referer both bad. Cross origin requests not allowed.");
        }
    }

    private boolean requestTypeOkay(HttpServletRequest request) {
        return !MODIFYING_METHODS.contains(request.getMethod());
    }

    private boolean refererMatches(HttpServletRequest request) {
        String referer = request.getHeader("referer");
        if (referer == null) {
            return false;
        }
        URI uri = URI.create(referer);
        return uri.getHost().endsWith(domainSuffix) && (uri.getPort() == -1 || uri.getPort() == port);
    }

    private boolean originMatches(HttpServletRequest request) {
        String origin = request.getHeader("origin");
        return origin != null && (origin.endsWith(domainSuffix) || origin.endsWith(domainAndPortSuffix));
    }
}
