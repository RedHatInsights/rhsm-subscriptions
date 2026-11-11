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

import com.redhat.swatch.clients.ClientConfig;
import com.redhat.swatch.clients.rbac.RbacService;
import com.redhat.swatch.clients.rbac.SwatchRbacApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RbacClientConfiguration {

  public static final String RBAC_SERVICE = "rbac-service";

  @ConfigProperty(name = "RBAC_API_ENDPOINT", defaultValue = "http://localhost:8006/api/rbac/v1")
  String endpoint;

  @ConfigProperty(name = "RBAC_READ_TIMEOUT", defaultValue = "120s")
  Duration readTimeout;

  @ConfigProperty(name = "RBAC_MAX_CONNECTIONS", defaultValue = "100")
  int maxConnections;

  @ConfigProperty(name = "RBAC_TRUST_STORE_PATH")
  Optional<String> trustStorePath;

  @ConfigProperty(name = "RBAC_TRUST_STORE_PASSWORD")
  Optional<String> trustStorePassword;

  @ConfigProperty(name = "RBAC_TRUST_STORE_TYPE")
  Optional<String> trustStoreType;

  @Produces
  @ApplicationScoped
  SwatchRbacApi rbacApi() {
    ClientConfig config =
        new ClientConfig(
            RBAC_SERVICE,
            null,
            endpoint,
            null,
            trustStorePath.orElse(null),
            trustStorePassword.map(String::toCharArray).orElse(null),
            trustStoreType.orElse(null),
            readTimeout,
            maxConnections);
    return new SwatchRbacApi(config);
  }

  @Produces
  @ApplicationScoped
  RbacService rbacService(SwatchRbacApi rbacApi) {
    return new RbacService(rbacApi);
  }
}
