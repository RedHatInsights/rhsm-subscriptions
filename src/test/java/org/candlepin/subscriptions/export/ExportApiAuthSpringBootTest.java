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
package org.candlepin.subscriptions.export;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import java.util.UUID;
import org.candlepin.subscriptions.security.OAuth2TokenProvider;
import org.candlepin.subscriptions.test.ExtendWithExportServiceWireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration coverage for export-service auth selection. {@code authenticated} is runtime
 * metadata, so this uses {@link SpringBootTest} rather than component tests.
 */
class ExportApiAuthSpringBootTest {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION = "subscriptions";
  private static final UUID RESOURCE_ID = UUID.randomUUID();
  private static final String TOKEN = "spring-boot-test-token";

  @Nested
  @SpringBootTest
  @ActiveProfiles({"worker", "kafka-queue", "test"})
  class AuthenticatedBearer extends AuthCase {

    @DynamicPropertySource
    static void exportServiceAuthenticated(DynamicPropertyRegistry registry) {
      registerExport(registry, "true");
    }

    @Test
    void sendsBearerTokenAndOmitsPsk() {
      callDownloadExportError();
      EXPORT_SERVICE_WIRE_MOCK_SERVER.verify(
          postRequestedFor(urlPathEqualTo(errorPath()))
              .withHeader("Authorization", equalTo("Bearer " + TOKEN))
              .withHeader("x-rh-exports-psk", absent()));
    }

    @Test
    void tokenFailureDoesNotFallBackToPsk() {
      when(oauth2TokenProvider.getAccessToken())
          .thenThrow(new IllegalStateException("token unavailable"));
      assertThrows(
          Exception.class,
          () ->
              exportApi.downloadExportError(
                  EXPORT_ID, APPLICATION, RESOURCE_ID, new DownloadExportErrorRequest()));
      EXPORT_SERVICE_WIRE_MOCK_SERVER.verify(0, postRequestedFor(urlPathEqualTo(errorPath())));
    }
  }

  @Nested
  @SpringBootTest
  @ActiveProfiles({"worker", "kafka-queue", "test"})
  class UnauthenticatedPsk extends AuthCase {

    @DynamicPropertySource
    static void exportServiceUnauthenticated(DynamicPropertyRegistry registry) {
      registerExport(registry, "false");
    }

    @Test
    void sendsPskAndOmitsBearer() {
      callDownloadExportError();
      EXPORT_SERVICE_WIRE_MOCK_SERVER.verify(
          postRequestedFor(urlPathEqualTo(errorPath()))
              .withHeader("x-rh-exports-psk", equalTo("placeholder"))
              .withHeader("Authorization", absent()));
    }
  }

  abstract static class AuthCase implements ExtendWithExportServiceWireMock {

    @Autowired SwatchExportApi exportApi;

    @MockitoBean OAuth2TokenProvider oauth2TokenProvider;

    @BeforeEach
    void stubExportError() {
      when(oauth2TokenProvider.getAccessToken()).thenReturn(TOKEN);
      EXPORT_SERVICE_WIRE_MOCK_SERVER.stubFor(post(urlPathEqualTo(errorPath())).willReturn(ok()));
    }

    protected void callDownloadExportError() {
      try {
        exportApi.downloadExportError(
            EXPORT_ID, APPLICATION, RESOURCE_ID, new DownloadExportErrorRequest());
      } catch (ApiException e) {
        fail(e);
      }
    }

    protected static String errorPath() {
      return "/app/export/v1/" + EXPORT_ID + "/" + APPLICATION + "/" + RESOURCE_ID + "/error";
    }

    protected static void registerExport(DynamicPropertyRegistry registry, String authenticated) {
      registry.add(
          "rhsm-subscriptions.export-service.uri", EXPORT_SERVICE_WIRE_MOCK_SERVER::baseUrl);
      registry.add("rhsm-subscriptions.export-service.authenticated", () -> authenticated);
    }
  }
}
