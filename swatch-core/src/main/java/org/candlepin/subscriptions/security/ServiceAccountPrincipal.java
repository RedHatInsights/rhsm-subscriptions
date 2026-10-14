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
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Represents a service account authenticated via the x-rh-identity header. */
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceAccountPrincipal extends OrgIdentityPrincipal {

  /** POJO representation of "service_account" object inside the x-rh-identity object JSON. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ServiceAccount {
    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("user_id")
    private String userId;

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }
  }

  @JsonProperty("user_id")
  private String userId;

  @JsonProperty("service_account")
  private ServiceAccount serviceAccount;

  public ServiceAccountPrincipal() {
    // intentionally left empty
  }

  // package-private to be used for testing
  ServiceAccountPrincipal(String org, String account) {
    getInternal().setOrgId(org);
    setAccountNumber(account);
  }

  /**
   * Returns the principal identifier used for Kessel authorization checks ({@code redhat/{id}}).
   *
   * <p>For service accounts, this returns the user_id from the service_account object, as used by
   * Kessel for service account authorization.
   */
  public Optional<String> getKesselPrincipalId() {
    // Kessel identifies service accounts by service_account.user_id, not client_id.
    if (serviceAccount != null
        && serviceAccount.getUserId() != null
        && !serviceAccount.getUserId().isBlank()) {
      return Optional.of(serviceAccount.getUserId());
    }
    // Retain support for non-standard identities that put user_id at the top level.
    if (userId != null && !userId.isBlank()) {
      String clientId = serviceAccount == null ? null : serviceAccount.getClientId();
      log.warn(
          "ServiceAccount has user_id at top-level instead of service_account.user_id"
              + " (non-standard): orgId={} clientId={}",
          getOrgId(),
          clientId);
      return Optional.of(userId);
    }
    // No user_id found - error and deny
    log.error(
        "ServiceAccount missing REQUIRED user_id field - denying access: orgId={} clientId={}",
        getOrgId(),
        serviceAccount == null ? null : serviceAccount.getClientId());
    return Optional.empty();
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public ServiceAccount getServiceAccount() {
    return serviceAccount;
  }

  public void setServiceAccount(ServiceAccount serviceAccount) {
    this.serviceAccount = serviceAccount;
  }
}
