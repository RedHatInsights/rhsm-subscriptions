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

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.HostHardwareType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.inventory.db.model.InventoryHostFacts;
import org.candlepin.subscriptions.registry.TagProfile;
import org.candlepin.subscriptions.tally.OrgHostsData;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Responsible for examining an inventory host and producing normalized and condensed facts based on
 * the host's facts.
 */
public class FactNormalizer {

  private static final Logger log = LoggerFactory.getLogger(FactNormalizer.class);

  private final ApplicationClock clock;
  private final Duration hostSyncThreshold;
  private final Map<Integer, Set<String>> engProductIdToSwatchProductIdsMap;
  private final Map<String, Set<String>> roleToProductsMap;

  public FactNormalizer(
      ApplicationProperties props, TagProfile tagProfile, ApplicationClock clock) {
    this.clock = clock;
    this.hostSyncThreshold = props.getHostLastSyncThreshold();
    log.info("rhsm-conduit stale threshold: {}", this.hostSyncThreshold);
    this.engProductIdToSwatchProductIdsMap = tagProfile.getEngProductIdToSwatchProductIdsMap();
    this.roleToProductsMap = tagProfile.getRoleToTagLookup();
  }

  public static boolean isRhelVariant(String product) {
    return product.startsWith("RHEL ") && !product.startsWith("RHEL for ");
  }

  /**
   * Normalize the FactSets of the given host.
   *
   * @param hostFacts the collection of facts to normalize.
   * @return a normalized version of the host's facts.
   */
  public NormalizedFacts normalize(InventoryHostFacts hostFacts, OrgHostsData guestData) {

    NormalizedFacts normalizedFacts = new NormalizedFacts();
    normalizeClassification(normalizedFacts, hostFacts, guestData);
    normalizeHardwareType(normalizedFacts, hostFacts);
    normalizeSystemProfileFacts(normalizedFacts, hostFacts);
    normalizeSatelliteFacts(normalizedFacts, hostFacts);
    normalizeRhsmFacts(normalizedFacts, hostFacts);
    normalizeQpcFacts(normalizedFacts, hostFacts);
    normalizeSocketCount(normalizedFacts, hostFacts);
    normalizeMarketplace(normalizedFacts, hostFacts);
    normalizeConflictingOrMissingRhelVariants(normalizedFacts);
    pruneProducts(normalizedFacts);
    normalizeUnits(normalizedFacts, hostFacts);
    defaultNullFacts(normalizedFacts, hostFacts);
    return normalizedFacts;
  }

  private void normalizeSatelliteFacts(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    handleRole(normalizedFacts, hostFacts.getSatelliteRole());
    handleSla(normalizedFacts, hostFacts, hostFacts.getSatelliteSla());
    handleUsage(normalizedFacts, hostFacts, hostFacts.getSatelliteUsage());
  }

