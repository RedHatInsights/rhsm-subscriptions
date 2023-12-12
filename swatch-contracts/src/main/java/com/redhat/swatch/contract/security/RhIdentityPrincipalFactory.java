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
package com.redhat.swatch.contract.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

@ApplicationScoped
public class RhIdentityPrincipalFactory {
  @Inject ObjectMapper mapper;

  public RhIdentityPrincipal fromHeader(String header) throws IOException {
    return fromJson(new ByteArrayInputStream(Base64.getDecoder().decode(header)), header);
  }

  public RhIdentityPrincipal fromJson(String json) throws IOException {
    return mapper.readValue(json, RhIdentityPrincipal.class);
  }

  public RhIdentityPrincipal fromJson(InputStream inputStream, String headerValue)
      throws IOException {
    var identity = mapper.readValue(inputStream, RhIdentityPrincipal.class);
    identity.setHeaderValue(headerValue);
    return identity;
  }
}
