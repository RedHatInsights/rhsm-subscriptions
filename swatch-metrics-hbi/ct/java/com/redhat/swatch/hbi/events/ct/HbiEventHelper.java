package com.redhat.swatch.hbi.events.ct;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class HbiEventHelper {
  public static String getRhsmHostEvent(
      String type,
      String rhProdJsonArray,
      boolean isVirtual,
      String syncTimestamp,
      String sla,
      String usage,
      int cores,
      int sockets
  ) {
    String template = readJsonFilePath("data/templates/hbi_rhsm_host_event.json");
    return template
        .replace("$TYPE", type)
        .replace("\"$RH_PROD_JSON\"", rhProdJsonArray)
        .replace("\"$IS_VIRTUAL\"", Boolean.toString(isVirtual))
        .replace("$SYNC_TIMESTAMP", syncTimestamp)
        .replace("$SYSPURPOSE_SLA", sla)
        .replace("$SYSPURPOSE_USAGE", usage)
        .replace("\"$CORES\"", Integer.toString(cores))
        .replace("\"$SOCKETS\"", Integer.toString(sockets));
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
}
