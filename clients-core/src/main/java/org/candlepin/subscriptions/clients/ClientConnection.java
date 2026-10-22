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

/**
 * Endpoint settings for a single Clowder (or local) dependency target.
 *
 * @param id logical id (e.g. {@code v1}, {@code v2})
 * @param uri base URI
 * @param trustStorePath optional truststore path
 * @param trustStorePassword optional truststore password
 * @param trustStoreType optional truststore type
 * @param authenticated Clowder V2 auth mode: {@code true} → Bearer token (omit PSK); {@code false}
 *     or {@code null} → existing {@code x-rh-exports-psk} flow
 */
public record ClientConnection(
    String id,
    String uri,
    String trustStorePath,
    char[] trustStorePassword,
    String trustStoreType,
    Boolean authenticated) {

  public boolean hasUri() {
    return uri != null && !uri.isBlank();
  }

  public boolean isAuthenticated() {
    return Boolean.TRUE.equals(authenticated);
  }
}
