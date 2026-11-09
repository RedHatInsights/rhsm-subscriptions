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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.servicecontrol.v1.CheckRequest;
import com.google.api.servicecontrol.v1.ReportRequest;
import com.sun.net.httpserver.HttpServer;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GcpServiceControlClientTest {
  private static final String SERVICE_NAME = "example-product.gcpmarketplace.example.com";
  private final ObjectMapper mapper = new ObjectMapper();
  private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> authorization = new CopyOnWriteArrayList<>();
  private HttpServer server;
  private String endpoint;
  private GcpServiceControlClient client;
  @TempDir Path temporaryDirectory;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/token",
        exchange -> {
          tokenRequests.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response =
              "{\"access_token\":\"local-test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (var output = exchange.getResponseBody()) {
            output.write(response);
          }
        });
    server.createContext(
        "/v1/services/",
        exchange -> {
          paths.add(exchange.getRequestURI().getPath());
          authorization.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
          exchange.getRequestBody().readAllBytes();
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, 2);
          try (var output = exchange.getResponseBody()) {
            output.write("{}".getBytes(StandardCharsets.UTF_8));
          }
        });
    server.start();
    endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void close() {
    if (client != null) {
      client.close();
    }
    if (server != null) {
      server.stop(0);
    }
  }

  private void configureClient(Path credentialsFile) throws IOException {
    var properties = new Properties();
    try (var input = getClass().getResourceAsStream("/application.properties")) {
      properties.load(input);
    }
    properties.setProperty("gcp.credentials-file", credentialsFile.toString());
    properties.setProperty("gcp.endpoint", endpoint);
    // This standalone mapping uses the standard Duration parser rather than Quarkus's converter.
    properties.setProperty("gcp.request-timeout", "PT10S");
    var config =
        new SmallRyeConfigBuilder()
            .withSources(new PropertiesConfigSource(properties, "test"))
            .withMapping(GcpConfiguration.class)
            .build()
            .getConfigMapping(GcpConfiguration.class);
    client = new GcpServiceControlClient(config);
  }

  private void submit() {
    client.checkAndReport(
        CheckRequest.newBuilder().setServiceName(SERVICE_NAME).build(),
        ReportRequest.newBuilder().setServiceName(SERVICE_NAME).build());
  }

  @Test
  void loadsConfiguredServiceAccountScopesTokenAndAuthenticatesCheckAndReport() throws Exception {
    // Generate a disposable test key; no real Google identity or credentials are used.
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String privateKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder()
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    Path file = temporaryDirectory.resolve("service-account.json");
    mapper.writeValue(
        file.toFile(),
        Map.of(
            "type", "service_account",
            "project_id", "example-publisher",
            "client_email", "producer@example-publisher.iam.gserviceaccount.com",
            "client_id", "123456789012345678901",
            "private_key_id", "local-test-key",
            "private_key", privateKey,
            "token_uri", endpoint + "/token"));
    configureClient(file);

    submit();

    assertEquals(
        List.of(
            "/v1/services/" + SERVICE_NAME + ":check", "/v1/services/" + SERVICE_NAME + ":report"),
        paths);
    assertEquals(List.of("Bearer local-test-token", "Bearer local-test-token"), authorization);
    assertEquals(1, tokenRequests.size());
    String assertion =
        java.util.Arrays.stream(tokenRequests.getFirst().split("&"))
            .filter(field -> field.startsWith("assertion="))
            .map(
                field ->
                    URLDecoder.decode(
                        field.substring("assertion=".length()), StandardCharsets.UTF_8))
            .findFirst()
            .orElseThrow();
    var claims = mapper.readTree(Base64.getUrlDecoder().decode(assertion.split("\\.")[1]));
    assertEquals("https://www.googleapis.com/auth/cloud-platform", claims.get("scope").asText());
    assertEquals("producer@example-publisher.iam.gserviceaccount.com", claims.get("iss").asText());
  }

  @Test
  void missingCredentialFileFailsBeforeAnyRemoteRequest() throws IOException {
    configureClient(temporaryDirectory.resolve("missing.json"));

    var failure = assertThrows(IllegalStateException.class, this::submit);

    assertInstanceOf(NoSuchFileException.class, failure.getCause());
    assertTrue(paths.isEmpty());
    assertTrue(tokenRequests.isEmpty());
  }

  @Test
  void rejectsOtherCredentialTypesBeforeAnyRemoteRequest() throws IOException {
    Path file = temporaryDirectory.resolve("user-credentials.json");
    Files.writeString(file, "{\"type\":\"authorized_user\"}");
    configureClient(file);

    var failure = assertThrows(IllegalStateException.class, this::submit);

    assertInstanceOf(IOException.class, failure.getCause());
    assertTrue(paths.isEmpty());
    assertTrue(tokenRequests.isEmpty());
  }
}
