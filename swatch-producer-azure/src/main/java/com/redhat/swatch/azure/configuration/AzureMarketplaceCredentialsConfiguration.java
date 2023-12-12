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
package com.redhat.swatch.azure.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.azure.file.AzureMarketplaceCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
@Slf4j
public class AzureMarketplaceCredentialsConfiguration {

  @Inject
  ObjectMapper mapper;

  @ConfigProperty(name = "AZURE_MARKETPLACE_CREDENTIALS")
  private String azureCredentialJson;

  @Getter private AzureMarketplaceCredentials azureMarketplaceCredentials;

  @ApplicationScoped
  @Produces
  public AzureMarketplaceCredentials defaultAzureCredentials() {
    try {
          return mapper.readValue(azureCredentialJson, AzureMarketplaceCredentials.class);
    }
    catch(JsonProcessingException e) {
      log.error("Failed to parse azure credentials json", e);
      return null;
    }
  }
}
