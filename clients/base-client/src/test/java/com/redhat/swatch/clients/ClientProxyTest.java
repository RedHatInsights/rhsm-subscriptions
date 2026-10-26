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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientProxyTest {

  private static final ClientConnection PRIMARY =
      new ClientConnection("v2", "https://v2.example", null, null, null, true);
  private static final ClientConnection FALLBACK =
      new ClientConnection("v1", "http://v1.example", null, null, null, null);

  @Test
  void stickyFallbackStaysOnFallback() throws Exception {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);

    fallOverOnce(proxy);
    assertEquals(FALLBACK, config.current());

    List<ClientConnection> used = new ArrayList<>();
    proxy.invoke(used::add);
    assertEquals(List.of(FALLBACK), used);
    assertEquals(FALLBACK, config.current());
  }

  @Test
  void invokeFallsBackWhenPrimaryIsUnreachable() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);
    List<ClientConnection> used = new ArrayList<>();
    AtomicInteger attempts = new AtomicInteger();

    assertDoesNotThrow(
        () ->
            proxy.invoke(
                connection -> {
                  used.add(connection);
                  if (attempts.getAndIncrement() == 0) {
                    throw new ConnectException("refused");
                  }
                }));

    assertEquals(List.of(PRIMARY, FALLBACK), used);
    assertEquals(FALLBACK, config.current());
  }

  @Test
  void invokeFallsBackWhenPrimaryReturnsAuthFailure() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);
    List<ClientConnection> used = new ArrayList<>();
    AtomicInteger attempts = new AtomicInteger();

    assertDoesNotThrow(
        () ->
            proxy.invoke(
                connection -> {
                  used.add(connection);
                  if (attempts.getAndIncrement() == 0) {
                    throw new AuthFailureException(new RuntimeException("401"));
                  }
                }));

    assertEquals(List.of(PRIMARY, FALLBACK), used);
    assertEquals(FALLBACK, config.current());
  }

  @Test
  void invokeDoesNotFallbackOnOtherErrors() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);

    IllegalStateException error = new IllegalStateException("bad request");
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                proxy.invoke(
                    connection -> {
                      throw error;
                    }));
    assertSame(error, thrown);
    assertEquals(PRIMARY, config.current());
  }

  @Test
  void invokeDoesNotFallbackWhenNoFallbackConfigured() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, null, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);

    assertThrows(
        ConnectException.class,
        () ->
            proxy.invoke(
                connection -> {
                  throw new ConnectException("refused");
                }));
    assertEquals(PRIMARY, config.current());
  }

  @Test
  void invokeRetriesWhenStickySelectionAdvancesDuringFailure() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);
    List<ClientConnection> used = new ArrayList<>();

    assertDoesNotThrow(
        () ->
            proxy.invoke(
                connection -> {
                  used.add(connection);
                  if (PRIMARY.equals(connection)) {
                    assertTrue(config.tryFallback());
                    throw new ConnectException("refused");
                  }
                }));
    assertEquals(FALLBACK, config.current());
    assertEquals(PRIMARY, used.get(0));
    assertEquals(FALLBACK, used.get(used.size() - 1));
  }

  @Test
  void clientForCachesPerConnection() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    AtomicInteger builds = new AtomicInteger();
    ClientProxy<ClientConnection> proxy =
        new ClientProxy<>(
            config,
            connection -> {
              builds.incrementAndGet();
              return connection;
            });

    assertSame(proxy.clientFor(PRIMARY), proxy.clientFor(PRIMARY));
    assertEquals(1, builds.get());
  }

  @Test
  void buildHttpClientAppliesBoundedReadTimeout() {
    assertDoesNotThrow(() -> ClientProxy.buildHttpClient(PRIMARY, Duration.ofSeconds(120)).close());
  }

  @Test
  void readTimeoutDoesNotTriggerStickyFallback() {
    ClientConfig config = new ClientConfig("export-service", "psk", PRIMARY, FALLBACK, null);
    ClientProxy<ClientConnection> proxy = new ClientProxy<>(config, connection -> connection);

    assertThrows(
        SocketTimeoutException.class,
        () ->
            proxy.invoke(
                connection -> {
                  throw new SocketTimeoutException("Read timed out");
                }));
    assertEquals(PRIMARY, config.current());
  }

  private static void fallOverOnce(ClientProxy<ClientConnection> proxy) throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    proxy.invoke(
        connection -> {
          if (attempts.getAndIncrement() == 0) {
            throw new ConnectException("refused");
          }
        });
  }
}
