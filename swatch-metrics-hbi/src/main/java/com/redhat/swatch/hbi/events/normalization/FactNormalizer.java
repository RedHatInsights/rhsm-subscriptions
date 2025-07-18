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
import com.redhat.swatch.common.model.ServiceLevel;
import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.normalization.facts.QpcFacts;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SatelliteFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.services.HbiHostRelationshipService;
import io.quarkus.runtime.util.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;

/**
 * Responsible for normalizing facts used to create {@link org.candlepin.subscriptions.json.Event}
 * objects from an HBI host.
 */
@Slf4j
@ApplicationScoped
public class FactNormalizer {

  private final ApplicationClock clock;
  private final ApplicationConfiguration appConfig;
  private final HbiHostRelationshipService hbiHostRelationshipService;

  @Inject
  public FactNormalizer(
      ApplicationClock clock,
      ApplicationConfiguration appConfig,
      HbiHostRelationshipService hbiHostRelationshipService) {
    this.clock = clock;
    this.appConfig = appConfig;
    this.hbiHostRelationshipService = hbiHostRelationshipService;
  }

  public NormalizedFacts normalize(Host host) {
    Optional<RhsmFacts> rhsmFacts = host.getRhsmFacts();
    Optional<SatelliteFacts> satelliteFacts = host.getSatelliteFacts();
    Optional<QpcFacts> qpcFacts = host.getQpcFacts();
    SystemProfileFacts systemProfileFacts = host.getSystemProfileFacts();

    String orgId = host.getOrgId();
    UUID inventoryId = host.getInventoryId();
    String syncTimestamp = rhsmFacts.map(RhsmFacts::getSyncTimestamp).orElse(null);
    HardwareMeasurementType cloudProviderType = determineCloudProviderType(systemProfileFacts);
    String hypervisorUuid = determineHypervisorUuid(systemProfileFacts, satelliteFacts);
    boolean isVirtual = determineIfVirtual(systemProfileFacts, rhsmFacts, satelliteFacts);

    boolean skipRhsmFacts = skipRhsmFacts(syncTimestamp);
    if (skipRhsmFacts) {
      log.info(
          "Skipping RHSM facts for HBI host orgId={} inventoryId={} during fact normalization.",
          orgId,
          inventoryId);
    }

    ProductNormalizer productNormalizer =
        new ProductNormalizer(
            systemProfileFacts, rhsmFacts, satelliteFacts, qpcFacts, skipRhsmFacts);

    String subscriptionManagerId = host.getSubscriptionManagerId();
    boolean isHypervisor = hbiHostRelationshipService.isHypervisor(orgId, subscriptionManagerId);
    boolean isUnmappedGuest =
        isVirtual
            && StringUtils.isNotEmpty(hypervisorUuid)
            && hbiHostRelationshipService.findHypervisor(orgId, hypervisorUuid).isEmpty();
    return NormalizedFacts.builder()
        .orgId(orgId)
        .inventoryId(inventoryId)
        .instanceId(determineInstanceId(host))
        .insightsId(host.getInsightsId())
        .subscriptionManagerId(subscriptionManagerId)
        .displayName(host.getDisplayName())
        .is3rdPartyMigrated(systemProfileFacts.getIs3rdPartyMigrated())
        .usage(
            determineUsage(orgId, subscriptionManagerId, satelliteFacts, rhsmFacts, skipRhsmFacts))
        .sla(determineSla(orgId, subscriptionManagerId, satelliteFacts, rhsmFacts, skipRhsmFacts))
        .cloudProviderType(cloudProviderType)
        .cloudProvider(toEventCloudProvider(cloudProviderType))
        .syncTimestamp(syncTimestamp)
        .isVirtual(isVirtual)
        .hardwareType(determineHardwareType(systemProfileFacts, isVirtual))
        .hypervisorUuid(hypervisorUuid)
        .isHypervisor(isHypervisor)
        .isUnmappedGuest(isUnmappedGuest)
        .productTags(productNormalizer.getProductTags())
        .productIds(productNormalizer.getProductIds())
        .lastSeen(determineLastSeenDate(host))
        .build();
  }

  private Event.CloudProvider toEventCloudProvider(HardwareMeasurementType measurementType) {
    if (Objects.isNull(measurementType)) {
      return null;
    }

    return switch (measurementType) {
      case AWS, AWS_CLOUDIGRADE -> Event.CloudProvider.AWS;
      case AZURE -> Event.CloudProvider.AZURE;
      case ALIBABA -> Event.CloudProvider.ALIBABA;
      case GOOGLE -> Event.CloudProvider.GOOGLE;
      default -> null;
    };
  }

  private String determineInstanceId(Host hbiHost) {
    String id = null;
    if (hbiHost.getProviderId() != null) {
      // will use the provider ID from HBI
      id = hbiHost.getProviderId();
    }

    if (id == null && hbiHost.getInventoryId() != null) {
      id = hbiHost.getInventoryId().toString();
    }
    return id;
  }

  private HardwareMeasurementType determineCloudProviderType(
      SystemProfileFacts systemProfileFacts) {
    HardwareMeasurementType type = null;
    String systemProfileCloudProvider = systemProfileFacts.getCloudProvider();
    if (HardwareMeasurementType.isSupportedCloudProvider(systemProfileCloudProvider)) {
      type = HardwareMeasurementType.fromString(systemProfileCloudProvider);
    }
    return type;
  }

