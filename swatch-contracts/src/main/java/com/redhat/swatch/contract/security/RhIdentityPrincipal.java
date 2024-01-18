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

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.security.Principal;
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
  private Identity identity;

  /* Base64 encoded header value retained so that it can be easily forwarded to rbac service */
  @JsonIgnore private String headerValue;

  @Override
  public String getName() {
    return switch (identity.getType()) {
      case "Associate" -> identity.getSamlAssertions().getEmail();
      case "X509" -> identity.getX509().getSubjectDn();
      case "User" -> identity.getOrgId();
      default ->
          throw new IllegalArgumentException(
              String.format("Unsupported RhIdentity type %s", getIdentity().getType()));
    };
  }

  public String toString() {
    return getName();
  }
}
