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
package com.redhat.swatch.hbi.events.ct;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.hbi.events.HbiEventConstants;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;

public class SwatchEventHelper {

  public static Event createSwatchEvent(
      HbiHost host,
      NormalizedEventType eventType,
      OffsetDateTime timestamp,
      Sla sla,
      Usage usage,
      Event.CloudProvider cloudProvider,
      HardwareType hardwareType,
      boolean isVirtual,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      String hypervisorUuid,
      List<String> productIds,
      Set<String> tags,
      List<Measurement> measurements) {
    String instanceId = host.getProviderId();
    if (instanceId == null || instanceId.isBlank() || "null".equalsIgnoreCase(instanceId)) {
      instanceId = host.getId().toString();
    }
    return new Event()
        .withServiceType(HbiEventConstants.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConstants.EVENT_SOURCE)
        .withEventType(eventType.toString())
        .withTimestamp(timestamp)
        .withExpiration(Optional.of(timestamp.plusHours(1)))
        .withLastSeen(OffsetDateTime.parse(host.getUpdated()))
        .withOrgId(host.getOrgId())
        .withInstanceId(instanceId)
        .withInventoryId(Optional.of(host.id.toString()))
        .withInsightsId(Optional.ofNullable(host.insightsId))
        .withSubscriptionManagerId(Optional.of(host.subscriptionManagerId))
        .withDisplayName(Optional.ofNullable(host.displayName))
        .withSla(sla)
        .withUsage(usage)
        .withCloudProvider(cloudProvider)
        .withHardwareType(hardwareType)
        .withHypervisorUuid(Optional.ofNullable(hypervisorUuid))
        .withProductTag(tags)
        .withProductIds(productIds)
        .withIsVirtual(isVirtual)
        .withIsUnmappedGuest(isUnmappedGuest)
        .withIsHypervisor(isHypervisor)
        .withMeasurements(measurements);
  }

  public List<Measurement> buildMeasurements(double cores, double sockets) {
    return List.of(
        new Measurement().withMetricId("cores").withValue(cores),
        new Measurement().withMetricId("sockets").withValue(sockets));
  }

  public static Event createExpectedEvent(
      HbiHostCreateUpdateEvent hbiEvent,
      List<String> productIds,
      Set<String> tags,
      boolean isUnmappedGuest,
      boolean isHypervisor) {
    return createExpectedEvent(hbiEvent, productIds, tags, isUnmappedGuest, isHypervisor, false);
  }

  public static Event createExpectedEvent(
      HbiHostCreateUpdateEvent hbiEvent,
      List<String> productIds,
      Set<String> tags,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      boolean forceUpdatedEventType) {
    // 1) Normalize context
    Host hostModel = new Host(hbiEvent.getHost());
    SystemProfileFacts sys = hostModel.getSystemProfileFacts();
    RhsmFacts rhsm = hostModel.getRhsmFacts().orElse(null);
    boolean isVirtualHost = isVirtualHost(sys, rhsm);
    boolean hasCloudProvider =
        HardwareMeasurementType.isSupportedCloudProvider(sys.getCloudProvider());
    String hypervisorUuid = sys.getHypervisorUuid();

    // 2) Hardware type
    HardwareType hardwareType = determineHardwareType(hasCloudProvider, isVirtualHost);

    // 3) Measurements
    int sockets =
        computeSockets(sys, isVirtualHost, hasCloudProvider, isUnmappedGuest, isHypervisor, tags);
    int cores = computeCores(sys, isVirtualHost);

    // 4) Cloud provider
    Event.CloudProvider cloudProvider = resolveCloudProvider(sys, hasCloudProvider);

    // 5) Event type and build
    NormalizedEventType eventType =
        forceUpdatedEventType
            ? NormalizedEventType.INSTANCE_UPDATED
            : NormalizedEventType.from(hbiEvent);

    return createSwatchEvent(
        hbiEvent.getHost(),
        eventType,
        hbiEvent.getTimestamp().toOffsetDateTime(),
        Sla.fromValue(rhsm != null ? rhsm.getSla() : null),
        Usage.fromValue(rhsm != null ? rhsm.getUsage() : null),
        cloudProvider,
        hardwareType,
        isVirtualHost,
        isUnmappedGuest,
        isHypervisor,
        hypervisorUuid,
        productIds,
        tags,
        new SwatchEventHelper().buildMeasurements(cores, sockets));
  }

