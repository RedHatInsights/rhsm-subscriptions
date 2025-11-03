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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import com.redhat.swatch.hbi.events.normalization.Host;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class HbiEventHelper {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  public static HbiHostCreateUpdateEvent getRhsmHostEvent(
      String type,
      String subscriptionManagerId,
      Collection<String> rhProdArray,
      boolean isVirtual,
      String arch,
      OffsetDateTime timestamp,
      String sla,
      String usage,
      int cores,
      int sockets,
      Integer threadsPerCore,
      Integer cpus,
      String cloudProvider) {

    OffsetDateTime staleTimestamp = timestamp.plusDays(7);

    String productIds =
        rhProdArray.stream().map(str -> "\"" + str + "\"").collect(Collectors.joining(","));

    String template =
        readJsonFilePath("data/templates/hbi_rhsm_host_event.json")
            .replaceAll("\\$TYPE", type)
            .replaceAll("\\$INVENTORY_UUID", UUID.randomUUID().toString())
            .replaceAll(
                "\\$SUBSCRIPTION_MANAGER_UUID",
                subscriptionManagerId == null
                    ? UUID.randomUUID().toString()
                    : subscriptionManagerId)
            .replaceAll("\\$INSIGHTS_UUID", UUID.randomUUID().toString())
            .replaceAll("\\$RH_PRODUCT_IDS", productIds)
            .replaceAll("\\$IS_VIRTUAL", Boolean.toString(isVirtual))
            .replaceAll("\\$ARCH", arch)
            .replaceAll("\\$TIMESTAMP", timestamp.toString())
            .replaceAll("\\$SYNC_TIMESTAMP", timestamp.toString())
            .replaceAll("\\$STALE_TIMESTAMP", staleTimestamp.toString())
            .replaceAll("\\$STALE_WARNING_TIMESTAMP", staleTimestamp.minusDays(3).toString())
            .replaceAll("\\$SYSPURPOSE_SLA", sla)
            .replaceAll("\\$SYSPURPOSE_USAGE", usage)
            .replaceAll(
                "\\$PROVIDER_ID",
                cloudProvider == null ? "null" : ("\"" + UUID.randomUUID().toString() + "\""))
            .replaceAll(
                "\\$PROVIDER_TYPE", cloudProvider == null ? "null" : ("\"" + cloudProvider + "\""))
            .replaceAll("\\$CORES", Integer.toString(cores))
            .replaceAll("\\$SOCKETS", Integer.toString(sockets))
            .replaceAll(
                "\\$THREADS_PER_CORE",
                threadsPerCore == null ? "null" : Integer.toString(threadsPerCore))
            .replaceAll("\\$CPUS", cpus == null ? "null" : Integer.toString(cpus))
            .replaceAll("\\$INFRASTRUCTURE_TYPE", isVirtual ? "virtual" : "physical")
            .replaceAll(
                "\\$HYPERVISOR_UUID",
                isVirtual ? ("\"" + UUID.randomUUID().toString() + "\"") : "null")
            .replaceAll(
                "\\$CLOUD_PROVIDER", cloudProvider == null ? "null" : ("\"" + cloudProvider + "\""))
            .replaceAll(
                "\\$BIOS_VERSION",
                "aws".equals(cloudProvider) ? ("\"" + "4.2.amazon" + "\"") : "null")
            .replaceAll("\\$REQUEST_ID", UUID.randomUUID().toString());

    EventTemplateValidator.verifyNoTemplateVariablesExist(template);
    return validateEvent(getEvent(template, HbiHostCreateUpdateEvent.class));
  }

  public static List<HbiHostCreateUpdateEvent> getHypervisorAndGuestEvents(
      String type,
      Collection<String> rhProdArray,
      OffsetDateTime timestamp,
      String sla,
      String usage,
      int hypervisorCores,
      int hypervisorSockets,
      int guestCores,
      int guestSockets) {
    // Create a shared subscription manager id for the hypervisor that the guest will reference
    String hypervisorSubscriptionManagerId = UUID.randomUUID().toString();

    // Build physical hypervisor event
    HbiHostCreateUpdateEvent hypervisorEvent =
        getRhsmHostEvent(
            type,
            hypervisorSubscriptionManagerId,
            rhProdArray,
            false,
            "x86_64",
            timestamp,
            sla,
            usage,
            hypervisorCores,
            hypervisorSockets,
            null,
            null,
            null);

    // Build guest (virtual, non-cloud) event; link to hypervisor via virtual_host_uuid
    HbiHostCreateUpdateEvent guestEvent =
        getRhsmHostEvent(
            type,
            null,
            rhProdArray,
            true,
            "x86_64",
            timestamp,
            sla,
            usage,
            guestCores,
            guestSockets,
            null,
            null,
            null);

    // Ensure the guest references the hypervisor by subscription_manager_id
    if (guestEvent.getHost() != null && guestEvent.getHost().getSystemProfile() != null) {
      guestEvent
          .getHost()
          .getSystemProfile()
          .put("virtual_host_uuid", hypervisorSubscriptionManagerId);
    }

    return List.of(hypervisorEvent, guestEvent);
  }

  private static String readJsonFilePath(String name) {
    try (InputStream is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + name);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed reading resource: " + name, e);
    }
  }

  private static <E> E getEvent(String message, Class<E> eventClass) {
    try {
      return objectMapper.readValue(message, eventClass);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Unable to create event class from message: " + eventClass, e);
    }
  }

  private static HbiHostCreateUpdateEvent validateEvent(HbiHostCreateUpdateEvent event) {
    try {
      // Will throw an exception if the data provided by the template is invalid.
      // This is primarily triggered by the normalization of the facts as they are defined
      // by HBI as a Map<String, Object> which isn't ideal.
      new Host(event.getHost());
    } catch (Exception e) {
      throw new RuntimeException("Invalid template data specified.", e);
    }
    return event;
  }
}
