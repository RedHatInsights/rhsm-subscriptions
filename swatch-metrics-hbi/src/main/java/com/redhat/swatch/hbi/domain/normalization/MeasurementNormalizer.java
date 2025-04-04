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
package com.redhat.swatch.hbi.domain.normalization;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.hbi.config.ApplicationProperties;
import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.SystemProfileFacts;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class MeasurementNormalizer {

  public static final String OPEN_SHIFT_CONTAINER_PLATFORM = "OpenShift Container Platform";
  private static final double DEFAULT_THREADS_PER_CORE = 2.0;

  private final ApplicationProperties applicationProperties;

  public MeasurementNormalizer(ApplicationProperties applicationProperties) {
    this.applicationProperties = applicationProperties;
  }

  public NormalizedMeasurements getMeasurements(
      NormalizedFacts facts,
      SystemProfileFacts systemProfile,
      Optional<RhsmFacts> rhsmFacts,
      Set<String> productTags,
      boolean isHypervisor,
      boolean isUnmappedGuest) {

    NormalizedMeasurements measurements = new NormalizedMeasurements();

    measurements.setCores(determineCores(systemProfile, productTags));
    measurements.setSockets(
        determineSockets(facts, systemProfile, productTags, isHypervisor, isUnmappedGuest));

    applySystemPurposeOverrides(
        systemProfile, rhsmFacts, measurements, facts.getSubscriptionManagerId());

    return measurements;
  }

  private Integer determineCores(SystemProfileFacts systemProfile, Set<String> productTags) {
    Integer calculatedCores = calculateCoresFromProfile(systemProfile, productTags);

    if (Boolean.TRUE.equals(systemProfile.getIsMarketplace())) {
      return 0;
    }

    return fallbackIfNull(calculatedCores, systemProfile.getCoresPerSocket());
  }

  private Integer determineSockets(
      NormalizedFacts facts,
      SystemProfileFacts systemProfile,
      Set<String> productTags,
      boolean isHypervisor,
      boolean isUnmappedGuest) {

    Integer rawSockets = fallbackIfZero(systemProfile.getSockets());

    Integer normalized =
        normalizeSocketCount(
            rawSockets, facts, systemProfile, productTags, isHypervisor, isUnmappedGuest);

    if (Boolean.TRUE.equals(systemProfile.getIsMarketplace())) {
      return 0;
    }

    return fallbackIfNull(normalized, systemProfile.getSockets());
  }

  private Integer calculateCoresFromProfile(
      SystemProfileFacts systemProfile, Set<String> productTags) {
    Integer cores = null;

    if (isPositive(systemProfile.getSockets()) && isPositive(systemProfile.getCoresPerSocket())) {
      cores = systemProfile.getSockets() * systemProfile.getCoresPerSocket();
    }

    boolean isVirtualX86 =
        "x86_64".equals(systemProfile.getArch())
            && HardwareMeasurementType.VIRTUAL
                .toString()
                .equalsIgnoreCase(systemProfile.getInfrastructureType());

    if (isVirtualX86) {
      cores = estimateVirtualCores(productTags, systemProfile);
    }

    return cores;
  }

  private Integer normalizeSocketCount(
      Integer sockets,
      NormalizedFacts facts,
      SystemProfileFacts systemProfile,
      Set<String> productTags,
      boolean isHypervisor,
      boolean isUnmappedGuest) {

    if (!facts.isVirtual() || isHypervisor) {
      if (isOdd(sockets)) return sockets + 1;
    }

    if (facts.getCloudProviderType() != null) {
      return Boolean.TRUE.equals(systemProfile.getIsMarketplace()) ? 0 : 1;
    }

    if (facts.isVirtual() && isUnmappedGuest && containsRhelProduct(productTags)) {
      return 1;
    }

    return sockets;
  }

  private Integer estimateVirtualCores(Set<String> products, SystemProfileFacts profile) {
    int logicalCpus = profile.getCoresPerSocket() * profile.getSockets();
    double threadsPerCore = DEFAULT_THREADS_PER_CORE;

    if (applicationProperties.isUseCpuSystemFactsForAllProducts()
        || products.contains(OPEN_SHIFT_CONTAINER_PLATFORM)) {
      if (isPositive(profile.getThreadsPerCore())) {
        threadsPerCore = profile.getThreadsPerCore();
        logThreadOverride(threadsPerCore, products);
      } else if (isPositive(profile.getCpus(), profile.getSockets(), profile.getCoresPerSocket())) {
        threadsPerCore =
            (double) profile.getCpus() / (profile.getSockets() * profile.getCoresPerSocket());
        logThreadFallback(threadsPerCore, profile, products);
      }
    }

    return (int) Math.ceil(logicalCpus / threadsPerCore);
  }

  private void applySystemPurposeOverrides(
      SystemProfileFacts systemProfile,
      Optional<RhsmFacts> rhsmFacts,
      NormalizedMeasurements measurements,
      String subscriptionManagerId) {

    String units = rhsmFacts.map(RhsmFacts::getSystemPurposeUnits).orElse(null);
    if (units == null) return;

    switch (units) {
      case "Sockets":
        measurements.setCores(null);
        measurements
            .getSockets()
            .ifPresentOrElse(
                sockets -> {}, () -> measurements.setSockets(systemProfile.getSockets()));
        break;
      case "Cores/vCPU":
        measurements.setSockets(null);
        measurements
            .getCores()
            .ifPresentOrElse(
                cores -> {}, () -> measurements.setCores(systemProfile.getCoresPerSocket()));
        break;
      default:
        log.warn(
            "Unsupported syspurpose unit '{}' on host with subscription-manager ID {}",
            units,
            subscriptionManagerId);
    }
  }

  // ——— Utility Methods ———

  private boolean isOdd(Integer value) {
    return value != null && value % 2 == 1;
  }

  private boolean isPositive(Integer... values) {
    return Arrays.stream(values).allMatch(val -> val != null && val > 0);
  }

  private boolean containsRhelProduct(Set<String> tags) {
    return tags.stream().anyMatch(tag -> startsWithIgnoreCase(tag, "RHEL"));
  }

  private boolean startsWithIgnoreCase(String value, String prefix) {
    return value != null && value.toLowerCase().startsWith(prefix.toLowerCase());
  }

  private Integer fallbackIfNull(Integer primary, Integer fallback) {
    return primary == null && fallback != null && fallback != 0 ? fallback : primary;
  }

  private Integer fallbackIfZero(Integer value) {
    return value != null && value == 0 ? null : value;
  }

  private void logThreadOverride(double threadsPerCore, Set<String> products) {
    if (threadsPerCore != DEFAULT_THREADS_PER_CORE) {
      log.warn(
          "Using '{}' threads per core from system profile for products: {}",
          threadsPerCore,
          String.join(", ", products));
    }
  }

  private void logThreadFallback(
      double threadsPerCore, SystemProfileFacts profile, Set<String> products) {
    if (threadsPerCore != DEFAULT_THREADS_PER_CORE) {
      log.warn(
          "Fallback calculation: threadsPerCore='{}' from cpus={}, sockets={}, cores/socket={} — products: {}",
          threadsPerCore,
          profile.getCpus(),
          profile.getSockets(),
          profile.getCoresPerSocket(),
          String.join(", ", products));
    }
  }
}
