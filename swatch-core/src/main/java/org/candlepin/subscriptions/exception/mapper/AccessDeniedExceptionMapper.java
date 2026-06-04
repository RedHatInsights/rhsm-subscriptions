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
package org.candlepin.subscriptions.exception.mapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ext.Provider;
import org.candlepin.subscriptions.security.RestAccessDeniedHandler;
import org.candlepin.subscriptions.utilization.api.v1.model.Error;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * This handler catches AccessDeniedExceptions that are thrown when access is denied by the
 * annotation-based security system. When AccessDeniedExceptions are thrown due to violations of the
 * declarative security constraints defined in the JavaConfig (e.g. using <code>hasRole()</code>),
 * the exception is handled by the AccessDeniedHandler defined on the ExceptionTranslationFilter.
 * Annotation based restrictions use a different pathway. Theoretically, the exception could be
 * handled by adding an ExceptionHandler annotation to the {@link
 * org.springframework.security.web.access.AccessDeniedHandler#handle(HttpServletRequest,
 * HttpServletResponse, AccessDeniedException)} method, but since we aren't doing that anywhere else
 * that would add an additional exception handling path for just one circumstance. Instead, the
 * easiest thing to do is to let the AccessDeniedException get thrown, leave Spring, and get handled
 * by JAX-RS like the rest of our exceptions. It is important to remember, however, that
 * AccessDeniedExceptions can still occur during the regular authentication/authorization process
 * and those will still be handled by our implementation of the AccessDeniedHandler interface,
 * {@link org.candlepin.subscriptions.security.RestAccessDeniedHandler}
 *
 * @see <a href="https://stackoverflow.com/a/43452573">https://stackoverflow.com/a/43452573</a>
 */
@Component
@Provider
public class AccessDeniedExceptionMapper extends BaseExceptionMapper<AccessDeniedException> {
  @Override
  protected Error buildError(AccessDeniedException exception) {
    return RestAccessDeniedHandler.buildError(exception);
  }
}
