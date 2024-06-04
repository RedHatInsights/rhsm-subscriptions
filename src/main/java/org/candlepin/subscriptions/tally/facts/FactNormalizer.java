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
package org.candlepin.subscriptions.tally.facts;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.tally.OrgHostsData;
import org.springframework.util.StringUtils;

/**
 * Responsible for examining an inventory host and producing normalized and condensed facts based on
 * the host's facts.
 */
@Slf4j
public class FactNormalizer {

  private static final String OPEN_SHIFT_CONTAINER_PLATFORM = "OpenShift Container Platform";
  private static final double THREADS_PER_CORE_DEFAULT = 2.0;

  private final ApplicationClock clock;
  private final ApplicationProperties props;
  private final ProductNormalizer productNormalizer;

  public FactNormalizer(
      ApplicationProperties props, ApplicationClock clock, ProductNormalizer productNormalizer) {
    this.clock = clock;
    this.props = props;
    this.productNormalizer = productNormalizer;
    log.info("rhsm-conduit stale threshold: {}", props.getHostLastSyncThreshold());
  }

  public static boolean isRhelVariant(String product) {
    return product.startsWith("RHEL ") && !product.startsWith("RHEL for ");
  }

  /**
   * Normalize the FactSets of the given host.
   *
   * @param hostFacts the collection of facts to normalize.
   * @param guestData
   * @param isMetered
   * @return a normalized version of the host's facts.
   */
  public NormalizedFacts normalize(
      InventoryHostFacts hostFacts, OrgHostsData guestData, boolean isMetered) {

    // If the host hasn't been seen by rhsm-conduit, consider the host as unregistered, and do not
    // apply this host's facts.
    // NOTE: This logic is applied since currently the inventory service does not prune inventory
    // records once a host no longer exists.
    var syncTimestampOptional = Optional.ofNullable(hostFacts.getSyncTimestamp());
    boolean skipRhsmFacts =
        syncTimestampOptional
            .map(
                syncTimestamp ->
                    StringUtils.hasText(syncTimestamp)
                        && hostUnregistered(OffsetDateTime.parse(syncTimestamp)))
            .orElse(false);

    NormalizedFacts normalizedFacts = new NormalizedFacts();

    normalizedFacts.setProducts(
        productNormalizer.normalizeProducts(hostFacts, isMetered, skipRhsmFacts));

    normalizeClassification(normalizedFacts, hostFacts, guestData);
    normalizeHardwareType(normalizedFacts, hostFacts);
    normalizeSystemProfileFacts(normalizedFacts, hostFacts);
    normalizeSatelliteFacts(normalizedFacts, hostFacts);
    if (!skipRhsmFacts) {
      normalizeRhsmFacts(normalizedFacts, hostFacts);
    }
    normalizeSocketCount(normalizedFacts, hostFacts);
    normalizeMarketplace(normalizedFacts, hostFacts);
    normalizeNullSocketsAndCores(normalizedFacts, hostFacts);
    normalizeUnits(normalizedFacts, hostFacts);
    return normalizedFacts;
  }

