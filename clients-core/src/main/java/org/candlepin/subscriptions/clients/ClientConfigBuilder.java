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
package org.candlepin.subscriptions.clients;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds a {@link ClientConfig} from Clowder endpoint mode and V1/V2 {@link ClientConnection}s.
 *
 * <p>{@code primary} is the V2 connection; {@code fallback} is the V1 connection. V2 is considered
 * present when it has a URI and Clowder provided {@code authenticated} (always true/false in V2).
 */
@Slf4j
public final class ClientConfigBuilder {

  private final String name;
  private final String psk;
  private final ClientConnection primary;
  private final ClientConnection fallback;
  private Duration readTimeout;

  private ClientConfigBuilder(
      String name, String psk, ClientConnection primary, ClientConnection fallback) {
    this.name = name;
    this.psk = psk;
    this.primary = primary;
    this.fallback = fallback;
  }

  public ClientConfigBuilder readTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
    return this;
  }

  public static ClientConfigBuilder with(
      String name, String psk, ClientConnection primary, ClientConnection fallback) {
    return new ClientConfigBuilder(name, psk, primary, fallback);
  }

  public ClientConfig build(ClientConfigMode mode) {
    // if primary is misconfigured, we use the fallback directly.
    if (primary == null || !primary.hasUri() || primary.authenticated() == null) {
      requireUri(fallback);
      log.warn("Forced to use fallback connection {} for client {}", fallback.id(), name);
      return new ClientConfig(name, psk, fallback, null, readTimeout);
    }

    // if fallback is misconfigured, we use the primary directly.
    if (fallback == null || !fallback.hasUri()) {
      log.warn("Forced to use primary connection {} for client {}", primary.id(), name);
      return new ClientConfig(name, psk, primary, null, readTimeout);
    }

    // if both connections are ok, we use the mode.
    return switch (mode) {
      case V1 -> new ClientConfig(name, psk, fallback, null, readTimeout);
      case V2 -> new ClientConfig(name, psk, primary, null, readTimeout);
      case AUTO -> new ClientConfig(name, psk, primary, fallback, readTimeout);
    };
  }

  private static void requireUri(ClientConnection connection) {
    if (connection == null || !connection.hasUri()) {
      throw new IllegalStateException("missing uri for " + connection);
    }
  }
}
