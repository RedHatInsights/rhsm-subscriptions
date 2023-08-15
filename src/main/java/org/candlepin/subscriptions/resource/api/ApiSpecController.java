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
package org.candlepin.subscriptions.resource.api;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.candlepin.subscriptions.exception.ErrorCode;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Fetches and returns the OpenAPI spec in either JSON or YAML format.
 *
 * <p>Pulls the spec files from the API jar.
 */
@Component
public class ApiSpecController {
  // The name these files are served as is controlled by the corresponding Resource
  // implementation (and ultimately in the openAPI spec itself)
  @Value("classpath:rhsm-subscriptions-api-spec.yaml")
  private Resource openApiYaml;

  @Value("classpath:openapi.json")
  private Resource openApiJson;

  @Value("classpath:internal-subscriptions-sync-api-spec.yaml")
  private Resource internalSubSyncApiYaml;

  @Value("classpath:internal-subscription-sync-openapi.json")
  private Resource internalSubSyncApiJson;

  @Value("classpath:internal-tally-api-spec.yaml")
  private Resource internalTallyApiYaml;

  @Value("classpath:internal-tally-openapi.json")
  private Resource internalTallyApiJson;

  @Value("classpath:internal-metering-api-spec.yaml")
  private Resource internalMeteringApiYaml;

  @Value("classpath:internal-metering-openapi.json")
  private Resource internalMeteringApiJson;

  @Value("classpath:internal-billing-api-spec.yaml")
  private Resource internalBillingApiYaml;

  @Value("classpath:internal-billing-openapi.json")
  private Resource internalBillingApiJson;

  @Value("classpath:internal-swatch-producer-red-hat-marketplace-openapi.json")
  private Resource internalProducerRedHatMarketplaceApiYaml;

  @Value("classpath:internal-producer-red-hat-marketplace-api-openapi.json")
  private Resource internalProducerRedHatMarketplaceApiJson;

  private String getResourceAsString(Resource r) {
    try (InputStream is = r.getInputStream()) {
      return IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new SubscriptionsException(
          ErrorCode.UNHANDLED_EXCEPTION_ERROR,
          Response.Status.INTERNAL_SERVER_ERROR,
          String.format("Unable to read %s", r.getFilename()),
          e.getMessage());
    }
  }

  public String getOpenApiJson() {
    return getResourceAsString(openApiJson);
  }

  public String getOpenApiYaml() {
    return getResourceAsString(openApiYaml);
  }

  public String getInternalSubSyncApiYaml() {
    return getResourceAsString(internalSubSyncApiYaml);
  }

  public String getInternalSubSyncApiJson() {
    return getResourceAsString(internalSubSyncApiJson);
  }

  public String getInternalTallyApiYaml() {
    return getResourceAsString(internalTallyApiYaml);
  }

  public String getInternalTallyApiJson() {
    return getResourceAsString(internalTallyApiJson);
  }

  public String getInternalMeteringApiYaml() {
    return getResourceAsString(internalMeteringApiYaml);
  }

  public String getInternalMeteringApiJson() {
    return getResourceAsString(internalMeteringApiJson);
  }

  public String getInternalBillingApiYaml() {
    return getResourceAsString(internalBillingApiYaml);
  }

  public String getInternalBillingApiJson() {
    return getResourceAsString(internalBillingApiJson);
  }

  public String getInternalProducerRHMApiYaml() {
    return getResourceAsString(internalProducerRedHatMarketplaceApiYaml);
  }

  public String getInternalProducerRHMApiJson() {
    return getResourceAsString(internalProducerRedHatMarketplaceApiJson);
  }
}
