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
package com.redhat.swatch.hbi.events.normalization;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import io.quarkus.runtime.util.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class MeasurementNormalizer {

  public static final String OPEN_SHIFT_CONTAINER_PLATFORM = "OpenShift Container Platform";
  private static final double THREADS_PER_CORE_DEFAULT = 2.0;

  private final ApplicationConfiguration appConfig;

  public MeasurementNormalizer(ApplicationConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  public NormalizedMeasurements getMeasurements(
      NormalizedFacts facts,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      Set<String> productTags,
      boolean isHypervisor,
      boolean isUnmappedGuest) {
    NormalizedMeasurements measurements = new NormalizedMeasurements();
    measurements.setCores(normalizeCores(systemProfileFacts, productTags));
    measurements.setSockets(
        normalizeSockets(facts, systemProfileFacts, productTags, isHypervisor, isUnmappedGuest));

    normalizeUnits(facts, systemProfileFacts, rhsmFacts, measurements);
    return measurements;
  }

  private Integer normalizeCores(SystemProfileFacts systemProfileFacts, Set<String> productTags) {
    Integer applicableCores = getSystemProfileCores(systemProfileFacts, productTags);
    if (Boolean.TRUE.equals(systemProfileFacts.getIsMarketplace())) {
      return 0;
    }

    return applicableCores;
  }

  private Integer normalizeSockets(
      NormalizedFacts normalizedFacts,
      SystemProfileFacts systemProfileFacts,
      Set<String> productTags,
      boolean isHypervisor,
      boolean isUnmappedGuest) {
    Integer applicableSockets = getSystemProfileSockets(systemProfileFacts);
    applicableSockets =
        normalizeSocketCount(
            applicableSockets,
            normalizedFacts,
            systemProfileFacts,
            productTags,
            isHypervisor,
            isUnmappedGuest);
    if (Boolean.TRUE.equals(systemProfileFacts.getIsMarketplace())) {
      return 0;
    }

    return applicableSockets;
  }

  private Integer getSystemProfileCores(
      SystemProfileFacts systemProfileFacts, Set<String> productTags) {

    // Only calculate vCPUs for x86 virtual guests
    if ("x86_64".equals(systemProfileFacts.getArch())
        && HardwareMeasurementType.VIRTUAL
            .toString()
            .equalsIgnoreCase(systemProfileFacts.getInfrastructureType())) {
      return calculateVirtualCPU(productTags, systemProfileFacts);
    }

    // Physical or other virtual types: use basic formula, but only if both values are present
    Integer spSockets = systemProfileFacts.getSockets();
    Integer spCoresPerSocket = systemProfileFacts.getCoresPerSocket();

    if (spSockets != null && spSockets != 0 && spCoresPerSocket != null && spCoresPerSocket != 0) {
      return spCoresPerSocket * spSockets;
    }

    return null;
  }

  private Integer getSystemProfileSockets(SystemProfileFacts systemProfileFacts) {
    Integer spSockets = systemProfileFacts.getSockets();
    return (spSockets != null && spSockets != 0) ? spSockets : null;
  }

  private Integer normalizeSocketCount(
      Integer currentCalculatedSockets,
      NormalizedFacts normalizedFacts,
      SystemProfileFacts systemProfileFacts,
      Set<String> productTags,
      boolean isHypervisor,
      boolean isUnmappedGuest) {

    // modulo-2 rounding only applied to physical or hypervisors
    if (!normalizedFacts.isVirtual() || isHypervisor) {
      if (currentCalculatedSockets != null && (currentCalculatedSockets % 2) == 1) {
        return currentCalculatedSockets + 1;
      }
    } else {
      // Cloud provider hosts only account for a single socket.
      if (Optional.ofNullable(normalizedFacts.getCloudProviderType()).isPresent()) {
        return Boolean.TRUE.equals(systemProfileFacts.getIsMarketplace()) ? 0 : 1;
      }

      boolean guestWithUnknownHypervisor = normalizedFacts.isVirtual() && isUnmappedGuest;
      // Unmapped virtual rhel guests only account for a single socket
      if (guestWithUnknownHypervisor
          && productTags.stream().anyMatch(prod -> startsWithIgnoreCase(prod, "RHEL"))) {
        return 1;
      }
    }
    return currentCalculatedSockets;
  }

  private Integer calculateVirtualCPU(Set<String> products, SystemProfileFacts systemProfileFacts) {
    //  For x86, guests: if we know the number of threads per core and is greater than one,
    //  then we divide the number of cores by that number.
    //  Otherwise, we divide by two.
    Integer coresPerSocket = systemProfileFacts.getCoresPerSocket();
    Integer sockets = systemProfileFacts.getSockets();

    if (coresPerSocket == null || sockets == null) {
      log.warn(
          "Missing coresPerSocket ({}) or sockets ({}). Returning null.", coresPerSocket, sockets);
      return null;
    }

    int cpu = coresPerSocket * sockets;
    double threadsPerCore = THREADS_PER_CORE_DEFAULT;

    if (appConfig.isUseCpuSystemFactsForAllProducts()
        || products.contains(OPEN_SHIFT_CONTAINER_PLATFORM)) {

      Integer systemThreadsPerCore = systemProfileFacts.getThreadsPerCore();
      if (isGreaterThanZero(systemThreadsPerCore)) {
        threadsPerCore = systemThreadsPerCore;

        if (threadsPerCore != THREADS_PER_CORE_DEFAULT) {
          log.warn(
              "Using '{}' threads per core from system profile for products '{}' to calculate vCPUs",
              threadsPerCore,
              String.join(", ", products));
        }

      } else if (isGreaterThanZero(systemProfileFacts.getCpus(), sockets, coresPerSocket)) {
        threadsPerCore = (double) systemProfileFacts.getCpus() / (sockets * coresPerSocket);

        if (threadsPerCore != THREADS_PER_CORE_DEFAULT) {
          log.warn(
              "Using '{}' threads per core from derived formula: cpus={} / (sockets={} * coresPerSocket={}) for products '{}'",
              threadsPerCore,
              systemProfileFacts.getCpus(),
              sockets,
              coresPerSocket,
              String.join(", ", products));
        }
      }
    }
    return (int) Math.ceil(cpu / threadsPerCore);
  }

  private void normalizeUnits(
      NormalizedFacts normalizedFacts,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      NormalizedMeasurements measurements) {
    String systemPurposeUnits = rhsmFacts.map(RhsmFacts::getSystemPurposeUnits).orElse(null);
    if (systemPurposeUnits == null) {
      return;
    }
    switch (systemPurposeUnits) {
      case "Sockets":
        measurements.setCores(null);
        if (measurements.getSockets().isEmpty()) {
          measurements.setSockets(systemProfileFacts.getSockets());
        }
        break;
      case "Cores/vCPU":
        measurements.setSockets(null);
        if (measurements.getCores().isEmpty()) {
          measurements.setCores(systemProfileFacts.getCoresPerSocket());
        }
        break;
      default:
        log.warn(
            "Unsupported value on host w/ subscription-manager ID {} for syspurpose units: {}",
            normalizedFacts.getSubscriptionManagerId(),
            systemPurposeUnits);
    }
  }

  private boolean isGreaterThanZero(Integer... values) {
    return Arrays.stream(values).allMatch(value -> value != null && value > 0);
  }

  private boolean startsWithIgnoreCase(String value, String match) {
    return !StringUtil.isNullOrEmpty(value) && value.toUpperCase().startsWith(match.toUpperCase());
  }
}
