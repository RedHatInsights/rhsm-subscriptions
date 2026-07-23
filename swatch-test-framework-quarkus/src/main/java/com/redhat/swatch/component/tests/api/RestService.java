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
package com.redhat.swatch.component.tests.api;

import static com.redhat.swatch.component.tests.utils.Ports.DEFAULT_HTTP_PORT;

import com.redhat.swatch.component.tests.core.BaseService;
import com.redhat.swatch.component.tests.logging.Log;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

public class RestService extends BaseService<RestService> {

  protected static final String HTTP = "http://";
  protected static final String BASE_PATH = "/";

  private final int httpPort;
  private final String basePath;

  public RestService() {
    this(DEFAULT_HTTP_PORT, BASE_PATH);
  }

  public RestService(int httpPort, String basePath) {
    this.httpPort = httpPort;
    this.basePath = basePath;
  }

  public RequestSpecification given() {
    return RestAssured.given()
        .baseUri(HTTP + getHost())
        .basePath(basePath)
        .port(getMappedPort(httpPort));
  }

  @Override
  public void start() {
    super.start();

    RestAssured.baseURI = HTTP + getHost();
    RestAssured.basePath = basePath;
    RestAssured.port = getMappedPort(httpPort);

    Log.debug(this, "REST service running at " + HTTP + getHost() + ":" + getMappedPort(httpPort));
  }

  @Override
  public void stop() {
    super.stop();
    RestAssured.reset();
  }
}
