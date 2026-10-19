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
package com.redhat.swatch.contract.config;

import static com.redhat.swatch.clowder.DependencyEndpointSelection.MODE_AUTO;

import com.redhat.swatch.clowder.DependencyEndpoint;
import com.redhat.swatch.clowder.DependencyEndpointResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Wires export-service Clowder V1/V2 properties into a shared dependency endpoint resolver. */
@ApplicationScoped
public class ExportServiceClowderConfig {

  public static final String EXPORT_SERVICE = "export-service";
  public static final String MODE_PROPERTY = "EXPORT_SERVICE_ENDPOINT_MODE";

  @ConfigProperty(name = MODE_PROPERTY, defaultValue = MODE_AUTO)
  String mode;

  @ConfigProperty(name = "EXPORT_SERVICE_ENDPOINT")
  Optional<String> v1Uri;

  @ConfigProperty(
      name = "clowder.optional-private-endpoints.export-service-service.trust-store-path")
  Optional<String> v1TrustStorePath;

  @ConfigProperty(
      name = "clowder.optional-private-endpoints.export-service-service.trust-store-password")
  Optional<String> v1TrustStorePassword;

  @ConfigProperty(
      name = "clowder.optional-private-endpoints.export-service-service.trust-store-type")
  Optional<String> v1TrustStoreType;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_URI")
  Optional<String> v2Uri;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_AUTHENTICATED")
  Optional<String> v2Authenticated;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_TRUST_STORE_PATH")
  Optional<String> v2TrustStorePath;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_TRUST_STORE_PASSWORD")
  Optional<String> v2TrustStorePassword;

  @ConfigProperty(name = "EXPORT_SERVICE_V2_TRUST_STORE_TYPE")
  Optional<String> v2TrustStoreType;

  @Produces
  @ApplicationScoped
  @Named(EXPORT_SERVICE)
  DependencyEndpointResolver exportServiceEndpointResolver() {
    DependencyEndpoint v1 =
        DependencyEndpoint.v1(
            v1Uri.orElse(null),
            v1TrustStorePath.orElse(null),
            v1TrustStorePassword.orElse(null),
            v1TrustStoreType.orElse(null));
    DependencyEndpoint v2 =
        DependencyEndpoint.of(
            v2Uri.orElse(null),
            v2TrustStorePath.orElse(null),
            v2TrustStorePassword.orElse(null),
            v2TrustStoreType.orElse(null),
            DependencyEndpoint.parseAuthenticated(v2Authenticated.orElse(null)));
    return new DependencyEndpointResolver(EXPORT_SERVICE, mode, v1, v2);
  }
}
