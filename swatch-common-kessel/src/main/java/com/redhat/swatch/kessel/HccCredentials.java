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
package com.redhat.swatch.kessel;

import java.util.function.Supplier;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.auth.OIDCDiscovery;

/** Framework-neutral, lazy workload credentials. The SDK owns synchronized caching and renewal. */
public class HccCredentials {
  private final Supplier<String> issuer;
  private final Supplier<String> clientId;
  private final Supplier<String> clientSecret;
  private OAuth2ClientCredentials credentials;

  public HccCredentials(
      Supplier<String> issuer, Supplier<String> clientId, Supplier<String> clientSecret) {
    this.issuer = issuer;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
  }

  public boolean isConfigured() {
    return nonBlank(issuer.get()) && nonBlank(clientId.get()) && nonBlank(clientSecret.get());
  }

  public String authorizationHeader() {
    return authorizationHeader(false);
  }

  public String authorizationHeader(boolean forceRefresh) {
    try {
      String token = credentials().getToken(forceRefresh).accessToken();
      if (!nonBlank(token)) {
        throw new IllegalStateException("HCC token response did not contain an access token");
      }
      return "Bearer " + token;
    } catch (OAuth2Exception e) {
      throw new IllegalStateException("Unable to obtain HCC workload authentication", e);
    }
  }

  private synchronized OAuth2ClientCredentials credentials() throws OAuth2Exception {
    if (credentials == null) {
      if (!isConfigured()) {
        throw new IllegalStateException(
            "HCC authentication requires KESSEL_AUTH_OIDC_ISSUER, KESSEL_AUTH_CLIENT_ID, and KESSEL_AUTH_CLIENT_SECRET");
      }
      var discovery = OIDCDiscovery.fetchOIDCDiscovery(issuer.get());
      credentials =
          new OAuth2ClientCredentials(
              new ClientConfigAuth(clientId.get(), clientSecret.get(), discovery.tokenEndpoint()));
    }
    return credentials;
  }

  private static boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }
}
