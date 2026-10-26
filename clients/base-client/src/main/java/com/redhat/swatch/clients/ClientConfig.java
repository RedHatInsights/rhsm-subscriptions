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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * Sticky primary/fallback endpoint selection. Starts on {@code primary}; on connectivity failure
 * {@link #tryFallback()} switches once to {@code fallback} for the process lifetime.
 */
public class ClientConfig {
  @Getter private final String name;
  @Getter private final String psk;
  @Getter private final Duration readTimeout;
  private final ClientConnection primary;
  private final ClientConnection fallback;
  private final AtomicBoolean usingFallback = new AtomicBoolean(false);

  /**
   * @param name dependency name
   * @param psk shared PSK
   * @param primary preferred endpoint
   * @param fallback optional sticky fallback
   * @param readTimeout optional RESTEasy read timeout for availability probes
   */
  public ClientConfig(
      String name,
      String psk,
      ClientConnection primary,
      ClientConnection fallback,
      Duration readTimeout) {
    this.name = Objects.requireNonNull(name, "name");
    this.psk = Objects.requireNonNull(psk, "psk");
    this.primary = Objects.requireNonNull(primary, "primary");
    this.fallback = fallback;
    this.readTimeout = readTimeout;
  }

  public ClientConnection current() {
    return usingFallback.get() && fallback != null ? fallback : primary;
  }

  public ClientConnection primary() {
    return primary;
  }

  public ClientConnection fallback() {
    return fallback;
  }

  /**
   * Advances sticky selection to {@code fallback} when one exists. Returns {@code true} only when
   * this call performs the switch.
   */
  protected boolean tryFallback() {
    if (fallback == null || !fallback.hasUri()) {
      return false;
    }
    return usingFallback.compareAndSet(false, true);
  }
}
