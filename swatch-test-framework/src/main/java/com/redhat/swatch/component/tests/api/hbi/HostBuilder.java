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
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Builder for applying reporter facts and customizations before inserting a {@link Host}.
 *
 * <p>Provides template methods for common infrastructure configurations and setters for attaching
 * typed reporter facts ({@link RhsmFacts}, {@link SatelliteFacts}, {@link QpcFacts}) and {@link
 * SystemProfileFacts}. Attaching a reporter's facts also registers that reporter on the host.
 */
public class HostBuilder {
  private final HostStateManager manager;
  private final Host host;

  HostBuilder(HostStateManager manager, Host host) {
    this.manager = manager;
    this.host = host;
  }

  // ===== Physical RHEL Templates =====

  /** Apply physical RHEL defaults (infrastructure=physical, IS_VIRTUAL=false, RHEL product). */
  public HostBuilder physicalRhelDefaults() {
    rhsmFacts(RhsmFacts.builder().defaultFacts().build());
    setSystemProfileFacts(
        SystemProfileFacts.builder().infrastructureType("physical").arch("x86_64").build());
    return this;
  }

  /** Physical RHEL host with the given socket/core counts. */
  public HostBuilder physicalRhel(int sockets, int cores) {
    physicalRhelDefaults();
    setSystemProfileFacts(
        host.getSystemProfileFacts().toBuilder()
            .numberOfSockets(sockets)
            .numberOfCpus(cores)
            .build());
    displayName(
        getOrDefault(host.getDisplayName(), generateName("physical", "RHEL", sockets, cores)));
    return this;
  }

  /** Physical RHEL host with 1 socket, 0 cores. */
  public HostBuilder physicalRhel1Socket0Cores() {
    return physicalRhel(1, 0);
  }

  /** Physical RHEL host with 2 sockets, 2 cores. */
  public HostBuilder physicalRhel2Socket2Cores() {
    return physicalRhel(2, 2);
  }

  /** Physical RHEL host with 8 sockets, 8 cores. */
  public HostBuilder physicalRhel8Sockets8Cores() {
    return physicalRhel(8, 8);
  }

  // ===== AWS RHEL Templates =====

  /** Apply AWS RHEL defaults (infrastructure=virtual, cloudProvider=aws, RHEL product). */
  public HostBuilder awsRhelDefaults() {
    rhsmFacts(RhsmFacts.builder().defaultFacts().isVirtual(true).build());
    setSystemProfileFacts(
        SystemProfileFacts.builder()
            .infrastructureType("virtual")
            .cloudProvider("aws")
            .arch("x86_64")
            .build());
    providerId("i-test-" + getUUIDOfLength(12));
    return this;
  }

  /** AWS RHEL host with the given socket/core counts. */
  public HostBuilder awsRhel(int sockets, int cores) {
    awsRhelDefaults();
    setSystemProfileFacts(
        host.getSystemProfileFacts().toBuilder()
            .numberOfSockets(sockets)
            .numberOfCpus(cores)
            .build());
    displayName(
        getOrDefault(host.getDisplayName(), generateName("virtual", "RHEL", sockets, cores)));
    return this;
  }

  /** AWS RHEL host - small instance (t3.medium equivalent). */
  public HostBuilder awsRhelSockets1Cores1() {
    return awsRhel(1, 1);
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
  public HostBuilder setSystemProfileFacts(SystemProfileFacts systemProfileFacts) {
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

  // ===== Insert (Final Action) =====

  /**
   * Finalize and insert the host into HBI.
   *
   * <p>Calculates derived fields (cores_per_socket), synthesizes Yupana facts when a Satellite or
   * QPC reporter is present, and seeds via connector.
   *
   * @return information about the seeded host
   */
  public SeededHost insert() {
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

    // Seed via manager (which tracks the host)
    return manager.seed(host);
  }

  private void setPrimaryReporter(String reporterName) {
    if (Arrays.stream(host.getReporters()).noneMatch(reporterName::equals)) {
      host.reporters(
          Stream.concat(Stream.of(reporterName), Arrays.stream(host.getReporters()))
              .toArray(String[]::new));
    }
    host.reporter(reporterName);
  }

  private String getOrDefault(String value, String defaultValue) {
    return value != null ? value : defaultValue;
  }

  private String getUUIDOfLength(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  private String generateName(
      String infrastType, String instanceType, int socketCount, int coreCount) {
    String prefix =
        StringUtils.capitalize(infrastType.substring(0, Math.min(3, infrastType.length())));
    return String.format(
        "%s-%s-sockets%d-cores%d-%s",
        prefix, instanceType, socketCount, coreCount, getUUIDOfLength(5));
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
