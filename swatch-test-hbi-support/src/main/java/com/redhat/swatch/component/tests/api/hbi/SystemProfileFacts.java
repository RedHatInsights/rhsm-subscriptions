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
package com.redhat.swatch.component.tests.api.hbi;

import java.util.Set;
import lombok.Getter;
import lombok.Singular;

/**
 * System-profile fields reported by HBI. Database-backed fields are persisted to {@code
 * hbi.system_profiles_static}; installed product IDs are included in generated HBI event payloads.
 * Mirrors the fields read by {@code SystemProfileFacts} in {@code swatch-model-metrics-hbi}.
 */
@Getter
public final class SystemProfileFacts {

  private final String hostType;
  private final String hypervisorUuid;
  private final String infrastructureType;
  private final Integer coresPerSocket;
  private final Integer numberOfSockets;
  private final Integer numberOfCpus;
  private final Integer threadsPerCore;
  private final String cloudProvider;
  private final String arch;
  private final Boolean isMarketplace;
  private final Boolean conversionsActivity;
  private final Set<String> installedProductIds;

  // Fully-qualified: a bare `@Builder` here would resolve to the nested Builder class below
  // instead of lombok.Builder, since a member type shadows a same-named import in its own body.
  @lombok.Builder(builderClassName = "Builder", toBuilder = true)
  private SystemProfileFacts(
      String hostType,
      String hypervisorUuid,
      String infrastructureType,
      Integer coresPerSocket,
      Integer numberOfSockets,
      Integer numberOfCpus,
      Integer threadsPerCore,
      String cloudProvider,
      String arch,
      Boolean isMarketplace,
      Boolean conversionsActivity,
      @Singular("installedProductId") Set<String> installedProductIds) {
    this.hostType = hostType;
    this.hypervisorUuid = hypervisorUuid;
    this.infrastructureType = infrastructureType;
    this.coresPerSocket = coresPerSocket;
    this.numberOfSockets = numberOfSockets;
    this.numberOfCpus = numberOfCpus;
    this.threadsPerCore = threadsPerCore;
    this.cloudProvider = cloudProvider;
    this.arch = arch;
    this.isMarketplace = isMarketplace;
    this.conversionsActivity = conversionsActivity;
    this.installedProductIds = installedProductIds;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Returns a copy with {@code coresPerSocket} set, unless it was already set. */
  SystemProfileFacts withComputedCoresPerSocket(int computedCoresPerSocket) {
    if (coresPerSocket != null) {
      return this;
    }
    return toBuilder().coresPerSocket(computedCoresPerSocket).build();
  }

  public static class Builder {}
}
