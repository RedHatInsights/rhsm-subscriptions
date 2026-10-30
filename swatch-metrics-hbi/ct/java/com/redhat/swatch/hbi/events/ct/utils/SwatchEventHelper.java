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
package com.redhat.swatch.hbi.events.ct.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.swatch.component.tests.utils.JsonUtils;
import com.redhat.swatch.hbi.events.HbiEventConstants;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.CloudProvider;
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
      CloudProvider cloudProvider,
      HardwareType hardwareType,
      boolean isVirtual,
      boolean isUnmappedGuest,
      boolean isHypervisor,
      String hypervisorUuid,
      List<String> productIds,
      Set<String> tags,
      List<Measurement> measurements,
      boolean conversion) {
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
        .withConversion(conversion)
        .withMeasurements(measurements);
  }

  public static List<Measurement> buildMeasurements(double cores, double sockets) {
    return List.of(
        new Measurement().withMetricId("cores").withValue(cores),
        new Measurement().withMetricId("sockets").withValue(sockets));
  }

  /**
   * Build measurements list, excluding suppressed metrics.
   *
   * @param cores cores value, or null to suppress
   * @param sockets sockets value, or null to suppress
   */
  private static List<Measurement> buildMeasurementsWithSuppression(
      Integer cores, Integer sockets) {
    List<Measurement> measurements = new ArrayList<>();
    if (cores != null) {
      measurements.add(new Measurement().withMetricId("cores").withValue(cores.doubleValue()));
    }
    if (sockets != null) {
      measurements.add(new Measurement().withMetricId("sockets").withValue(sockets.doubleValue()));
    }
    return measurements;
  }

  public static Event createExpectedEvent(
      HbiHostCreateUpdateEvent hbiEvent,
      NormalizedEventType eventType,
      List<String> productIds,
      Set<String> tags,
      boolean isUnmappedGuest,
      boolean isHypervisor) {
    // 1) Normalize context
    Host hostModel = new Host(hbiEvent.getHost());
    SystemProfileFacts sys = hostModel.getSystemProfileFacts();
    RhsmFacts rhsm = hostModel.getRhsmFacts().orElse(null);
    boolean isVirtualHost = isVirtualHost(sys, rhsm);
    boolean hasCloudProvider =
        "AWS".equalsIgnoreCase(sys.getCloudProvider())
            || "AZURE".equalsIgnoreCase(sys.getCloudProvider());
    String hypervisorUuid = sys.getHypervisorUuid();

    // 2) Hardware type
    HardwareType hardwareType = determineHardwareType(hasCloudProvider, isVirtualHost);

    // 3) Measurements
    Integer sockets =
        computeSockets(sys, isVirtualHost, hasCloudProvider, isUnmappedGuest, isHypervisor, tags);
    Integer cores = computeCores(sys, isVirtualHost);

    // Apply marketplace normalization (forces cores and sockets to 0)
    if (Boolean.TRUE.equals(sys.getIsMarketplace())) {
      cores = 0;
      sockets = 0;
    }

    // Apply system purpose units normalization (suppresses measurements)
    String systemPurposeUnits = rhsm != null ? rhsm.getSystemPurposeUnits() : null;
    if (systemPurposeUnits != null) {
      switch (systemPurposeUnits) {
        case "Sockets":
          cores = null; // Suppress cores when units are set to Sockets
          break;
        case "Cores/vCPU":
          sockets = null; // Suppress sockets when units are set to Cores/vCPU
          break;
        default:
          // Other values don't affect measurements
          break;
      }
    }

    // 4) Cloud provider
    CloudProvider cloudProvider = resolveCloudProvider(sys, hasCloudProvider);

    // 5) Conversion (3rd party migration)
    boolean conversion = Boolean.TRUE.equals(sys.getIs3rdPartyMigrated());

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
        buildMeasurementsWithSuppression(cores, sockets),
        conversion);
  }

  public static Event createExpectedDeletedEvent(
      HbiHostCreateUpdateEvent lastKnownHbiEvent,
      HbiHostDeleteEvent deleteEvent,
      List<String> productIds,
      Set<String> tags,
      boolean isUnmappedGuest,
      boolean isHypervisor) {

    return lastKnownHbiEvent != null
        ? createExpectedDeletedEventFromKnownHost(
            lastKnownHbiEvent, deleteEvent, productIds, tags, isUnmappedGuest, isHypervisor)
        : createExpectedDeletedEventFromUnseenHost(deleteEvent);
  }

  private static Event createExpectedDeletedEventFromKnownHost(
      HbiHostCreateUpdateEvent lastKnownHbiEvent,
      HbiHostDeleteEvent deleteEvent,
      List<String> productIds,
      Set<String> tags,
      boolean isUnmappedGuest,
      boolean isHypervisor) {

    Host hostModel = new Host(lastKnownHbiEvent.getHost());
    SystemProfileFacts sys = hostModel.getSystemProfileFacts();
    RhsmFacts rhsm = hostModel.getRhsmFacts().orElse(null);

    boolean isVirtualHost = isVirtualHost(sys, rhsm);
    boolean hasCloudProvider =
        "AWS".equalsIgnoreCase(sys.getCloudProvider())
            || "AZURE".equalsIgnoreCase(sys.getCloudProvider());
    String hypervisorUuid = sys.getHypervisorUuid();

    HardwareType hardwareType = determineHardwareType(hasCloudProvider, isVirtualHost);
    int sockets =
        computeSockets(sys, isVirtualHost, hasCloudProvider, isUnmappedGuest, isHypervisor, tags);
    int cores = computeCores(sys, isVirtualHost);
    CloudProvider cloudProvider = resolveCloudProvider(sys, hasCloudProvider);
    boolean conversion = Boolean.TRUE.equals(sys.getIs3rdPartyMigrated());

    return createSwatchEvent(
        lastKnownHbiEvent.getHost(),
        NormalizedEventType.INSTANCE_DELETED,
        deleteEvent.getTimestamp().toOffsetDateTime(),
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
        buildMeasurements(cores, sockets),
        conversion);
  }

  private static Event createExpectedDeletedEventFromUnseenHost(HbiHostDeleteEvent deleteEvent) {

    // Minimal “unseen host” deleted event; only trusted fields from delete payload.
    String inventoryId = deleteEvent.getId().toString();
    String insightsId = deleteEvent.getInsightsId();
    String orgId = deleteEvent.getOrgId();

    return new Event()
        .withServiceType(HbiEventConstants.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConstants.EVENT_SOURCE)
        .withEventType(NormalizedEventType.INSTANCE_DELETED.toString())
        .withTimestamp(deleteEvent.getTimestamp().toOffsetDateTime())
        .withExpiration(
            java.util.Optional.of(deleteEvent.getTimestamp().toOffsetDateTime().plusHours(1)))
        .withOrgId(orgId)
        .withInstanceId(inventoryId) // choose inventory UUID as instance id
        .withInventoryId(java.util.Optional.of(inventoryId))
        .withInsightsId(java.util.Optional.ofNullable(insightsId));
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

  private static CloudProvider resolveCloudProvider(
      SystemProfileFacts sys, boolean hasCloudProvider) {
    if (!hasCloudProvider) {
      return null;
    }
    String name = sys.getCloudProvider();
    if ("AWS".equalsIgnoreCase(name)) {
      return CloudProvider.AWS;
    }
    if ("AZURE".equalsIgnoreCase(name)) {
      return CloudProvider.AZURE;
    }
    return null;
  }

  /**
   * Extract a measurement value from a SWatch event by metric ID.
   *
   * @param event the SWatch event
   * @param metricId the metric ID (e.g., "cores", "sockets")
   * @return the measurement value, or null if not found
   */
  public static Double getMeasurementValue(Event event, String metricId) {
    if (event.getMeasurements() == null) {
      return null;
    }
    return event.getMeasurements().stream()
        .filter(m -> metricId.equals(m.getMetricId()))
        .findFirst()
        .map(Measurement::getValue)
        .orElse(null);
  }

  /**
   * Compare two SWatch events for equality using normalization logic. This method normalizes both
   * events by removing volatile fields (timestamp, expiration, lastSeen), removing null-valued
   * fields, and sorting arrays where order is not semantically relevant.
   *
   * @param expected the expected event
   * @param actual the actual event
   * @return true if events match after normalization
   */
  public static boolean eventsMatch(Event expected, Event actual) {
    try {
      ObjectMapper mapper = JsonUtils.getObjectMapper();
      ObjectNode expectedNode = mapper.valueToTree(expected).deepCopy();
      ObjectNode actualNode = mapper.valueToTree(actual).deepCopy();

      normalizeSwatchEventNode(expectedNode);
      normalizeSwatchEventNode(actualNode);

      return expectedNode.equals(actualNode);
    } catch (Exception e) {
      return false;
    }
  }

  private static void normalizeSwatchEventNode(ObjectNode node) {
    // Drop volatile fields
    node.remove("timestamp");
    node.remove("expiration");
    node.remove("last_seen");
    node.remove("lastSeen");

    // Remove null-valued fields
    List<String> toRemove = new ArrayList<>();
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String f = it.next();
      JsonNode v = node.get(f);
      if (v == null || v.isNull()) {
        toRemove.add(f);
      }
    }
    node.remove(toRemove);

    // Sort arrays where order is not semantically relevant
    sortTextArray(node, "product_ids");
    sortTextArray(node, "productIds");
    sortTextArray(node, "product_tag");
    sortTextArray(node, "productTag");
    sortMeasurements(node, "measurements");
  }

  private static void sortTextArray(ObjectNode node, String fieldName) {
    JsonNode n = node.get(fieldName);
    if (n != null && n.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      n.forEach(items::add);
      items.sort(Comparator.comparing(JsonNode::asText));
      ArrayNode newArr = node.arrayNode();
      for (JsonNode it : items) {
        newArr.add(it);
      }
      node.set(fieldName, newArr);
    }
  }

  private static void sortMeasurements(ObjectNode node, String fieldName) {
    JsonNode n = node.get(fieldName);
    if (n != null && n.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      n.forEach(items::add);
      Function<JsonNode, String> key =
          m -> {
            if (m.has("metric_id")) {
              return m.get("metric_id").asText();
            }
            if (m.has("metricId")) {
              return m.get("metricId").asText();
            }
            return m.toString();
          };
      items.sort(Comparator.comparing(key));
      ArrayNode newArr = node.arrayNode();
      for (JsonNode it : items) {
        newArr.add(it);
      }
      node.set(fieldName, newArr);
    }
  }
}
