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
package com.redhat.swatch.component.tests.utils;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PooledRestAssuredTest {

  // One more than PoolingClientConnectionManager defaultMaxPerRoute (8).
  private static final int REQUESTS_BEYOND_MAX_PER_ROUTE = 9;
  private static final byte[] EMPTY = new byte[0];

  private KeepAliveServer server;

  @BeforeEach
  void givenKeepAliveServer() throws IOException {
    server = new KeepAliveServer();
    server.start();
    PooledRestAssured.install();
  }

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void shouldReleaseConnectionsAfterKeepAliveResponses() {
    for (int i = 0; i < REQUESTS_BEYOND_MAX_PER_ROUTE; i++) {
      int request = i;
      assertDoesNotThrow(
          this::whenPostEmptyOk,
          () ->
              "request "
                  + request
                  + " failed with "
                  + PooledRestAssured.leasedCount()
                  + " leased connections");
      assertEquals(0, PooledRestAssured.leasedCount(), "leased after request " + request);
    }
  }

  @Test
  void shouldReusePoolAfterServerDropsKeepAliveSocket() {
    whenPostEmptyOk();
    server.dropClientSockets();
    assertDoesNotThrow(this::whenPostEmptyOk);
    assertEquals(0, PooledRestAssured.leasedCount());
  }

  private void whenPostEmptyOk() {
    given()
        .baseUri("http://127.0.0.1")
        .port(server.port())
        .contentType("application/json")
        .body(Map.of("contains", "component-test-generated"))
        .when()
        .post("/__admin/mappings/remove-by-metadata")
        .then()
        .statusCode(200);
  }

  /**
   * HTTP/1.1 keep-alive server that reuses the accepted TCP socket. Closing the client socket after
   * a response mimics Fabric8 LocalPortForward dropping a tunnel while RestAssured still has the
   * connection in the pool.
   */
  private static final class KeepAliveServer implements AutoCloseable {
    private final ServerSocket serverSocket;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private volatile Socket currentClient;

    KeepAliveServer() throws IOException {
      this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    }

    int port() {
      return serverSocket.getLocalPort();
    }

    void start() {
      workers.submit(this::acceptLoop);
    }

    void dropClientSockets() {
      Socket client = currentClient;
      if (client != null) {
        try {
          client.close();
        } catch (IOException ignored) {
          // test fixture
        }
      }
    }

    @Override
    public void close() {
      running.set(false);
      workers.shutdownNow();
      try {
        serverSocket.close();
      } catch (IOException ignored) {
        // test fixture
      }
      dropClientSockets();
    }

    private void acceptLoop() {
      while (running.get()) {
        try {
          Socket client = serverSocket.accept();
          currentClient = client;
          workers.submit(() -> handleClient(client));
        } catch (IOException e) {
          if (running.get()) {
            throw new IllegalStateException(e);
          }
        }
      }
    }

    private void handleClient(Socket client) {
      try (Socket ignored = client;
          InputStream raw = client.getInputStream();
          OutputStream out = client.getOutputStream()) {
        InputStream in = new BufferedInputStream(raw);
        while (running.get() && !client.isClosed()) {
          if (!consumeRequest(in)) {
            return;
          }
          out.write(response());
          out.flush();
        }
      } catch (IOException ignored) {
        // client or test closed the socket
      }
    }

    private static boolean consumeRequest(InputStream in) throws IOException {
      StringBuilder headers = new StringBuilder();
      int seen = 0;
      while (seen < 4) {
        int b = in.read();
        if (b == -1) {
          return false;
        }
        headers.append((char) b);
        if (b == '\r' || b == '\n') {
          seen++;
        } else {
          seen = 0;
        }
      }
      int contentLength = 0;
      for (String line : headers.toString().split("\r\n")) {
        int colon = line.indexOf(':');
        if (colon > 0 && line.substring(0, colon).equalsIgnoreCase("Content-Length")) {
          contentLength = Integer.parseInt(line.substring(colon + 1).trim());
        }
      }
      in.readNBytes(contentLength);
      return true;
    }

    private static byte[] response() {
      String header =
          "HTTP/1.1 200 OK\r\n"
              + "Content-Length: "
              + EMPTY.length
              + "\r\n"
              + "Connection: keep-alive\r\n"
              + "\r\n";
      return header.getBytes(StandardCharsets.US_ASCII);
    }
  }
}
