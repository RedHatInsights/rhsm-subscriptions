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
package org.candlepin.subscriptions.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Optional;

/**
 * Base class for identity principals that belong to an organization. Handles both User and
 * ServiceAccount types from x-rh-identity header.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class OrgIdentityPrincipal implements RhIdentity.Identity {

  /** POJO representation of "internal" object inside the x-rh-identity object JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Internal {
    @JsonProperty("org_id")
    private String orgId;

    public String getOrgId() {
      return orgId;
    }

    public void setOrgId(String orgId) {
      this.orgId = orgId;
    }
  }

  @JsonProperty("account_number")
  private String accountNumber;

  @JsonProperty("org_id")
  private String orgId;

  private Internal internal = new Internal();

  /** Returns the organization ID, preferring the value from internal.org_id if present. */
  public String getOrgId() {
    String internalOrgId = internal == null ? null : internal.getOrgId();
    if (internalOrgId != null && !internalOrgId.isBlank()) {
      return internalOrgId;
    }
    return orgId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public Internal getInternal() {
    return internal;
  }

  public void setInternal(Internal internal) {
    this.internal = internal;
  }

  /**
   * Returns the principal identifier used for Kessel authorization checks. Subclasses must
   * implement this based on their specific identity type.
   */
  public abstract Optional<String> getKesselPrincipalId();

  public String toString() {
    return getOrgId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OrgIdentityPrincipal principal)) {
      return false;
    }
    return Objects.equals(getOrgId(), principal.getOrgId())
        && Objects.equals(getAccountNumber(), principal.getAccountNumber());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getOrgId(), getAccountNumber());
  }
}