  private void normalizeUnits(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    if (hostFacts.getSyspurposeUnits() == null) {
      return;
    }
    switch (hostFacts.getSyspurposeUnits()) {
      case "Sockets":
        normalizedFacts.setCores(0);
        break;
      case "Cores/vCPU":
        normalizedFacts.setSockets(0);
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

  @SuppressWarnings("indentation")
  private void pruneProducts(NormalizedFacts normalizedFacts) {
    // If a Satellite or OpenShift product was found, do not include RHEL or its variants.
    boolean hasRhelIncludedProduct =
        normalizedFacts.getProducts().stream()
            .anyMatch(s -> s.startsWith("Satellite") || s.startsWith("OpenShift"));
    if (hasRhelIncludedProduct) {
      normalizedFacts.setProducts(
          normalizedFacts.getProducts().stream()
              .filter(prod -> !prod.startsWith("RHEL"))
              .collect(Collectors.toSet()));
    }
  }

  private void normalizeSocketCount(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    // modulo-2 rounding only applied to physical or hypervisors
    if (normalizedFacts.isHypervisor() || !isVirtual(hostFacts)) {
      Integer sockets = normalizedFacts.getSockets();
      if (sockets != null && (sockets % 2) == 1) {
        normalizedFacts.setSockets(sockets + 1);
      }
    }
  }

  private void normalizeConflictingOrMissingRhelVariants(NormalizedFacts normalizedFacts) {
    long variantCount =
        normalizedFacts.getProducts().stream().filter(FactNormalizer::isRhelVariant).count();

    boolean hasRhel = normalizedFacts.getProducts().contains("RHEL");

    if ((variantCount == 0 && hasRhel) || variantCount > 1) {
      normalizedFacts.addProduct("RHEL Ungrouped");
    }
  }

  private void normalizeSystemProfileFacts(
      NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    String cloudProvider = hostFacts.getCloudProvider();
    if (HardwareMeasurementType.isSupportedCloudProvider(cloudProvider)) {
      normalizedFacts.setCloudProviderType(
          HardwareMeasurementType.valueOf(cloudProvider.toUpperCase()));
    }
    if (hostFacts.getSystemProfileSockets() != 0) {
      normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
    }
    if (hostFacts.getSystemProfileSockets() != 0
        && hostFacts.getSystemProfileCoresPerSocket() != 0) {
      normalizedFacts.setCores(
          hostFacts.getSystemProfileCoresPerSocket() * hostFacts.getSystemProfileSockets());
    }
    if ("x86_64".equals(hostFacts.getSystemProfileArch())
        && HardwareMeasurementType.VIRTUAL
            .toString()
            .equalsIgnoreCase(hostFacts.getSystemProfileInfrastructureType())) {
      var effectiveCores = calculateVirtualCPU(hostFacts);
      normalizedFacts.setCores(effectiveCores);
    }
    getProductsFromProductIds(normalizedFacts, hostFacts.getSystemProfileProductIds());
  }

  private void normalizeMarketplace(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    if (!hostFacts.isMarketplace()) {
      return;
    }

    normalizedFacts.setMarketplace(hostFacts.isMarketplace());
    if (normalizedFacts.getCores() != 0) {
      normalizedFacts.setCores(0);
    }

    if (normalizedFacts.getSockets() != 0) {
      normalizedFacts.setSockets(0);
    }
  }

  private void defaultNullFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    if (normalizedFacts.getCores() == null) {
      normalizedFacts.setCores(hostFacts.getSystemProfileCoresPerSocket());
    }
    if (normalizedFacts.getSockets() == null) {
      normalizedFacts.setSockets(hostFacts.getSystemProfileSockets());
    }
  }

  private Integer calculateVirtualCPU(InventoryHostFacts virtualFacts) {
    //  For x86, guests: if we know the number of threads per core and its greater than one,
    //  then we divide the number of cores by that number.
    //  Otherwise we divide by two.
    int cpu =
        virtualFacts.getSystemProfileCoresPerSocket() * virtualFacts.getSystemProfileSockets();

    var threadsPerCore = 2.0;
    return (int) Math.ceil(cpu / threadsPerCore);
  }

  private void getProductsFromProductIds(
      NormalizedFacts normalizedFacts, Collection<String> productIds) {
    if (productIds == null) {
      return;
    }

    for (String productId : productIds) {
      try {
        Integer numericProductId = Integer.parseInt(productId);
        normalizedFacts
            .getProducts()
            .addAll(
                engProductIdToSwatchProductIdsMap.getOrDefault(
                    numericProductId, Collections.emptySet()));
      } catch (NumberFormatException e) {
        log.debug("Skipping non-numeric productId: {}", productId);
      }
    }
  }

  private void normalizeRhsmFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    // If the host hasn't been seen by rhsm-conduit, consider the host as unregistered, and do not
    // apply this host's facts.
    //
    // NOTE: This logic is applied since currently the inventory service does not prune inventory
    //       records once a host no longer exists.
    String syncTimestamp = hostFacts.getSyncTimestamp();
    boolean skipRhsmFacts =
        StringUtils.hasText(syncTimestamp) && hostUnregistered(OffsetDateTime.parse(syncTimestamp));
    if (!skipRhsmFacts) {
      getProductsFromProductIds(normalizedFacts, hostFacts.getProducts());

      // Check for cores and sockets. If not included, default to 0.

      normalizedFacts.setOrgId(hostFacts.getOrgId());
      normalizedFacts.setAccount(hostFacts.getAccount());

      handleRole(normalizedFacts, hostFacts.getSyspurposeRole());
      handleSla(normalizedFacts, hostFacts, hostFacts.getSyspurposeSla());
      handleUsage(normalizedFacts, hostFacts, hostFacts.getSyspurposeUsage());
    }
  }

  private void handleRole(NormalizedFacts normalizedFacts, String role) {
    if (role != null) {
      normalizedFacts.getProducts().removeIf(FactNormalizer::isRhelVariant);
      normalizedFacts
          .getProducts()
          .addAll(roleToProductsMap.getOrDefault(role, Collections.emptySet()));
    }
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

  private void normalizeQpcFacts(NormalizedFacts normalizedFacts, InventoryHostFacts hostFacts) {
    // Check if this is a RHEL host and set product.
    if (hostFacts.getQpcProducts() != null && hostFacts.getQpcProducts().contains("RHEL")) {
      normalizedFacts.addProduct("RHEL");
    }
    getProductsFromProductIds(normalizedFacts, hostFacts.getQpcProductIds());
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
    return lastSync.isBefore(clock.startOfToday().minus(hostSyncThreshold));
  }
}
