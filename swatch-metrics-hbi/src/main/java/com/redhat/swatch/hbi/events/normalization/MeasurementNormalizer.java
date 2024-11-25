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
import com.redhat.swatch.hbi.events.normalization.facts.HostFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.services.HypervisorRelationshipService;
import io.quarkus.runtime.util.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class MeasurementNormalizer {

  private static final String OPEN_SHIFT_CONTAINER_PLATFORM = "OpenShift Container Platform";
  private static final double THREADS_PER_CORE_DEFAULT = 2.0;

  private final ApplicationConfiguration appConfig;
  private final HypervisorRelationshipService hypervisorRelationshipService;

  public MeasurementNormalizer(
      ApplicationConfiguration appConfig,
      HypervisorRelationshipService hypervisorRelationshipService) {
    this.appConfig = appConfig;
    this.hypervisorRelationshipService = hypervisorRelationshipService;
  }

  public NormalizedMeasurements getMeasurements(
      HostFacts facts,
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      ProductNormalizer products) {
    NormalizedMeasurements measurements = new NormalizedMeasurements();
    measurements.setCores(normalizeCores(systemProfileFacts, products));
    measurements.setSockets(normalizeSockets(facts, systemProfileFacts, products));

    normalizeUnits(facts, systemProfileFacts, rhsmFacts, measurements);
    return measurements;
  }

  private Integer normalizeCores(
      SystemProfileFacts systemProfileFacts, ProductNormalizer products) {
    Integer applicableCores = getSystemProfileCores(systemProfileFacts, products);
    if (Boolean.TRUE.equals(systemProfileFacts.getIsMarketplace())) {
      applicableCores = 0;
    }
    return normalizeNullMetric(applicableCores, systemProfileFacts.getCoresPerSocket());
  }

  private Integer normalizeSockets(
      HostFacts facts, SystemProfileFacts systemProfileFacts, ProductNormalizer products) {
    // noralizeSocketCount
    // normalizeMarketplace
    // normalizeNullSocketsAndCores
    Integer applicableSockets = getSystemProfileSockets(systemProfileFacts);
    applicableSockets =
        normalizeSocketCount(applicableSockets, facts, systemProfileFacts, products);
    if (Boolean.TRUE.equals(systemProfileFacts.getIsMarketplace())) {
      applicableSockets = 0;
    }
    return normalizeNullMetric(applicableSockets, systemProfileFacts.getSockets());
  }

  private Integer getSystemProfileCores(
      SystemProfileFacts systemProfileFacts, ProductNormalizer products) {
    Integer spSockets = systemProfileFacts.getSockets();
    Integer spCoresPerSocket = systemProfileFacts.getCoresPerSocket();

    Integer applicableCores = null;
    if (spSockets != null && spSockets != 0 && spCoresPerSocket != null && spCoresPerSocket != 0) {
      applicableCores = spCoresPerSocket * spSockets;
    }

    if ("x86_64".equals(systemProfileFacts.getArch())
        && HardwareMeasurementType.VIRTUAL
            .toString()
            .equalsIgnoreCase(systemProfileFacts.getInfrastructureType())) {
      applicableCores = calculateVirtualCPU(products.getProductTags(), systemProfileFacts);
    }
    return applicableCores;
  }

  private Integer getSystemProfileSockets(SystemProfileFacts systemProfileFacts) {
    Integer spSockets = systemProfileFacts.getSockets();
    return spSockets != null && spSockets != 0 ? spSockets : null;
  }

  private Integer normalizeSocketCount(
      Integer currentCalculatedSockets,
      HostFacts normalizedFacts,
      SystemProfileFacts systemProfileFacts,
      ProductNormalizer products) {
    // modulo-2 rounding only applied to physical or hypervisors
    boolean isHypervisor =
        hypervisorRelationshipService.isHypervisor(normalizedFacts.getSubscriptionManagerId());
    if (isHypervisor || !normalizedFacts.isVirtual()) {
      if (currentCalculatedSockets != null && (currentCalculatedSockets % 2) == 1) {
        return currentCalculatedSockets + 1;
      }
    } else {
      boolean isHypervisorUnknown =
          hypervisorRelationshipService.isUnmappedGuest(normalizedFacts.getHypervisorUuid());
      boolean guestWithUnknownHypervisor = normalizedFacts.isVirtual() && isHypervisorUnknown;
      // Cloud provider hosts only account for a single socket.
      if (Optional.ofNullable(normalizedFacts.getCloudProviderType()).isPresent()) {
        // TODO Rework this comparison; sonar was complaining otherwise.
        return Boolean.TRUE.equals(systemProfileFacts.getIsMarketplace()) ? 0 : 1;
      }
      // Unmapped virtual rhel guests only account for a single socket
      else if (guestWithUnknownHypervisor
          && products.getProductTags().stream()
              .anyMatch(prod -> startsWithIgnoreCase(prod, "RHEL"))) {
        return 1;
      }
    }
    return currentCalculatedSockets;
  }

  private Integer calculateVirtualCPU(Set<String> products, SystemProfileFacts systemProfileFacts) {
    //  For x86, guests: if we know the number of threads per core and is greater than one,
    //  then we divide the number of cores by that number.
    //  Otherwise, we divide by two.
    int cpu = systemProfileFacts.getCoresPerSocket() * systemProfileFacts.getSockets();

    var threadsPerCore = THREADS_PER_CORE_DEFAULT;
    if (appConfig.isUseCpuSystemFactsForAllProducts()
        || products.contains(OPEN_SHIFT_CONTAINER_PLATFORM)) {
      if (isGreaterThanZero(systemProfileFacts.getThreadsPerCore())) {
        threadsPerCore = systemProfileFacts.getThreadsPerCore();

        if (threadsPerCore != THREADS_PER_CORE_DEFAULT) {
          log.warn(
              "Using '{}' threads per core from system profile for products '{}' to calculate vCPUs",
              threadsPerCore,
              String.join(", ", products));
        }
      } else if (isGreaterThanZero(
          systemProfileFacts.getCpus(),
          systemProfileFacts.getSockets(),
          systemProfileFacts.getCoresPerSocket())) {
        threadsPerCore =
            (double) systemProfileFacts.getCpus()
                / (systemProfileFacts.getSockets() * systemProfileFacts.getCoresPerSocket());
        if (threadsPerCore != THREADS_PER_CORE_DEFAULT) {
          log.warn(
              "Using '{}' threads per core from formula 'number of cpus as {}' / ('number of sockets as {}' * 'cores per socket as {}') profile for products '{}' to calculate vCPUs",
              threadsPerCore,
              systemProfileFacts.getCpus(),
              systemProfileFacts.getSockets(),
              systemProfileFacts.getCoresPerSocket(),
              String.join(", ", products));
        }
      }
    }
    return (int) Math.ceil(cpu / threadsPerCore);
  }

  private void normalizeUnits(
      HostFacts normalizedFacts,
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

  private Integer normalizeNullMetric(Integer currentValue, Integer replacementValue) {
    return currentValue == null && replacementValue != null && replacementValue != 0
        ? replacementValue
        : currentValue;
  }

  private boolean startsWithIgnoreCase(String value, String match) {
    return !StringUtil.isNullOrEmpty(value) && value.toUpperCase().startsWith(match.toUpperCase());
  }
}
