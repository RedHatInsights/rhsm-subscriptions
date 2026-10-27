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

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.redhat.swatch.clients.export.SwatchExportApi;
import com.redhat.swatch.clients.export.api.client.ApiException;
import com.redhat.swatch.clients.export.api.model.DownloadExportErrorRequest;
import com.redhat.swatch.contract.test.resources.ExportApiAuthWireMockResource;
import com.redhat.swatch.contract.test.resources.InjectWireMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.ResourceArg;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Quarkus wiring: {@code authenticated=false} → PSK (mirrors Spring {@code
 * ExportApiAuthSpringBootTest.UnauthenticatedPsk}).
 */
@QuarkusTest
@QuarkusTestResource(
    value = ExportApiAuthWireMockResource.class,
    initArgs = @ResourceArg(name = "authenticated", value = "false"),
    restrictToAnnotatedClass = true)
class ExportApiAuthPskQuarkusTest {

  private static final UUID EXPORT_ID = UUID.randomUUID();
  private static final String APPLICATION = "subscriptions";
  private static final UUID RESOURCE_ID = UUID.randomUUID();

  @Inject SwatchExportApi exportApi;
  @InjectWireMock WireMockServer wireMock;

  @Test
  void sendsPskAndOmitsBearer() {
    try {
      exportApi.downloadExportError(
          EXPORT_ID, APPLICATION, RESOURCE_ID, new DownloadExportErrorRequest());
    } catch (ApiException e) {
      fail(e);
    }
    wireMock.verify(
        postRequestedFor(urlPathEqualTo(errorPath()))
            .withHeader("x-rh-exports-psk", equalTo("placeholder"))
            .withHeader("Authorization", absent()));
  }

  private static String errorPath() {
    return "/app/export/v1/" + EXPORT_ID + "/" + APPLICATION + "/" + RESOURCE_ID + "/error";
  }
}
