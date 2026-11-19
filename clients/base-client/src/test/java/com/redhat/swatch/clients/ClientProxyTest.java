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
package com.redhat.swatch.clients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.ws.rs.client.Client;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;

class ClientProxyTest {

  @Test
  void clientIsBuiltLazilyAndCached() throws Exception {
    AtomicInteger builds = new AtomicInteger();
    ClientConfig config =
        ClientConfig.builder()
            .name("export-service")
            .psk("psk")
            .uri("https://export.example")
            .authenticated(true)
            .build();
    ClientProxy<String> proxy =
        new ClientProxy<>(
            config,
            unused -> {
              builds.incrementAndGet();
              return "client";
            });

    proxy.invoke(client -> assertEquals("client", client));
    assertSame(proxy.client(), proxy.client());
    assertEquals(1, builds.get());
  }

  @Test
  void missingUriFailsClosed() {
    ClientConfig config =
        ClientConfig.builder().name("export-service").psk("psk").authenticated(false).build();
    ClientProxy<String> proxy = new ClientProxy<>(config, unused -> "client");
    assertThrows(IllegalStateException.class, proxy::client);
  }

  @Test
  void loadsStoreFromJarUrl() throws Exception {
    char[] password = "changeit".toCharArray();
    KeyStore store = KeyStore.getInstance("JKS");
    store.load(null, password);
    Path jar = Files.createTempFile("client-proxy", ".jar");

    try {
      try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
        output.putNextEntry(new JarEntry("truststore.jks"));
        store.store(output, password);
        output.closeEntry();
      }

      ClientConfig config =
          ClientConfig.builder()
              .name("rbac-service")
              .uri("https://rbac.example")
              .trustStorePath("jar:" + jar.toUri() + "!/truststore.jks")
              .trustStorePassword(password)
              .trustStoreType("JKS")
              .build();

      try (Client ignored = ClientProxy.buildHttpClient(config)) {
        // The client is built to verify that the JAR resource was loaded successfully.
      }
    } finally {
      Files.deleteIfExists(jar);
    }
  }
}
