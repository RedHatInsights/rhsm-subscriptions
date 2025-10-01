package com.redhat.swatch.hbi.events.ct;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class HbiEventHelper {

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());

  public static HbiHostCreateUpdateEvent getRhsmHostEvent(
      String type,
      Collection<String> rhProdArray,
      boolean isVirtual,
      String syncTimestamp,
      String sla,
      String usage,
      int cores,
      int sockets
  ) {
    String template = readJsonFilePath("data/templates/hbi_rhsm_host_event.json")
        .replaceAll("\\$TYPE", type)
        .replaceAll("\\$RH_PRODUCT_IDS", String.join(",", rhProdArray))
        .replaceAll("\\$IS_VIRTUAL", Boolean.toString(isVirtual))
        .replaceAll("\\$SYNC_TIMESTAMP", syncTimestamp)
        .replaceAll("\\$SYSPURPOSE_SLA", sla)
        .replaceAll("\\$SYSPURPOSE_USAGE", usage)
        .replaceAll("\\$CORES", Integer.toString(cores))
        .replaceAll("\\$SOCKETS", Integer.toString(sockets));
    return getEvent(template, HbiHostCreateUpdateEvent.class);
  }

  private static String readJsonFilePath(String name) {
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + name);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed reading resource: " + name, e);
    }
  }

  private static <E> E getEvent( String message, Class<E> eventClass) {
    try {
      return objectMapper.readValue(message, eventClass);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Unable to create event class from message: " + eventClass, e);
    }
  }
}
