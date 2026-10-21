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
package com.redhat.swatch.clowder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Reads sanitized Clowder configuration slices from {@code cdappconfig.json}. */
final class ClowderCdappConfig {

  static final String ENDPOINTS = "endpoints";
  static final String PRIVATE_ENDPOINTS = "privateEndpoints";
  static final String DEPENDENCY_ENDPOINTS = "dependencyEndpoints";
  static final String PRIVATE_DEPENDENCY_ENDPOINTS = "privateDependencyEndpoints";

  private ClowderCdappConfig() {}

  static Map<String, Object> read(Path path, ObjectMapper mapper) {
    if (path == null || mapper == null || !Files.isRegularFile(path)) {
      return Map.of();
    }
    try {
      JsonNode root = mapper.readTree(Files.readString(path));
      Map<String, Object> config = new LinkedHashMap<>();
      putIfPresent(config, ENDPOINTS, sanitizeEndpointList(root.get(ENDPOINTS)));
      putIfPresent(config, PRIVATE_ENDPOINTS, sanitizeEndpointList(root.get(PRIVATE_ENDPOINTS)));
      putIfPresent(
          config, DEPENDENCY_ENDPOINTS, sanitizeVersionedEndpoints(root.get(DEPENDENCY_ENDPOINTS)));
      putIfPresent(
          config,
          PRIVATE_DEPENDENCY_ENDPOINTS,
          sanitizeVersionedEndpoints(root.get(PRIVATE_DEPENDENCY_ENDPOINTS)));
      return config;
    } catch (IOException e) {
      return Map.of();
    }
  }

  private static void putIfPresent(Map<String, Object> config, String key, Object value) {
    if (value instanceof Map<?, ?> map && !map.isEmpty()) {
      config.put(key, value);
    } else if (value instanceof List<?> list && !list.isEmpty()) {
      config.put(key, value);
    }
  }

  static List<Map<String, Object>> sanitizeEndpointList(JsonNode endpoints) {
    if (endpoints == null || !endpoints.isArray()) {
      return List.of();
    }
    List<Map<String, Object>> sanitized = new ArrayList<>();
    for (JsonNode endpoint : endpoints) {
      Map<String, Object> info = sanitizeV1Endpoint(endpoint);
      if (!info.isEmpty()) {
        sanitized.add(info);
      }
    }
    return sanitized;
  }

  static Map<String, Object> sanitizeV1Endpoint(JsonNode endpoint) {
    Map<String, Object> info = new LinkedHashMap<>();
    if (endpoint == null || !endpoint.isObject()) {
      return info;
    }
    putText(info, endpoint, "app");
    putText(info, endpoint, "name");
    putText(info, endpoint, "hostname");
    JsonNode port = endpoint.get("port");
    if (port != null && port.isNumber()) {
      info.put("port", port.asInt());
    }
    return info;
  }

  static Map<String, Object> sanitizeVersionedEndpoints(JsonNode versioned) {
    if (versioned == null || !versioned.isObject()) {
      return Map.of();
    }
    Map<String, Object> versions = new LinkedHashMap<>();
    for (Entry<String, JsonNode> versionEntry : versioned.properties()) {
      JsonNode apps = versionEntry.getValue();
      if (apps == null || !apps.isObject()) {
        continue;
      }
      Map<String, Object> appMap = new LinkedHashMap<>();
      for (Entry<String, JsonNode> appEntry : apps.properties()) {
        JsonNode deployments = appEntry.getValue();
        if (deployments == null || !deployments.isObject()) {
          continue;
        }
        Map<String, Object> deploymentMap = new LinkedHashMap<>();
        for (Entry<String, JsonNode> deploymentEntry : deployments.properties()) {
          Map<String, Object> endpoint = sanitizeV2Endpoint(deploymentEntry.getValue());
          if (!endpoint.isEmpty()) {
            deploymentMap.put(deploymentEntry.getKey(), endpoint);
          }
        }
        if (!deploymentMap.isEmpty()) {
          appMap.put(appEntry.getKey(), deploymentMap);
        }
      }
      if (!appMap.isEmpty()) {
        versions.put(versionEntry.getKey(), appMap);
      }
    }
    return versions;
  }

  static Map<String, Object> sanitizeV2Endpoint(JsonNode endpoint) {
    Map<String, Object> info = new LinkedHashMap<>();
    if (endpoint == null || !endpoint.isObject()) {
      return info;
    }
    JsonNode uri = endpoint.get("uri");
    if (uri != null && uri.isTextual() && !uri.asText().isBlank()) {
      info.put("uri", ClowderDependencyInfoContributor.redactUserInfo(uri.asText()));
    }
    JsonNode authenticated = endpoint.get("authenticated");
    if (authenticated != null && authenticated.isBoolean()) {
      info.put("authenticated", authenticated.asBoolean());
    }
    return info;
  }

  private static void putText(Map<String, Object> info, JsonNode endpoint, String field) {
    JsonNode value = endpoint.get(field);
    if (value != null && value.isTextual() && !value.asText().isBlank()) {
      info.put(field, value.asText());
    }
  }
}
