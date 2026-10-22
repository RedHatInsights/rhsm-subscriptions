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
package com.redhat.swatch.contract.service.export;

import com.redhat.swatch.clients.export.api.resources.ApiExceptionMapper;
import com.redhat.swatch.clients.export.api.resources.ExportApi;
import com.redhat.swatch.clowder.DependencyEndpointSelection;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * Builds and caches {@link ExportApi} clients for resolved export-service endpoints.
 *
 * <p>Request auth is handled by {@link ExportAuthHeaderProvider} from {@code
 * EXPORT_SERVICE_AUTHENTICATED}, independent of V1/V2 URI selection. Bearer auth requires an HTTPS
 * endpoint URI so the token is not sent in cleartext.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ExportServiceApiFactory {

  private final ExportAuthHeaderProvider authHeaderProvider;
  private final ConcurrentMap<DependencyEndpointSelection, ExportApi> clients =
      new ConcurrentHashMap<>();

  public ExportApi clientFor(DependencyEndpointSelection selection) {
    if (selection == null || selection.uri() == null || selection.uri().isBlank()) {
      throw new IllegalStateException("export-service endpoint URI is not configured");
    }
    return clients.computeIfAbsent(selection, this::build);
  }

  private ExportApi build(DependencyEndpointSelection selection) {
    URI baseUri = URI.create(selection.uri());
    if (authHeaderProvider.usesBearerAuth()) {
      requireEncryptedTransport(baseUri);
    }
    RestClientBuilder builder =
        RestClientBuilder.newBuilder()
            .baseUri(baseUri)
            .register(authHeaderProvider)
            .register(ApiExceptionMapper.class);
    KeyStore trustStore = loadTrustStore(selection);
    if (trustStore != null) {
      builder.trustStore(trustStore);
    }
    log.info("Built export-service client version={} uri={}", selection.version(), selection.uri());
    return builder.build(ExportApi.class);
  }

  static void requireEncryptedTransport(URI uri) {
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IllegalStateException(
          "export-service authenticated endpoint requires HTTPS transport, got scheme="
              + uri.getScheme());
    }
  }

  private static KeyStore loadTrustStore(DependencyEndpointSelection selection) {
    String path = selection.trustStorePath();
    if (path == null || path.isBlank()) {
      return null;
    }
    String type =
        selection.trustStoreType() == null || selection.trustStoreType().isBlank()
            ? KeyStore.getDefaultType()
            : selection.trustStoreType();
    try {
      KeyStore keyStore = KeyStore.getInstance(type);
      try (InputStream input = Files.newInputStream(Path.of(path))) {
        char[] password =
            selection.trustStorePassword() == null
                ? null
                : selection.trustStorePassword().toCharArray();
        keyStore.load(input, password);
      }
      return keyStore;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load export-service truststore from " + path, e);
    }
  }
}
