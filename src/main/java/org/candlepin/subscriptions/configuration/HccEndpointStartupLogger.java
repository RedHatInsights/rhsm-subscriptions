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
package org.candlepin.subscriptions.configuration;

import com.redhat.swatch.kessel.EndpointLogSanitizer;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.candlepin.subscriptions.rbac.KesselProperties;
import org.candlepin.subscriptions.rbac.RbacProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/** Report effective destinations after Spring binds configuration, without contacting HCC. */
@Slf4j
@RequiredArgsConstructor
public class HccEndpointStartupLogger {
  private final RbacProperties rbac;
  private final KesselProperties kessel;
  private final HttpClientProperties export;

  @EventListener(ApplicationReadyEvent.class)
  public void onStartup() {
    destinations()
        .forEach(
            (service, destination) ->
                log.info(
                    "HCC endpoint resolved: service={}, destination={}", service, destination));
  }

  Map<String, String> destinations() {
    Map<String, String> endpoints = new LinkedHashMap<>();
    if (rbac != null) {
      endpoints.put(
          "rbac", rbac.isUseStub() ? "<stub>" : EndpointLogSanitizer.resolve(rbac::getUrl));
    }
    if (kessel != null) {
      endpoints.put("kessel", EndpointLogSanitizer.resolve(kessel::getEndpoint));
      endpoints.put(
          "rbac-workspaces",
          EndpointLogSanitizer.resolve(
              () -> {
                String endpoint = kessel.getRbacBaseEndpoint();
                if ((endpoint == null || endpoint.isBlank()) && rbac != null) {
                  return rbac.getUrl();
                }
                return endpoint;
              }));
    }
    if (export != null) {
      endpoints.put(
          "export", export.isUseStub() ? "<stub>" : EndpointLogSanitizer.resolve(export::getUrl));
    }
    return endpoints;
  }
}
