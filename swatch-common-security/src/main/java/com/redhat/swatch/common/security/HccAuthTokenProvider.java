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
package com.redhat.swatch.common.security;

import com.redhat.swatch.kessel.HccCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** One lazy SDK credential cache shared by Quarkus HCC clients, including token renewal. */
@ApplicationScoped
public class HccAuthTokenProvider {

  @Inject KesselProperties properties;
  private final HccCredentials credentials =
      new HccCredentials(
          () -> properties.authOidcIssuer().orElse(null),
          () -> properties.authClientId().orElse(null),
          () -> properties.authClientSecret().orElse(null));

  public boolean isConfigured() {
    return credentials.isConfigured();
  }

  public String authorizationHeader() {
    return credentials.authorizationHeader();
  }

  HccCredentials credentials() {
    return credentials;
  }
}
