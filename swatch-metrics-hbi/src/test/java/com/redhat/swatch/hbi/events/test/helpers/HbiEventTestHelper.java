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
package com.redhat.swatch.hbi.events.test.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.configuration.ApplicationConfiguration;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostFacts;
import com.redhat.swatch.hbi.events.normalization.model.facts.RhsmFacts;
import com.redhat.swatch.hbi.events.repository.HbiHostRelationship;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import org.candlepin.clock.ApplicationClock;

/** Provides utility test helpers for HBI Event based tests. */
@ApplicationScoped
@Getter
public class HbiEventTestHelper {
  private final ObjectMapper objectMapper;
  private final ApplicationClock clock;
  private final ApplicationConfiguration config;

  public HbiEventTestHelper(ApplicationConfiguration config, ObjectMapper objectMapper) {
    this.config = config;
    this.clock = config.applicationClock();
    this.objectMapper = objectMapper;
  }

  public HbiHostCreateUpdateEvent getCreateUpdateEvent(String messageJson) {
    HbiHostCreateUpdateEvent event =
        HbiEventTestData.getEvent(objectMapper, messageJson, HbiHostCreateUpdateEvent.class);

    // Ensure the event is not stale.
    event.getHost().setStaleTimestamp(clock.now().plusMonths(1).toString());
    // Override the syncTimestamp fact so that it aligns with the current time
    // and is within the configured 'hostLastSyncThreshold'.
    setRhsmSyncTimestamp(event, clock.now().minusHours(5));
    return event;
  }

  public HbiEvent createEventOfTypeUnknown() {
    return new HbiEvent() {
      @Override
      public String getType() {
        return "unknown";
      }
    };
  }

  public HbiHostCreateUpdateEvent createTemplatedGuestCreatedEvent(
      String orgId, UUID inventoryUuid, UUID subscriptionManagerId, String hypervisorUuid) {
    String template = HbiEventTestData.getHbiRhelGuestHostCreatedTemplate();
    template = template.replaceAll("\\$INVENTORY_UUID", inventoryUuid.toString());
    template = template.replaceAll("\\$ORG_ID", orgId);
    template = template.replaceAll("\\$SUBSCRIPTION_MANAGER_ID", subscriptionManagerId.toString());
    template = template.replaceAll("\\$HYPERVISOR_UUID", hypervisorUuid);
    return getCreateUpdateEvent(template);
  }

  public HbiHostDeleteEvent createHostDeleteEvent(
      String orgId, UUID inventoryId, OffsetDateTime timestamp) {
    HbiHostDeleteEvent event =
        HbiEventTestData.getEvent(
            objectMapper, HbiEventTestData.getHostDeletedTemplate(), HbiHostDeleteEvent.class);
    event.setId(inventoryId);
    event.setTimestamp(timestamp.toZonedDateTime());
    event.setOrgId(orgId);
    return event;
  }

  public HbiHostRelationship relationshipFromHbiEvent(HbiHostCreateUpdateEvent event) {
    try {
      HbiHostRelationship relationship = new HbiHostRelationship();
      relationship.setOrgId(event.getHost().getOrgId());
      relationship.setInventoryId(event.getHost().getId());
      relationship.setSubscriptionManagerId(event.getHost().getSubscriptionManagerId());
      relationship.setCreationDate(clock.now());
      relationship.setLastUpdated(clock.now());
      relationship.setLatestHbiEventData(objectMapper.writeValueAsString(event.getHost()));
      return relationship;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public HbiHostRelationship virtualRelationshipFromHbiEvent(
      HbiHostCreateUpdateEvent event, String hypervisorUuid, boolean isUnmappedGuest)
      throws RuntimeException {
    HbiHostRelationship relationship = relationshipFromHbiEvent(event);
    relationship.setUnmappedGuest(isUnmappedGuest);
    relationship.setHypervisorUuid(hypervisorUuid);
    return relationship;
  }

  public void setFact(
      HbiHostCreateUpdateEvent event, String namespace, String factName, String factValue) {
    HbiHostFacts hostFacts =
        event.getHost().getFacts().stream()
            .filter(f -> namespace.equals(f.getNamespace()))
            .findFirst()
            .orElseGet(
                () -> {
                  HbiHostFacts facts = new HbiHostFacts();
                  facts.setNamespace(namespace);
                  return facts;
                });
    hostFacts.getFacts().put(factName, factValue);
  }

  public void setRhsmSyncTimestamp(HbiHostCreateUpdateEvent event, OffsetDateTime syncTimestamp) {
    setFact(
        event,
        RhsmFacts.RHSM_FACTS_NAMESPACE,
        RhsmFacts.SYNC_TIMESTAMP_FACT,
        syncTimestamp.toString());
  }
}
