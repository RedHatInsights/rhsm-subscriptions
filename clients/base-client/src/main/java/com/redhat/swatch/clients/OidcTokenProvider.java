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
package com.redhat.swatch.clients;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;

@Slf4j
public final class OidcTokenProvider implements TokenProvider {

  @FunctionalInterface
  public interface AccessTokenSource {
    String getAccessToken() throws Exception;
  }

  @FunctionalInterface
  public interface CredentialsFactory {
    AccessTokenSource create(String issuerUrl, String clientId, String clientSecret)
        throws Exception;
  }

  private static final CredentialsFactory DEFAULT_FACTORY =
      (issuerUrl, clientId, clientSecret) -> {
        var discovery = OIDCDiscovery.fetchOIDCDiscovery(issuerUrl);
        OAuth2ClientCredentials credentials =
            new OAuth2ClientCredentials(
                new ClientConfigAuth(clientId, clientSecret, discovery.tokenEndpoint()));
        return () -> credentials.getToken().accessToken();
      };

  private final String issuerUrl;
  private final String clientId;
  private final String clientSecret;
  private final boolean oauthConfigured;
  private final CredentialsFactory credentialsFactory;
  private final Object lock = new Object();
  private volatile AccessTokenSource accessTokenSource;

  public OidcTokenProvider(String issuerUrl, String clientId, String clientSecret) {
    this(issuerUrl, clientId, clientSecret, DEFAULT_FACTORY);
  }

  OidcTokenProvider(
      String issuerUrl,
      String clientId,
      String clientSecret,
      CredentialsFactory credentialsFactory) {
    this.issuerUrl = blankToNull(issuerUrl);
    this.clientId = blankToNull(clientId);
    this.clientSecret = blankToNull(clientSecret);
    this.oauthConfigured =
        this.issuerUrl != null && this.clientId != null && this.clientSecret != null;
    this.credentialsFactory = Objects.requireNonNull(credentialsFactory, "credentialsFactory");
    if (!oauthConfigured) {
      log.info(
          "OAuth2 client credentials not configured; authenticated service-to-service calls will be"
              + " unavailable");
    }
  }

  @Override
  public String getAccessToken() {
    if (!oauthConfigured) {
      throw new IllegalStateException("OAuth2 client credentials are not configured");
    }

    AccessTokenSource source = accessTokenSource;
    if (source == null) {
      synchronized (lock) {
        source = accessTokenSource;
        if (source == null) {
          source = initializeAccessTokenSource();
          accessTokenSource = source;
        }
      }
    }

    try {
      return source.getAccessToken();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to obtain OAuth2 access token", e);
    }
  }

  private AccessTokenSource initializeAccessTokenSource() {
    try {
      AccessTokenSource created = credentialsFactory.create(issuerUrl, clientId, clientSecret);
      log.info("OAuth2 client credentials initialized (OIDC issuer)");
      return created;
    } catch (Exception e) {
      log.warn(
          "Failed to initialize OAuth2 client credentials; will retry on next authenticated"
              + " request",
          e);
      throw new IllegalStateException("Failed to initialize OAuth2 client credentials", e);
    }
  }

  private static String blankToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
