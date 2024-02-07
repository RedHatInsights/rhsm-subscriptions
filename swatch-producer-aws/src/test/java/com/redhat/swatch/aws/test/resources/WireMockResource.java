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
package com.redhat.swatch.aws.test.resources;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class WireMockResource
    implements QuarkusTestResourceConfigurableLifecycleManager<WireMockTest> {
  private WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    // allow static stubbing methods for this thread
    WireMock.configureFor(new WireMock(wireMockServer));
    // setup static wiremock methods to affect this wiremock server
    WireMock.configureFor("localhost", wireMockServer.port());
    var config = new HashMap<String, String>();
    config.put("SWATCH_INTERNAL_SUBSCRIPTION_ENDPOINT", wireMockServer.baseUrl());
    return config;
  }

  @Override
  public void stop() {
    wireMockServer.stop();
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(
        wireMockServer,
        new TestInjector.AnnotatedAndMatchesType(Inject.class, WireMockServer.class));
    // allow static stubbing methods for test threads
    WireMock.configureFor(new WireMock(wireMockServer));
  }
}
