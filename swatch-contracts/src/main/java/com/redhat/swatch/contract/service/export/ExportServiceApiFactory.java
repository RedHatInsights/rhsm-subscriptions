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
 * <p>Per-connection auth matches Spring {@code SwatchExportApi} (SWATCH-5655): {@code
 * authenticated=true} → Kessel Bearer and omit PSK; {@code authenticated=false} or unset (V1) →
 * {@code x-rh-exports-psk} and omit Bearer. Auth failures are not downgraded between schemes.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ExportServiceApiFactory {

  private final ExportPskHeaderProvider pskHeaderProvider;
  private final ExportBearerHeaderProvider bearerHeaderProvider;
  private final ConcurrentMap<DependencyEndpointSelection, ExportApi> clients =
      new ConcurrentHashMap<>();

  public ExportApi clientFor(DependencyEndpointSelection selection) {
    if (selection == null || selection.uri() == null || selection.uri().isBlank()) {
      throw new IllegalStateException("export-service endpoint URI is not configured");
    }
    return clients.computeIfAbsent(selection, this::build);
  }

  /** {@code authenticated=true} → bearer. Otherwise ({@code false}, V1 {@code null}) → PSK. */
  static boolean useBearerAuth(DependencyEndpointSelection selection) {
    return selection != null
        && selection.endpoint() != null
        && Boolean.TRUE.equals(selection.endpoint().authenticated());
  }

  private ExportApi build(DependencyEndpointSelection selection) {
    boolean bearer = useBearerAuth(selection);
    Object authFilter = bearer ? bearerHeaderProvider : pskHeaderProvider;
    RestClientBuilder builder =
        RestClientBuilder.newBuilder()
            .baseUri(URI.create(selection.uri()))
            .register(authFilter)
            .register(ApiExceptionMapper.class);
    KeyStore trustStore = loadTrustStore(selection);
    if (trustStore != null) {
      builder.trustStore(trustStore);
    }
    log.info(
        "Built export-service client version={} uri={} authenticated={}",
        selection.version(),
        selection.uri(),
        selection.endpoint() == null ? null : selection.endpoint().authenticated());
    return builder.build(ExportApi.class);
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
