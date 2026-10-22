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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Stores the resource method's URI pattern as a request attribute for observability.
 *
 * @see ResteasyRequestObservationConvention
 */
@Component
public class ResteasyUriExtractionFilter implements HandlerInterceptor {
  public static final String JAXRS_URI = "JAXRS_URI";
  private static final Logger log = LoggerFactory.getLogger(ResteasyUriExtractionFilter.class);

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    try {
      // Spring MVC provides the best matching pattern
      String pattern =
          (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

      // Fallback: try to extract from handler method annotations
      if (pattern == null && handler instanceof HandlerMethod handlerMethod) {
        RequestMapping classMapping =
            handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
        RequestMapping methodMapping = handlerMethod.getMethodAnnotation(RequestMapping.class);

        if (classMapping != null && methodMapping != null) {
          String classPath = classMapping.value().length > 0 ? classMapping.value()[0] : "";
          String methodPath = methodMapping.value().length > 0 ? methodMapping.value()[0] : "";
          pattern = classPath + methodPath;
        }
      }

      // Final fallback: use request URI
      if (pattern == null) {
        pattern = request.getRequestURI();
      }

      request.setAttribute(JAXRS_URI, pattern);
    } catch (Exception e) {
      log.debug("Unable to determine templated resource path, using request URI", e);
      request.setAttribute(JAXRS_URI, request.getRequestURI());
    }
    return true;
  }
}
