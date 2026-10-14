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

/** Represents a user authenticated via the x-rh-identity header. */
@Slf4j
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsightsUserPrincipal extends OrgIdentityPrincipal {

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

  @JsonProperty("user_id")
  private String userId;

  private User user = new User();

  public InsightsUserPrincipal() {
    // intentionally left empty
  }

  // package-private to be used for testing
  InsightsUserPrincipal(String org, String account) {
    getInternal().setOrgId(org);
    setAccountNumber(account);
  }

  /**
   * Returns the principal identifier used for Kessel authorization checks ({@code redhat/{id}}).
   *
   * <p>For users, this returns user.user_id or top-level user_id.
   *
   * <p>Kessel requires user_id, not client_id.
   *
   * <p>Matches insights-rbac and swatch-common-security KesselPrincipalIds logic.
   */
  public Optional<String> getKesselPrincipalId() {
    // Check top-level user_id
    if (userId != null && !userId.isBlank()) {
      return Optional.of(userId);
    }
    // Check nested user.user_id
    if (user != null && user.getUserId() != null && !user.getUserId().isBlank()) {
      return Optional.of(user.getUserId());
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
}
