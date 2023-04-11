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
import java.io.IOException;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.util.StringUtils;

/**
 * Spring Security filter responsible for pulling the principal out of the x-rh-identity header.
 *
 * <p>Note that we don't register the filter as a bean anywhere, because if we did it would be
 * registered as a an extraneous ServletFilter in addition to its use in our SpringSecurity config.
 * See https://stackoverflow.com/a/31571715
 */
public class IdentityHeaderAuthenticationFilter extends AbstractPreAuthenticatedProcessingFilter {
  private static final Logger log =
      LoggerFactory.getLogger(IdentityHeaderAuthenticationFilter.class);
  public static final String RH_IDENTITY_HEADER = "x-rh-identity";
  public static final String RH_PSK_HEADER = "x-rh-swatch-psk";

  private final ObjectMapper mapper;

  public IdentityHeaderAuthenticationFilter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
    String identityHeader = request.getHeader(RH_IDENTITY_HEADER);
    String pskHeader = request.getHeader(RH_PSK_HEADER);

    // Check PSK header first
    if (StringUtils.hasText(pskHeader)) {
      return new PskClientPrincipal(pskHeader);
      // If missing check for Identity header
    } else if (StringUtils.hasText(identityHeader)) {
      try {
        return createPrincipal(Base64.getDecoder().decode(identityHeader));
      } catch (Exception e) {
        log.error(SECURITY_STACKTRACE, RH_IDENTITY_HEADER + " was not valid.", e);
        // Initialize an empty principal. The IdentityHeaderAuthenticationProvider will validate it.
        return new InsightsUserPrincipal();
      }
    }
    // If both headers are missing it will be passed down the chain.
    else {
      log.debug("{} and {} are empty", RH_IDENTITY_HEADER, RH_PSK_HEADER);
      return null;
    }
  }

  private RhIdentity.Identity createPrincipal(byte[] decodedHeader) throws IOException {
    RhIdentity.Identity identity = mapper.readValue(decodedHeader, RhIdentity.class).getIdentity();
    if (identity == null) {
      throw new SubscriptionsException(
          ErrorCode.REQUEST_PROCESSING_ERROR,
          Response.Status.BAD_REQUEST,
          RH_IDENTITY_HEADER + " parsed, but invalid.",
          RH_IDENTITY_HEADER + " was missing identity.");
    }
    return identity;
  }

  /**
   * Credentials are not applicable in this case, so we return a placeholder value.
   *
   * @param request the servlet request
   * @return a placeholder value
   */
  @Override
  protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
    return "N/A";
  }
}