  private void normalizeSatelliteFacts(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {

    handleSla(normalizedFacts, hostFacts, hostFacts.getSatelliteSla());
    handleUsage(normalizedFacts, hostFacts, hostFacts.getSatelliteUsage());
  }

  private void normalizeUnits(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    if (hostFacts.getSyspurposeUnits() == null) {
      return;
    }
    switch (hostFacts.getSyspurposeUnits()) {
      case "Sockets":
        normalizedFacts.setCores(null);
        if (normalizedFacts.getSockets() == null) {
          normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
        }
        break;
      case "Cores/vCPU":
        normalizedFacts.setSockets(null);
        if (normalizedFacts.getCores() == null) {
          normalizedFacts.setCores(hostFacts.getSystemProfileCoresPerSocket());
        }
        break;
      default:
        log.warn(
            "Unsupported value on host w/ subscription-manager ID {} for syspurpose units: {}",
            hostFacts.getSubscriptionManagerId(),
            hostFacts.getSyspurposeUnits());
    }
  }

  private boolean isVirtual(InventoryHostFacts hostFacts) {
    return hostFacts.isVirtual()
        || StringUtils.hasText(hostFacts.getSatelliteHypervisorUuid())
        || "virtual".equalsIgnoreCase(hostFacts.getSystemProfileInfrastructureType());
  }

  private void normalizeClassification(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts, OrgHostsData orgHostsData) {
    boolean isVirtual = isVirtual(hostFacts);

    String hypervisorUuid = hostFacts.getSatelliteHypervisorUuid();
    if (!StringUtils.hasText(hypervisorUuid)) {
      hypervisorUuid = hostFacts.getHypervisorUuid();
    }
    if (StringUtils.hasText(hypervisorUuid)) {
      normalizedFacts.setHypervisorUuid(hypervisorUuid);
    }

    boolean isHypervisorUnknown =
        (isVirtual && !StringUtils.hasText(hypervisorUuid))
            || orgHostsData.isUnmappedHypervisor(hypervisorUuid);
    normalizedFacts.setHypervisorUnknown(isHypervisorUnknown);

    boolean isHypervisor =
        StringUtils.hasText(hostFacts.getSubscriptionManagerId())
            && orgHostsData.hasHypervisorUuid(hostFacts.getSubscriptionManagerId());
    normalizedFacts.setHypervisor(isHypervisor);
  }

  private void normalizeHardwareType(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    var hardwareType = HostHardwareType.PHYSICAL;
    if (HardwareMeasurementType.isSupportedCloudProvider(hostFacts.getCloudProvider())) {
      hardwareType = HostHardwareType.CLOUD;
    } else if (isVirtual(hostFacts)) {
      hardwareType = HostHardwareType.VIRTUALIZED;
    }
    normalizedFacts.setHardwareType(hardwareType);
  }

  private void normalizeSocketCount(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    // modulo-2 rounding only applied to physical or hypervisors
    if (normalizedFacts.isHypervisor() || !isVirtual(hostFacts)) {
      Integer sockets = normalizedFacts.getSockets();
      if (sockets != null && (sockets % 2) == 1) {
        normalizedFacts.setSockets(sockets + 1);
      }
    } else {
      boolean guestWithUnknownHypervisor =
          normalizedFacts.isVirtual() && normalizedFacts.isHypervisorUnknown();
      // Cloud provider hosts only account for a single socket.
      if (normalizedFacts.getCloudProviderType() != null) {
        var sockets = normalizedFacts.isMarketplace() ? 0 : 1;
        normalizedFacts.setSockets(sockets);
      }
      // Unmapped virtual rhel guests only account for a single socket
      else if (guestWithUnknownHypervisor
          && normalizedFacts.getProducts().stream()
              .anyMatch(prod -> StringUtils.startsWithIgnoreCase(prod, "RHEL"))) {
        normalizedFacts.setSockets(1);
      }
    }
  }

  private void normalizeSystemProfileFacts(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    String cloudProvider = hostFacts.getCloudProvider();
    if (HardwareMeasurementType.isSupportedCloudProvider(cloudProvider)) {
      normalizedFacts.setCloudProviderType(HardwareMeasurementType.fromString(cloudProvider));
    }
    if (hostFacts.getSystemProfileSockets() != null && hostFacts.getSystemProfileSockets() != 0) {
      normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
    }
    if (hostFacts.getSystemProfileSockets() != 0
        && hostFacts.getSystemProfileSockets() != null
        && hostFacts.getSystemProfileCoresPerSocket() != 0
        && hostFacts.getSystemProfileCoresPerSocket() != null) {
      normalizedFacts.setCores(
          hostFacts.getSystemProfileCoresPerSocket() * hostFacts.getSystemProfileSockets());
    }

    if ("x86_64".equals(hostFacts.getSystemProfileArch())
        && HardwareMeasurementType.VIRTUAL
            .toString()
            .equalsIgnoreCase(hostFacts.getSystemProfileInfrastructureType())) {
      var effectiveCores = calculateVirtualCPU(normalizedFacts.getProducts(), hostFacts);
      normalizedFacts.setCores(effectiveCores);
    }
  }

  private void normalizeMarketplace(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    boolean isMarketplace = hostFacts.isMarketplace();
    normalizedFacts.setMarketplace(isMarketplace);

    if (isMarketplace) {
      normalizedFacts.setCores(0);
      normalizedFacts.setSockets(0);
    }
  }

  private void normalizeNullSocketsAndCores(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    if (normalizedFacts.getCores() == null && hostFacts.getSystemProfileCoresPerSocket() != 0) {
      normalizedFacts.setCores(hostFacts.getSystemProfileCoresPerSocket());
    }
    if (normalizedFacts.getSockets() == null && hostFacts.getSystemProfileSockets() != 0) {
      normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
    }
  }

  private Integer calculateVirtualCPU(Set<String> products, InventoryHostFacts virtualFacts) {
    //  For x86, guests: if we know the number of threads per core and is greater than one,
    //  then we divide the number of cores by that number.
    //  Otherwise, we divide by two.
    int cpu =
        virtualFacts.getSystemProfileCoresPerSocket() * virtualFacts.getSystemProfileSockets();

    var threadsPerCore = THREADS_PER_CORE_DEFAULT;
    if (props.isUseCpuSystemFactsToAllProducts()
        || products.contains(OPEN_SHIFT_CONTAINER_PLATFORM)) {
      if (isGreaterThanZero(virtualFacts.getSystemProfileThreadsPerCore())) {
        threadsPerCore = virtualFacts.getSystemProfileThreadsPerCore();

        if (threadsPerCore != THREADS_PER_CORE_DEFAULT) {
          log.warn(
              "Using '{}' threads per core from system profile for products '{}' to calculate vCPUs",
              threadsPerCore,
              String.join(", ", products));
        }

      } else if (isGreaterThanZero(
          virtualFacts.getSystemProfileCpus(),
          virtualFacts.getSystemProfileSockets(),
          virtualFacts.getSystemProfileCoresPerSocket())) {
        threadsPerCore =
            (double) virtualFacts.getSystemProfileCpus()
                / (virtualFacts.getSystemProfileSockets()
                    * virtualFacts.getSystemProfileCoresPerSocket());
        if (threadsPerCore != THREADS_PER_CORE_DEFAULT) {
          log.warn(
              "Using '{}' threads per core from formula 'number of cpus as {}' / ('number of sockets as {}' * 'cores per socket as {}') profile for products '{}' to calculate vCPUs",
              threadsPerCore,
              virtualFacts.getSystemProfileCpus(),
              virtualFacts.getSystemProfileSockets(),
              virtualFacts.getSystemProfileCoresPerSocket(),
              String.join(", ", products));
        }
      }
    }

    return (int) Math.ceil(cpu / threadsPerCore);
  }

  private void normalizeRhsmFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {

    // Check for cores and sockets. If not included, default to 0.
    normalizedFacts.setOrgId(hostFacts.getOrgId());
    handleSla(normalizedFacts, hostFacts, hostFacts.getSyspurposeSla());
    handleUsage(normalizedFacts, hostFacts, hostFacts.getSyspurposeUsage());
  }

  private void handleUsage(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts, String usage) {
    Usage effectiveUsage = Usage.fromString(usage);
    if (usage != null && effectiveUsage == Usage.EMPTY && log.isDebugEnabled()) {

      log.debug(
          "OrgId {} host {} has unsupported value for Usage: {}",
          hostFacts.getOrgId(),
          hostFacts.getSubscriptionManagerId(),
          usage);
    }
    if (effectiveUsage != Usage.EMPTY) {
      normalizedFacts.setUsage(effectiveUsage);
    }
  }

  private void handleSla(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts, String sla) {
    ServiceLevel effectiveSla = ServiceLevel.fromString(sla);
    if (sla != null && effectiveSla == ServiceLevel.EMPTY && log.isDebugEnabled()) {

      log.debug(
          "OrgId {} host {} has unsupported value for SLA: {}",
          hostFacts.getOrgId(),
          hostFacts.getSubscriptionManagerId(),
          sla);
    }
    if (effectiveSla != ServiceLevel.EMPTY) {
      normalizedFacts.setSla(effectiveSla);
    }
  }

  /**
   * A host is considered unregistered if the last time it was synced passes the configured number
   * of hours.
   *
   * <p>NOTE: If the passed lastSync date is null, it is considered to be registered.
   *
   * @param lastSync the last known time that a host sync occured from pinhead to conduit.
   * @return true if the host is considered unregistered, false otherwise.
   */
  private boolean hostUnregistered(OffsetDateTime lastSync) {
    // If last sync is not present, consider it a registered host.
    if (lastSync == null) {
      return false;
    }
    // NOTE: sync threshold is relative to conduit schedule - i.e. midnight UTC
    // otherwise the sync threshold would be offset by the tally schedule, which would be confusing
    return lastSync.isBefore(clock.startOfToday().minus(props.getHostLastSyncThreshold()));
  }

  private boolean isGreaterThanZero(Integer... values) {
    return Arrays.stream(values).allMatch(value -> value != null && value > 0);
  }
}
