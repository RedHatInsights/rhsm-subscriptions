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
package com.redhat.swatch.gcp;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServiceControlStub implements QuarkusTestResourceLifecycleManager {
  static final List<String> paths = new CopyOnWriteArrayList<>();
  static final List<String> bodies = new CopyOnWriteArrayList<>();
  static volatile String checkResponse = "{}";
  static volatile String reportResponse = "{}";
  static volatile int checkStatus = 200;
  private HttpServer server;

  @Override
  public Map<String, String> start() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/v1/services/",
          exchange -> {
            String path = exchange.getRequestURI().getPath();
            paths.add(path);
            bodies.add(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean check = path.endsWith(":check");
            byte[] body = (check ? checkResponse : reportResponse).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(check ? checkStatus : 200, body.length);
            try (var output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      server.start();
      return Map.of(
          "gcp.dry-run",
          "false",
          "gcp.emulator-endpoint",
          "http://127.0.0.1:" + server.getAddress().getPort());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void stop() {
    if (server != null) {
      server.stop(0);
    }
  }
}
