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
package com.redhat.swatch.common.security;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.project_kessel.api.auth.AuthRequest;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.rbac.v2.FetchWorkspace;

/** The SDK workspace lookup is a separate HTTP client and must share RBAC discovery and trust. */
@ApplicationScoped
public class RbacWorkspaceClient {

  @Inject KesselProperties properties;
  @Inject HccAuthTokenProvider tokenProvider;

  @ConfigProperty(name = "RBAC_AUTHENTICATED", defaultValue = "false")
  boolean authenticated;

  @ConfigProperty(name = "KESSEL_AUTH_ENABLED", defaultValue = "false")
  boolean kesselAuthEnabled;

  @ConfigProperty(name = "RBAC_TRUST_STORE")
  Optional<String> trustStore;

  @ConfigProperty(name = "RBAC_TRUST_STORE_PASSWORD")
  Optional<String> trustStorePassword;

  @ConfigProperty(name = "RBAC_TRUST_STORE_TYPE", defaultValue = "PKCS12")
  String trustStoreType;

  @ConfigProperty(name = "RBAC_ENDPOINT")
  String rbacEndpoint;

  private HttpClient client;

  public String defaultWorkspaceId(String orgId) {
    // Preserve legacy credential-driven workspace auth, as well as both explicit requirements.
    AuthRequest auth =
        authenticated || kesselAuthEnabled || tokenProvider.isConfigured()
            ? builder -> builder.setHeader("Authorization", tokenProvider.authorizationHeader())
            : null;
    try {
      return FetchWorkspace.fetchDefaultWorkspace(
              properties.rbacBaseEndpoint().filter(s -> !s.isBlank()).orElse(rbacEndpoint),
              orgId,
              auth,
              httpClient())
          .getId();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("RBAC workspace lookup was interrupted", e);
    } catch (IOException | OAuth2Exception e) {
      throw new IllegalStateException("RBAC workspace lookup failed", e);
    }
  }

  synchronized HttpClient httpClient() {
    if (client == null) {
      var builder =
          HttpClient.newBuilder().connectTimeout(Duration.ofMillis(properties.timeoutMs()));
      trustStore
          .filter(s -> !s.isBlank())
          .ifPresent(
              location -> {
                try (InputStream input = openTrustStore(location)) {
                  KeyStore store = KeyStore.getInstance(trustStoreType);
                  store.load(input, trustStorePassword.orElse("").toCharArray());
                  var factory =
                      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                  factory.init(store);
                  var context = SSLContext.getInstance("TLS");
                  context.init(null, factory.getTrustManagers(), null);
                  builder.sslContext(context);
                } catch (IOException | GeneralSecurityException e) {
                  throw new IllegalStateException("Cannot configure RBAC workspace TLS trust", e);
                }
              });
      // With no endpoint CA, leave the builder's default system trust intact.
      client = builder.build();
    }
    return client;
  }

  private InputStream openTrustStore(String location) throws IOException {
    if (location.startsWith("classpath:")) {
      String resource = location.substring("classpath:".length()).replaceFirst("^/", "");
      InputStream input =
          Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
      if (input == null) {
        throw new IOException("RBAC truststore resource was not found");
      }
      return input;
    }
    return Files.newInputStream(
        location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location));
  }

  @PreDestroy
  synchronized void close() {
    if (client != null) {
      client.close();
    }
  }
}
