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
package org.candlepin.subscriptions.test;

import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ExtendWithExportServiceWireMock {

  static WireMockServer wireMockServer = start();
  private static final String EXPORT_ERROR_REQUEST =
      "/app/export/v1/b24c269d-33d6-410e-8808-c71c9635e84f/subscriptions/2e3d7746-2cf2-441e-84fe-cf28863d22ae/error";

  static WireMockServer start() {
    wireMockServer =
        new WireMockServer(wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(true)));
    wireMockServer.resetAll();
    wireMockServer.start();
    wireMockServer.stubFor(post(EXPORT_ERROR_REQUEST));
    System.out.printf("Running mock export services on port %d%n", wireMockServer.port());
    return wireMockServer;
  }

  @DynamicPropertySource
  static void registerExportApiProperties(DynamicPropertyRegistry registry) {
    registry.add("rhsm-subscriptions.export-service.url", wireMockServer::baseUrl);
  }
}
