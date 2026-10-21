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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.info.InfoContributor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Exposes sanitized Clowder {@code cdappconfig.json} slices on management {@code GET /info}.
 * Includes V1 ({@code endpoints} / {@code privateEndpoints}) and V2 ({@code dependencyEndpoints} /
 * {@code privateDependencyEndpoints}). Additional config sections can be added in the shared reader
 * later. CA paths and URI userinfo are omitted.
 */
@ApplicationScoped
public class ClowderConfigInfoContributor implements InfoContributor {

  public static final String SECTION = "clowder-config";
  public static final String DEFAULT_CONFIG_PATH = "/cdapp/cdappconfig.json";

  private final ObjectMapper objectMapper;
  private final String configPath;

  @Inject
  public ClowderConfigInfoContributor(
      ObjectMapper objectMapper,
      @ConfigProperty(name = "acg.config", defaultValue = DEFAULT_CONFIG_PATH) String configPath) {
    this.objectMapper = objectMapper;
    this.configPath = configPath;
  }

  @Override
  public String name() {
    return SECTION;
  }

  @Override
  public Object data() {
    return ClowderCdappConfig.read(Path.of(configPath), objectMapper);
  }
}
