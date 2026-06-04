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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostDeleteEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.io.FileUtils;

public class HbiEventTestData {

  public static String getSatelliteRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_rhel_host_created_satellite_event.json");
  }

  public static String getPhysicalRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_physical_rhel_host_created_event.json");
  }

  public static String getPhysicalRhelHostUpdatedEvent() {
    return loadEventFileAsString("data/hbi_physical_rhel_host_updated_event.json");
  }

  public static String getVirtualRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_virtual_rhel_host_created_event.json");
  }

  public static String getQpcRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_qpc_rhel_host_created_event.json");
  }

  public static String getHostDeletedEvent() {
    return loadEventFileAsString("data/hbi_physical_rhel_host_deleted_event.json");
  }

  public static String getHostUnknownEvent() {
    return loadEventFileAsString("data/hbi_unknown_event.json");
  }

  public static String getHostDeletedTemplate() {
    return loadEventFileAsString("data/templates/hbi_host_deleted_template.json");
  }

  public static String getHbiRhelGuestHostCreatedTemplate() {
    return loadEventFileAsString("data/templates/hbi_rhel_guest_host_created_template.json");
  }

  private static String loadEventFileAsString(String eventFileName) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL fileUrl = loader.getResource(eventFileName);
      if (Objects.isNull(fileUrl)) {
        throw new RuntimeException("Test event resource file not found: " + eventFileName);
      }
      return FileUtils.readFileToString(new File(fileUrl.getFile()), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Unable to load test event resource file: " + eventFileName, e);
    }
  }

  public static <E> E getEvent(ObjectMapper mapper, String message, Class<E> eventClass) {
    try {
      return mapper.readValue(message, eventClass);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Unable to create event class from message: " + eventClass, e);
    }
  }

  public static HbiHostCreateUpdateEvent createTemplatedGuestCreatedEvent(
      ObjectMapper mapper,
      String orgId,
      UUID inventoryUuid,
      UUID subscriptionManagerId,
      String hypervisorUuid) {
    String template = getHbiRhelGuestHostCreatedTemplate();
    template = template.replaceAll("\\$INVENTORY_UUID", inventoryUuid.toString());
    template = template.replaceAll("\\$ORG_ID", orgId);
    template = template.replaceAll("\\$SUBSCRIPTION_MANAGER_ID", subscriptionManagerId.toString());
    template = template.replaceAll("\\$HYPERVISOR_UUID", hypervisorUuid);
    return getEvent(mapper, template, HbiHostCreateUpdateEvent.class);
  }

  public static HbiHostDeleteEvent createTemplatedHostDeletedEvent(
      ObjectMapper mapper,
      String orgId,
      UUID inventoryUuid,
      String insightsId,
      OffsetDateTime timestamp) {
    HbiHostDeleteEvent event = getEvent(mapper, getHostDeletedTemplate(), HbiHostDeleteEvent.class);
    event.setOrgId(orgId);
    event.setId(inventoryUuid);
    event.setInsightsId(insightsId);
    event.setTimestamp(ZonedDateTime.ofInstant(timestamp.toInstant(), timestamp.getOffset()));
    return event;
  }
}
