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

import java.util.Optional;
import org.candlepin.subscriptions.clients.TokenProvider;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** OIDC client-credentials access-token source. */
@Service
public class OAuth2TokenProvider implements TokenProvider {

  private final Optional<OAuth2ClientCredentials> credentials;

  public OAuth2TokenProvider(@Autowired Optional<OAuth2ClientCredentials> credentials) {
    this.credentials = credentials;
  }

  @Override
  public String getAccessToken() {
    if (credentials.isEmpty()) {
      throw new IllegalStateException("OAuth2 client credentials are not configured");
    }
    try {
      return credentials.get().getToken().accessToken();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to obtain OAuth2 access token", e);
    }
  }
}
