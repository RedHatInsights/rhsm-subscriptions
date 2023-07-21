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

import static org.candlepin.subscriptions.security.SecurityConfiguration.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.OutputStream;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.ExceptionUtil;
import org.candlepin.subscriptions.utilization.api.model.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/** AuthenticationFailureHandler that returns a JSON response. */
public class IdentityHeaderAuthenticationFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log =
      LoggerFactory.getLogger(IdentityHeaderAuthenticationFailureHandler.class);

  private final ObjectMapper mapper;

  public IdentityHeaderAuthenticationFailureHandler(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse,
      AuthenticationException authException)
      throws IOException {

    Error error = buildError(authException);
    log.error(SECURITY_STACKTRACE, "{}: {}", error.getTitle(), error.getDetail(), authException);

    Response r = ExceptionUtil.toResponse(error);
    servletResponse.setContentType(r.getMediaType().toString());
    servletResponse.setStatus(r.getStatus());

    OutputStream out = servletResponse.getOutputStream();
    mapper.writeValue(out, r.getEntity());
    out.flush();
  }

  protected Error buildError(AuthenticationException exception) {
    return new Error()
        .code(ErrorCode.REQUEST_PROCESSING_ERROR.getCode())
        .status(String.valueOf(Status.UNAUTHORIZED.getStatusCode()))
        .title("Could not authenticate the user.")
        .detail(exception.getMessage());
  }
}
