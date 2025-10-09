package com.redhat.swatch.hbi.events.ct;

import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.Host;
import com.redhat.swatch.hbi.events.normalization.NormalizedEventType;
import com.redhat.swatch.hbi.events.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.events.services.HbiEventConsumer;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.candlepin.subscriptions.json.Measurement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SwatchEventHelper {

  public static Event createSwatchEvent(
      HbiHost host,
      NormalizedEventType eventType,
      OffsetDateTime timestamp,
      Sla sla,
      Usage usage,
      HardwareType hardwareType,
      boolean isVirtual,
      boolean isUnmappedGuest, 
      boolean isHypervisor,
      String hypervisorUuid,
      List<String> productIds,
      Set<String> tags,
      List<Measurement> measurements) {
    return new Event()
        .withServiceType(HbiEventConsumer.EVENT_SERVICE_TYPE)
        .withEventSource(HbiEventConsumer.EVENT_SOURCE)
        .withEventType(eventType.toString())
        .withTimestamp(timestamp)
        .withExpiration(Optional.of(timestamp.plusHours(1)))
        .withLastSeen(OffsetDateTime.parse(host.getUpdated()))
        .withOrgId(host.getOrgId())
        .withInstanceId(host.getId().toString())
        .withInventoryId(Optional.of(host.id.toString()))
        .withInsightsId(Optional.ofNullable(host.insightsId))
        .withSubscriptionManagerId(Optional.of(host.subscriptionManagerId))
        .withDisplayName(Optional.ofNullable(host.displayName))
        .withSla(sla)
        .withUsage(usage)
        .withCloudProvider(null)
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
      HbiHostCreateUpdateEvent hbiEvent, List<String> productIds, Set<String> tags) {
    Host hostModel = new Host(hbiEvent.getHost());
    SystemProfileFacts sys = hostModel.getSystemProfileFacts();
    RhsmFacts rhsm = hostModel.getRhsmFacts().orElse(null);

    boolean isVirtual = (rhsm != null && Boolean.TRUE.equals(rhsm.getIsVirtual()))
        || (sys.getInfrastructureType() != null && sys.getInfrastructureType().equalsIgnoreCase("virtual"));
    String hypervisorUuid = sys.getHypervisorUuid();

    HardwareType hardwareType;
    if (sys.getCloudProvider() != null && !sys.getCloudProvider().isEmpty()) {
      hardwareType = HardwareType.CLOUD;
    } else if (isVirtual) {
      hardwareType = HardwareType.VIRTUAL;
    } else {
      hardwareType = HardwareType.PHYSICAL;
    }

    // Align with service semantics: cores equals total CPUs when present, otherwise
    // cores_per_socket * number_of_sockets
    Integer cpus = sys.getCpus();
    double sockets = sys.getSockets() != null ? sys.getSockets() : 0;
    double cores;
    if (cpus != null) {
      cores = cpus;
    } else {
      int coresPerSocket = sys.getCoresPerSocket() != null ? sys.getCoresPerSocket() : 0;
      int socketsInt = sys.getSockets() != null ? sys.getSockets() : 0;
      cores = (double) (coresPerSocket * socketsInt);
    }

    return createSwatchEvent(
        hbiEvent.getHost(),
        NormalizedEventType.from(hbiEvent),
        hbiEvent.getTimestamp().toOffsetDateTime(),
        Sla.fromValue(rhsm != null ? rhsm.getSla() : null),
        Usage.fromValue(rhsm != null ? rhsm.getUsage() : null),
        hardwareType,
        isVirtual,
        /* isUnmappedGuest */ false,
        /* isHypervisor */ false,
        hypervisorUuid,
        productIds,
        tags,
        new SwatchEventHelper().buildMeasurements(cores, sockets));
  }
}