  private boolean determineIfVirtual(
      SystemProfileFacts systemProfileFacts,
      Optional<RhsmFacts> rhsmFacts,
      Optional<SatelliteFacts> satelliteFacts) {
    boolean rhsmVirtual = rhsmFacts.map(RhsmFacts::getIsVirtual).orElse(false);
    boolean satelliteVirtual =
        !StringUtil.isNullOrEmpty(
            satelliteFacts.map(SatelliteFacts::getHypervisorUuid).orElse(null));
    boolean systemProfileVirtual =
        "virtual".equalsIgnoreCase(systemProfileFacts.getInfrastructureType());
    return rhsmVirtual || satelliteVirtual || systemProfileVirtual;
  }

  private String determineHypervisorUuid(
      SystemProfileFacts systemProfileFacts, Optional<SatelliteFacts> satelliteFacts) {
    String hypervisorUuid = satelliteFacts.map(SatelliteFacts::getHypervisorUuid).orElse(null);
    if (StringUtil.isNullOrEmpty(hypervisorUuid)) {
      hypervisorUuid = systemProfileFacts.getHypervisorUuid();
    }
    return hypervisorUuid;
  }

  private HardwareType determineHardwareType(
      SystemProfileFacts systemProfileFacts, boolean isVirtual) {
    var hardwareType = HardwareType.PHYSICAL;
    if (HardwareMeasurementType.isSupportedCloudProvider(systemProfileFacts.getCloudProvider())) {
      hardwareType = HardwareType.CLOUD;
    } else if (isVirtual) {
      hardwareType = HardwareType.VIRTUAL;
    }
    return hardwareType;
  }

  private boolean skipRhsmFacts(String syncTimestampFact) {
    return Optional.ofNullable(syncTimestampFact)
        .map(
            syncTimestamp ->
                !StringUtil.isNullOrEmpty(syncTimestampFact)
                    && hostUnregistered(OffsetDateTime.parse(syncTimestamp)))
        .orElse(false);
  }

  private String determineUsage(
      String orgId,
      String subscriptionManagerId,
      Optional<SatelliteFacts> satelliteFacts,
      Optional<RhsmFacts> rhsmFacts,
      boolean skipRhsmFacts) {
    Optional<Usage> satelliteUsage =
        handleUsage(
            orgId,
            subscriptionManagerId,
            satelliteFacts.map(SatelliteFacts::getUsage).orElse(null));

    if (!skipRhsmFacts) {
      Optional<Usage> rhsmUsage =
          handleUsage(
              orgId, subscriptionManagerId, rhsmFacts.map(RhsmFacts::getUsage).orElse(null));
      if (rhsmUsage.isPresent()) {
        return rhsmUsage.get().getValue();
      }
    }
    return satelliteUsage.map(Usage::getValue).orElse(null);
  }

  private String determineSla(
      String orgId,
      String subscriptionManagerId,
      Optional<SatelliteFacts> satelliteFacts,
      Optional<RhsmFacts> rhsmFacts,
      boolean skipRhsmFacts) {
    Optional<ServiceLevel> satelliteSla =
        handleSla(
            orgId, subscriptionManagerId, satelliteFacts.map(SatelliteFacts::getSla).orElse(null));

    if (!skipRhsmFacts) {
      Optional<ServiceLevel> rhsmSla =
          handleSla(orgId, subscriptionManagerId, rhsmFacts.map(RhsmFacts::getSla).orElse(null));
      if (rhsmSla.isPresent()) {
        return rhsmSla.get().getValue();
      }
    }
    return satelliteSla.map(ServiceLevel::getValue).orElse(null);
  }

  // NOTE: Modified from FactNormalizer
  private Optional<Usage> handleUsage(String orgId, String subscriptionManagerId, String usage) {
    Usage effectiveUsage = Usage.fromString(usage);
    if (usage != null && effectiveUsage == Usage.EMPTY && log.isDebugEnabled()) {
      log.debug(
          "OrgId {} host {} has unsupported value for Usage: {}",
          orgId,
          subscriptionManagerId,
          usage);
    }

    if (effectiveUsage != Usage.EMPTY) {
      return Optional.of(effectiveUsage);
    }
    return Optional.empty();
  }

  private Optional<ServiceLevel> handleSla(String orgId, String subscriptionManagerId, String sla) {
    ServiceLevel effectiveSla = ServiceLevel.fromString(sla);
    if (sla != null && effectiveSla == ServiceLevel.EMPTY && log.isDebugEnabled()) {
      log.debug(
          "OrgId {} host {} has unsupported value for SLA: {}", orgId, subscriptionManagerId, sla);
    }

    if (effectiveSla != ServiceLevel.EMPTY) {
      return Optional.of(effectiveSla);
    }
    return Optional.empty();
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
    return lastSync.isBefore(clock.startOfToday().minus(appConfig.getHostLastSyncThreshold()));
  }

  private OffsetDateTime determineLastSeenDate(Host host) {
    if (Objects.nonNull(host) && !StringUtil.isNullOrEmpty(host.getUpdatedDate())) {
      try {
        return OffsetDateTime.parse(host.getUpdatedDate());
      } catch (DateTimeParseException e) {
        log.warn(
            "Unable to determine lastSeenDate for {}; defaulting to null.", host.getUpdatedDate());
      }
    }
    return null;
  }
}
