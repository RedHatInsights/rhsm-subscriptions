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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * If the user is not presently authenticated, the server needs to send back a response indicating
 * that they must authenticate. In normal web applications, this would be a redirect to a login page
 * or similar. But since we require external authentication, there is no entry point we can direct
 * the user to. If the x-rh-identity header is absent we send back a 401 telling the user that
 * authentication failed.
 *
 * @see org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final IdentityHeaderAuthenticationFailureHandler failureHandler;

  public RestAuthenticationEntryPoint(IdentityHeaderAuthenticationFailureHandler failureHandler) {
    this.failureHandler = failureHandler;
  }

  @Override
  public void commence(
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse,
      AuthenticationException authException)
      throws IOException, ServletException {

    failureHandler.onAuthenticationFailure(servletRequest, servletResponse, authException);
  }
}
