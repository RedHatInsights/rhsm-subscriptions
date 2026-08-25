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
 * Represents a user or service account authenticated via the x-rh-identity header.
 *
 * <p>This class handles both "User" and "ServiceAccount" identity types from the x-rh-identity
 * header, as they share the same org_id structure and authentication flow.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsightsUserPrincipal implements RhIdentity.Identity {

  public InsightsUserPrincipal() {
    // intentionally left empty
  }

  // package-private to be used for testing
  InsightsUserPrincipal(String org, String account) {
    internal.orgId = org;
    accountNumber = account;
  }

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

  /** POJO representation of "user" object inside the x-rh-identity object JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class User {
    @JsonProperty("user_id")
    private String userId;

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }
  }

  /** POJO representation of "service_account" object inside the x-rh-identity object JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ServiceAccount {
    @JsonProperty("client_id")
    private String clientId;

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }
  }

  @JsonProperty("account_number")
  private String accountNumber;

  @JsonProperty("org_id")
  private String orgId;

  @JsonProperty("user_id")
  private String userId;

  private Internal internal = new Internal();

  private User user = new User();

  @JsonProperty("service_account")
  private ServiceAccount serviceAccount;

  /**
   * Returns the principal identifier used for Kessel authorization checks ({@code redhat/{id}}).
   *
   * <p>For users, this returns the user_id. For service accounts, this returns the client_id.
   *
   * <p>Matches insights-rbac and swatch-common-security KesselPrincipalIds logic.
   */
  public Optional<String> getKesselPrincipalId() {
    // Check top-level user_id first (present in both user and service account identities)
    if (userId != null && !userId.isBlank()) {
      return Optional.of(userId);
    }
    // Check nested user.user_id (for User type)
    if (user != null && user.getUserId() != null && !user.getUserId().isBlank()) {
      return Optional.of(user.getUserId());
    }
    // Check service_account.client_id (for ServiceAccount type)
    if (serviceAccount != null
        && serviceAccount.getClientId() != null
        && !serviceAccount.getClientId().isBlank()) {
      return Optional.of(serviceAccount.getClientId());
    }
    return Optional.empty();
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

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

  public String toString() {
    return getOrgId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InsightsUserPrincipal)) {
      return false;
    }
    InsightsUserPrincipal principal = (InsightsUserPrincipal) o;
    return Objects.equals(getOrgId(), principal.getOrgId())
        && Objects.equals(getAccountNumber(), principal.getAccountNumber());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getOrgId(), getAccountNumber());
  }
}
