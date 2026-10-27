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
package com.redhat.swatch.contract.test.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;

/**
 * WireMock + export-service config for auth wiring tests. Init arg {@code authenticated} selects
 * Bearer ({@code true}) vs PSK ({@code false}).
 */
public class ExportApiAuthWireMockResource implements QuarkusTestResourceLifecycleManager {

  private WireMockServer wireMockServer;
  private String authenticated = "true";

  @Override
  public void init(Map<String, String> initArgs) {
    if (initArgs != null && initArgs.containsKey("authenticated")) {
      authenticated = initArgs.get("authenticated");
    }
  }

  @Override
  public Map<String, String> start() {
    wireMockServer =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .stubRequestLoggingDisabled(true)
                .maxLoggedResponseSize(1000));
    wireMockServer.start();
    wireMockServer.stubFor(post(urlPathMatching("/internal/export/.*")).willReturn(ok()));
    String base = wireMockServer.baseUrl();
    return Map.of(
        "EXPORT_SERVICE_ENDPOINT",
        base,
        "EXPORT_SERVICE_AUTHENTICATED",
        authenticated,
        "SWATCH_EXPORT_PSK",
        "placeholder");
  }

  @Override
  public void stop() {
    if (wireMockServer != null) {
      wireMockServer.stop();
      wireMockServer = null;
    }
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(
        wireMockServer,
        new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockServer.class));
  }
}
