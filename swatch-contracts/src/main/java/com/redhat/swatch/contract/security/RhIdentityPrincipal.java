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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Principal;
import java.util.Base64;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.annotation.JsonbTransient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a principal authenticated via x-rh-identity.
 *
 * <p>This is the decoded x-rh-identity data currently supported.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RhIdentityPrincipal implements Principal {
  private static Jsonb jsonb = JsonbBuilder.create();

  private Identity identity;

  /* header value captured so it can be easily forwarded to rbac service */
  @JsonbTransient private String headerValue;

  public static RhIdentityPrincipal fromHeader(String header) {
    return fromJson(new ByteArrayInputStream(Base64.getDecoder().decode(header)), header);
  }

  public static RhIdentityPrincipal fromJson(String json) {
    return jsonb.fromJson(json, RhIdentityPrincipal.class);
  }

  public static RhIdentityPrincipal fromJson(InputStream inputStream, String headerValue) {
    var identity = jsonb.fromJson(inputStream, RhIdentityPrincipal.class);
    identity.setHeaderValue(headerValue);
    return identity;
  }

  @Override
  public String getName() {
    return switch (identity.getType()) {
      case "Associate" -> identity.getSamlAssertions().getEmail();
      case "X509" -> identity.getX509().getSubjectDn();
      case "User" -> identity.getOrgId();
      default -> throw new IllegalArgumentException(
          String.format("Unsupported RhIdentity type %s", getIdentity().getType()));
    };
  }

  public String toString() {
    return getName();
  }
}
