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

import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHost;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostEventMetadata;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HbiEventBuilder {
  private final Host host;
  private final UUID inventoryId;

  private String type = "created";
  private OffsetDateTime timestamp = OffsetDateTime.now();
  private String requestId = UUID.randomUUID().toString();

  HbiEventBuilder(Host host) {
    this.host = host;
    this.inventoryId = host.getId() != null ? host.getId() : UUID.randomUUID();
  }

  public HbiEventBuilder type(String type) {
    this.type = type;
    return this;
  }

  public HbiEventBuilder timestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public HbiEventBuilder requestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  /**
   * Build and return the event as the base type. Suitable for passing directly to {@link
   * HbiEventManager#publish}. Use {@link #buildCreateUpdate()} or {@link #buildDelete()} when the
   * specific subtype is needed.
   */
  public HbiEvent build() {
    if ("delete".equals(type)) {
      return buildDeleteEvent();
    }
    return buildCreateUpdateEvent();
  }

  /** Build a create/update event as its concrete type */
  public HbiHostCreateUpdateEvent buildCreateUpdate() {
    return buildCreateUpdateEvent();
  }

  /** Build a delete event as its concrete type */
  public HbiHostDeleteEvent buildDelete() {
    return buildDeleteEvent();
  }

  private HbiHostCreateUpdateEvent buildCreateUpdateEvent() {
    var event = new HbiHostCreateUpdateEvent();
    event.setType(type);
    event.setTimestamp(timestamp.toZonedDateTime());
    var metadata = new HbiHostEventMetadata();
    metadata.setRequestId(requestId);
    event.setMetadata(metadata);
    event.setHost(mapToHbiHost());
    return event;
  }

  private HbiHostDeleteEvent buildDeleteEvent() {
    var event = new HbiHostDeleteEvent();
    event.setType(type);
    event.setTimestamp(timestamp.toZonedDateTime());
    event.setId(inventoryId);
    event.setOrgId(host.getOrgId());
    event.setAccount(host.getAccount());
    event.setInsightsId(host.getInsightsId());
    event.setRequestId(requestId);
    return event;
  }

  private HbiHost mapToHbiHost() {
    var hbiHost = new HbiHost();
    hbiHost.id = inventoryId;
    hbiHost.orgId = host.getOrgId();
    hbiHost.account = host.getAccount();
    hbiHost.displayName = host.getDisplayName();
    hbiHost.insightsId = host.getInsightsId();
    hbiHost.subscriptionManagerId = host.getSubscriptionManagerId();
    hbiHost.providerId = host.getProviderId();
    hbiHost.reporter = host.getReporter();

    hbiHost.created =
        host.getCreatedOn() != null ? host.getCreatedOn().toString() : timestamp.toString();
    hbiHost.updated =
        host.getModifiedOn() != null ? host.getModifiedOn().toString() : timestamp.toString();
    hbiHost.staleTimestamp = timestamp.plusHours(29).toString();
    hbiHost.staleWarningTimestamp = timestamp.plusDays(7).toString();
    hbiHost.culledTimestamp = timestamp.plusDays(14).toString();

    hbiHost.facts = buildFacts();

    if (host.getSystemProfileFacts() != null) {
      hbiHost.systemProfile = buildSystemProfile(host.getSystemProfileFacts());
    }

    return hbiHost;
  }

  private List<HbiHostFacts> buildFacts() {
    var facts = new ArrayList<HbiHostFacts>();
    if (host.getRhsmFacts() != null) {
      addNamespace(facts, "rhsm", host.getRhsmFacts().toMap());
    }
    if (host.getSatelliteFacts() != null) {
      addNamespace(facts, "satellite", host.getSatelliteFacts().toMap());
    }
    if (host.getQpcFacts() != null) {
      addNamespace(facts, "qpc", host.getQpcFacts().toMap());
    }
    return facts;
  }

  private void addNamespace(List<HbiHostFacts> list, String namespace, Map<String, Object> map) {
    if (map.isEmpty()) {
      return;
    }
    var entry = new HbiHostFacts();
    entry.setNamespace(namespace);
    entry.setFacts(map);
    list.add(entry);
  }

  private Map<String, Object> buildSystemProfile(SystemProfileFacts sp) {
    var profile = new LinkedHashMap<String, Object>();

    // Re-apply derivation here because build() bypasses applyDerivedFacts()
    Integer cps = sp.getCoresPerSocket();
    if (cps == null
        && sp.getNumberOfCpus() != null
        && sp.getNumberOfSockets() != null
        && sp.getNumberOfSockets() > 0) {
      cps = sp.getNumberOfCpus() / sp.getNumberOfSockets();
    }

    putIfPresent(profile, "host_type", sp.getHostType());
    putIfPresent(profile, "virtual_host_uuid", sp.getHypervisorUuid());
    putIfPresent(profile, "infrastructure_type", sp.getInfrastructureType());
    putIfPresent(profile, "cores_per_socket", cps);
    putIfPresent(profile, "number_of_sockets", sp.getNumberOfSockets());
    putIfPresent(profile, "number_of_cpus", sp.getNumberOfCpus());
    putIfPresent(profile, "threads_per_core", sp.getThreadsPerCore());
    putIfPresent(profile, "cloud_provider", sp.getCloudProvider());
    putIfPresent(profile, "arch", sp.getArch());
    putIfPresent(profile, "is_marketplace", sp.getIsMarketplace());
    return profile;
  }

  private static void putIfPresent(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
