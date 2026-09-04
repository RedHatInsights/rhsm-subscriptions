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

import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Builder for applying reporter facts and customizations before inserting a {@link Host}.
 *
 * <p>Exposes only orthogonal, composable setters for attaching typed reporter facts ({@link
 * RhsmFacts}, {@link SatelliteFacts}, {@link QpcFacts}) and {@link SystemProfileFacts}. Attaching a
 * reporter's facts also registers that reporter on the host. Host-level templates that shape
 * several of these at once (e.g. "physical RHEL host") live in {@link HostTemplates} and are
 * applied via {@link #apply}.
 *
 * <p>Call {@link #insert()} to seed a new host, then keep the same builder instance and call {@link
 * #update()} to persist further changes to the same row - useful for tests that seed a host, tally,
 * change a fact, tally again.
 */
public class HostBuilder {
  private final HostStateManager manager;
  private final Host host;

  HostBuilder(HostStateManager manager, Host host) {
    this.manager = manager;
    this.host = host;
  }

  // ===== Templates =====

  /**
   * Apply a host-level template (see {@link HostTemplates}), e.g. {@code
   * HostTemplates.physicalRhel(2, 4)}.
   */
  public HostBuilder apply(Function<HostBuilder, HostBuilder> template) {
    return template.apply(this);
  }

  // ===== Reporter Facts =====

  /** Attach RHSM Conduit facts and register {@code rhsm-conduit} as a reporter. */
  public HostBuilder rhsmFacts(RhsmFacts rhsmFacts) {
    host.rhsmFacts(rhsmFacts);
    setPrimaryReporter("rhsm-conduit");
    return this;
  }

  /** Attach Satellite facts and register {@code satellite} as a reporter. */
  public HostBuilder satelliteFacts(SatelliteFacts satelliteFacts) {
    host.satelliteFacts(satelliteFacts);
    setPrimaryReporter("satellite");
    return this;
  }

  /** Attach QPC/discovery facts and register {@code discovery} as a reporter. */
  public HostBuilder qpcFacts(QpcFacts qpcFacts) {
    host.qpcFacts(qpcFacts);
    setPrimaryReporter("discovery");
    return this;
  }

  /** Set the system-profile fields (persisted to {@code hbi.system_profiles_static}). */
  public HostBuilder systemProfileFacts(SystemProfileFacts systemProfileFacts) {
    host.systemProfileFacts(systemProfileFacts);
    return this;
  }

  // ===== Override Methods (Always Override) =====

  public HostBuilder orgId(String orgId) {
    host.orgId(orgId);
    return this;
  }

  public HostBuilder displayName(String displayName) {
    host.displayName(displayName);
    return this;
  }

  public HostBuilder subscriptionManagerId(String subscriptionManagerId) {
    host.subscriptionManagerId(subscriptionManagerId);
    return this;
  }

  public HostBuilder providerId(String providerId) {
    host.providerId(providerId);
    return this;
  }

  // ===== Insert / Update (Final Actions) =====

  /**
   * Finalize and insert the host into HBI.
   *
   * <p>Calculates derived fields (cores_per_socket), synthesizes Yupana facts when a Satellite or
   * QPC reporter is present, and seeds via connector.
   *
   * @return information about the seeded host
   */
  public SeededHost insert() {
    applyDerivedFacts();

    // Seed via manager (which tracks the host)
    SeededHost seeded = manager.seed(host);
    host.id(seeded.hostId());
    return seeded;
  }

  /**
   * Update the previously-{@link #insert()}-ed host in place, using whatever is currently set on
   * this builder. Reuse the same {@link HostBuilder} instance across {@code insert()} and {@code
   * update()} calls - fact groups you don't re-set (e.g. Satellite facts, when only updating
   * system-profile facts) are preserved as-is, since they're still held on this builder's {@link
   * Host}.
   *
   * @return information about the updated host
   * @throws IllegalStateException if this builder's host has not been {@link #insert()}-ed yet
   */
  public SeededHost update() {
    if (host.getId() == null) {
      throw new IllegalStateException("Host must be insert()-ed before it can be update()-d");
    }

    applyDerivedFacts();

    return manager.update(host);
  }

  private void applyDerivedFacts() {
    if (host.getSatelliteFacts() != null || host.getQpcFacts() != null) {
      setYupanaFactsIfMissing();
    }

    SystemProfileFacts profile = host.getSystemProfileFacts();
    if (profile != null
        && profile.getNumberOfCpus() != null
        && profile.getNumberOfSockets() != null
        && profile.getNumberOfSockets() > 0) {
      int computedCoresPerSocket = profile.getNumberOfCpus() / profile.getNumberOfSockets();
      host.systemProfileFacts(profile.withComputedCoresPerSocket(computedCoresPerSocket));
    }
  }

  // ===== Reporter bookkeeping =====

  private void setPrimaryReporter(String reporterName) {
    if (Arrays.stream(host.getReporters()).noneMatch(reporterName::equals)) {
      host.reporters(
          Stream.concat(Stream.of(reporterName), Arrays.stream(host.getReporters()))
              .toArray(String[]::new));
    }
    host.reporter(reporterName);
  }

  private void setYupanaFactsIfMissing() {
    if (host.getYupanaFacts() != null) {
      return;
    }

    String source;
    if (host.getSatelliteFacts() != null) {
      source = "satellite";
    } else if (host.getQpcFacts() != null) {
      source = "discovery";
    } else {
      source = "";
    }

    host.yupanaFacts(
        YupanaFacts.builder()
            .orgId(host.getOrgId())
            .source(source)
            .account(host.getAccount() != null ? host.getAccount() : UUID.randomUUID().toString())
            .yupanaHostId(UUID.randomUUID().toString())
            .reportSliceId(UUID.randomUUID().toString())
            .reportPlatformId(UUID.randomUUID().toString())
            .build());
  }
}
