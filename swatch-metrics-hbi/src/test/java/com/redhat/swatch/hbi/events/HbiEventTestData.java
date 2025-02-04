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
package com.redhat.swatch.hbi.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.io.FileUtils;

public class HbiEventTestData {

  public static String getSatelliteRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_rhel_host_created_satellite_event.json");
  }

  public static String getPhysicalRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_physical_rhel_host_created_event.json");
  }

  public static String getVirtualRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_virtual_rhel_host_created_event.json");
  }

  public static String getQpcRhelHostCreatedEvent() {
    return loadEventFileAsString("data/hbi_qpc_rhel_host_created_event.json");
  }

  public static String getHostDeletedEvent() {
    return loadEventFileAsString("data/hbi_host_deleted_event.json");
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
}
