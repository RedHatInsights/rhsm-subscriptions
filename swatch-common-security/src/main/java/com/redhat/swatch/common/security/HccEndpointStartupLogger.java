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
package com.redhat.swatch.common.security;

import com.redhat.swatch.kessel.EndpointLogSanitizer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.Config;

/** Report runtime destinations even when the corresponding REST/gRPC clients are still lazy. */
@Slf4j
@ApplicationScoped
public class HccEndpointStartupLogger {
  static final String RBAC_URL =
      "quarkus.rest-client.\"com.redhat.swatch.clients.rbac.api.resources.AccessApi\".url";
  static final String EXPORT_URL =
      "quarkus.rest-client.\"com.redhat.swatch.clients.export.api.resources.ExportApi\".url";

  @Inject Config config;
  @Inject KesselProperties kessel;

  void onStartup(@Observes StartupEvent event) {
    destinations()
        .forEach(
            (service, destination) ->
                log.info(
                    "HCC endpoint resolved: service={}, destination={}", service, destination));
  }

  Map<String, String> destinations() {
    Map<String, String> endpoints = new LinkedHashMap<>();
    if (config
        .getOptionalValue("swatch-common-security.include-rbac", Boolean.class)
        .orElse(true)) {
      endpoints.put("rbac", configuredDestination(RBAC_URL));
      endpoints.put(
          "rbac-workspaces",
          EndpointLogSanitizer.resolve(
              () ->
                  kessel
                      .rbacBaseEndpoint()
                      .filter(s -> !s.isBlank())
                      .orElseGet(
                          () ->
                              config
                                  .getOptionalValue("RBAC_ENDPOINT", String.class)
                                  .orElse(null))));
      endpoints.put("kessel", EndpointLogSanitizer.resolve(kessel::resolvedEndpoint));
    }
    // Export is a Contracts dependency; do not report it in services such as Utilization.
    for (String name : config.getPropertyNames()) {
      if (EXPORT_URL.equals(name) || uriProperty(EXPORT_URL).equals(name)) {
        endpoints.put("export", configuredDestination(EXPORT_URL));
        break;
      }
    }
    return endpoints;
  }

  private String configuredDestination(String property) {
    return EndpointLogSanitizer.resolve(
        () -> {
          // RESTEasy selects the configured URI before URL when both are supplied.
          var uri = config.getOptionalValue(uriProperty(property), String.class);
          if (uri.isPresent()) {
            return uri.get();
          }
          var value = config.getConfigValue(property);
          if (value.getValue() != null) {
            return value.getValue();
          }
          if (value.getRawValue() != null && !value.getRawValue().isBlank()) {
            // Distinguish an unresolved expression from a genuinely absent optional endpoint.
            return config.getValue(property, String.class);
          }
          return null;
        });
  }

  private String uriProperty(String urlProperty) {
    return urlProperty.substring(0, urlProperty.length() - 3) + "uri";
  }
}