  private static boolean isVirtualHost(SystemProfileFacts sys, RhsmFacts rhsm) {
    boolean rhsmVirtual = rhsm != null && Boolean.TRUE.equals(rhsm.getIsVirtual());
    boolean infraVirtual =
        sys.getInfrastructureType() != null
            && sys.getInfrastructureType().equalsIgnoreCase("virtual");
    return rhsmVirtual || infraVirtual;
  }

  private static HardwareType determineHardwareType(boolean hasCloudProvider, boolean isVirtual) {
    if (hasCloudProvider) {
      return HardwareType.CLOUD;
    }
    if (isVirtual) {
      return HardwareType.VIRTUAL;
    }
    return HardwareType.PHYSICAL;
  }

  private static int computeSockets(
      SystemProfileFacts sys,
      boolean isVirtual,
      boolean hasCloudProvider,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      Set<String> tags) {
    int sockets = sys.getSockets() != null ? sys.getSockets() : 0;

    if (isVirtual) {
      if (hasCloudProvider) {
        sockets = 1;
      } else if (isUnmappedVirtualRhel(isVirtual, isUnmappedGuest, tags)) {
        sockets = 1;
      }
    }

    return roundSocketsForPhysicalOrHypervisor(sockets, isVirtual, isHypervisor);
  }

  private static int computeCores(SystemProfileFacts sys, boolean isVirtual) {
    Integer cpus = sys.getCpus();
    Integer sockets = sys.getSockets();
    Integer coresPerSocket = sys.getCoresPerSocket();

    if (isVirtual && "x86_64".equalsIgnoreCase(sys.getArch())) {
      double threadsPerCore = 2.0;
      if (sys.getThreadsPerCore() != null && sys.getThreadsPerCore() > 0) {
        threadsPerCore = sys.getThreadsPerCore();
      } else if (cpus != null
          && sockets != null
          && sockets > 0
          && coresPerSocket != null
          && coresPerSocket > 0) {
        threadsPerCore = (double) cpus / (sockets * coresPerSocket);
      }

      if (coresPerSocket == null || coresPerSocket == 0 || sockets == null || sockets == 0) {
        return 0;
      }
      int cpuTotal = coresPerSocket * sockets;
      return (int) Math.ceil(cpuTotal / threadsPerCore);
    }

    if (cpus != null) {
      return cpus;
    }
    int cps = coresPerSocket != null ? coresPerSocket : 0;
    int s = sockets != null ? sockets : 0;
    return cps * s;
  }

  private static boolean isUnmappedVirtualRhel(
      boolean isVirtual, boolean isUnmappedGuest, Set<String> tags) {
    if (!isVirtual || !isUnmappedGuest) {
      return false;
    }
    return tags.stream().anyMatch(t -> t != null && t.toUpperCase().startsWith("RHEL"));
  }

  private static int roundSocketsForPhysicalOrHypervisor(
      int sockets, boolean isVirtual, boolean isHypervisor) {
    if ((!isVirtual || isHypervisor) && sockets > 0 && (sockets % 2) == 1) {
      return sockets + 1;
    }
    return sockets;
  }

  private static Event.CloudProvider resolveCloudProvider(
      SystemProfileFacts sys, boolean hasCloudProvider) {
    if (!hasCloudProvider) {
      return null;
    }
    HardwareMeasurementType type = HardwareMeasurementType.fromString(sys.getCloudProvider());
    switch (type) {
      case AWS:
        return Event.CloudProvider.AWS;
      case AZURE:
        return Event.CloudProvider.AZURE;
      default:
        return null;
    }
  }
}
