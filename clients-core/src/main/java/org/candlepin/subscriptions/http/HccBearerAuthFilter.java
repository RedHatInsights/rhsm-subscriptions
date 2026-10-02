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
package org.candlepin.subscriptions.http;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.util.function.Supplier;

/** Per-request workload authentication; only register on endpoints that require it. */
public class HccBearerAuthFilter implements ClientRequestFilter {
  private final Supplier<String> authorization;

  public HccBearerAuthFilter(Supplier<String> authorization) {
    this.authorization = authorization;
  }

  @Override
  public void filter(ClientRequestContext request) {
    if (authorization == null) {
      throw new IllegalStateException("HCC endpoint requires workload authentication");
    }
    String header = authorization.get();
    if (header == null || !header.startsWith("Bearer ") || header.substring(7).isBlank()) {
      throw new IllegalStateException("HCC workload token is missing");
    }
    request.getHeaders().remove("x-rh-exports-psk");
    request.getHeaders().putSingle("Authorization", header);
  }
}
