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
import lombok.Getter;

/** Configuration for a single outbound service client. */
@Getter
public class ClientConfig {

  private final String name;
  private final String psk;
  private final String uri;
  private final Boolean authenticated;
  private final String trustStorePath;
  private final char[] trustStorePassword;
  private final String trustStoreType;
  private final Duration readTimeout;

  public ClientConfig(
      String name,
      String psk,
      String uri,
      Boolean authenticated,
      String trustStorePath,
      char[] trustStorePassword,
      String trustStoreType,
      Duration readTimeout) {
    this.name = Objects.requireNonNull(name, "name");
    this.psk = psk;
    this.uri = uri;
    this.authenticated = authenticated;
    this.trustStorePath = trustStorePath;
    this.trustStorePassword = trustStorePassword;
    this.trustStoreType = trustStoreType;
    this.readTimeout = readTimeout;
  }

  public boolean hasUri() {
    return uri != null && !uri.isBlank();
  }

  public boolean isAuthenticated() {
    return Boolean.TRUE.equals(authenticated);
  }
}
