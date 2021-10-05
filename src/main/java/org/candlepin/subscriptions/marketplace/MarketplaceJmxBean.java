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
package org.candlepin.subscriptions.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.subscriptions.json.TallySummary;
import org.candlepin.subscriptions.marketplace.api.model.UsageRequest;
import org.candlepin.subscriptions.resource.ResourceUtils;
import org.candlepin.subscriptions.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/** Exposes admin functions for Marketplace integration. */
@Component
@ManagedResource
// must log, then throw because the exception is passed to client and not logged.
@SuppressWarnings("java:S2139")
public class MarketplaceJmxBean {

  private static final Logger log = LoggerFactory.getLogger(MarketplaceJmxBean.class);
  public static final String USAGE_SUBMISSION_ERROR_MESSAGE = "Error submitting usage info via JMX";

  private final SecurityProperties properties;
  private final MarketplaceProperties mktProperties;
  private final MarketplaceService marketplaceService;
  private final MarketplaceProducer marketplaceProducer;
  private final ObjectMapper mapper;
  private final MarketplacePayloadMapper marketplacePayloadMapper;

  MarketplaceJmxBean(
      SecurityProperties properties,
      MarketplaceProperties mktProperties,
      MarketplaceService marketplaceService,
      MarketplaceProducer marketplaceProducer,
      ObjectMapper mapper,
      MarketplacePayloadMapper marketplacePayloadMapper) {

    this.properties = properties;
    this.mktProperties = mktProperties;
    this.marketplaceService = marketplaceService;
    this.marketplaceProducer = marketplaceProducer;
    this.mapper = mapper;
    this.marketplacePayloadMapper = marketplacePayloadMapper;
  }

  @ManagedOperation(description = "Force a refresh of the access token")
  public void refreshAccessToken() throws ApiException {
    Object principal = ResourceUtils.getPrincipal();
    log.info("Marketplace access token forcibly refreshed by {}", principal);
    marketplaceService.forceRefreshAccessToken();
  }

  @ManagedOperation(
      description =
          "Submit tally summary JSON to be converted to a usage event and send to"
              + " RHM as a UsageRequest (available when enabled "
              + "via MarketplaceProperties.isManualMarketplaceSubmissionEnabled or in dev-mode)")
  @ManagedOperationParameter(
      name = "tallySummaryJson",
      description =
          "String representation of Tally "
              + "Summary json. Don't forget to escape quotation marks if you're trying to invoke this endpoint via "
              + "curl command")
  public void submitTallySummary(String tallySummaryJson) throws JsonProcessingException {
    if (!properties.isDevMode() && !mktProperties.isManualMarketplaceSubmissionEnabled()) {
      throw new JmxException("This feature is not currently enabled.");
    }

    TallySummary tallySummary = mapper.readValue(tallySummaryJson, TallySummary.class);
    UsageRequest usageRequest = marketplacePayloadMapper.createUsageRequest(tallySummary);

    log.info("usageRequest to be sent: {}", usageRequest);

    try {
      marketplaceProducer.submitUsageRequest(usageRequest);
    } catch (Exception e) {
      log.error(USAGE_SUBMISSION_ERROR_MESSAGE, e);
      throw new JmxException(USAGE_SUBMISSION_ERROR_MESSAGE, e);
    }
  }

  @ManagedOperation(
      description =
          "Submit usage JSON to RHM (available when enabled "
              + "via MarketplaceProperties.isManualMarketplaceSubmissionEnabled or in dev-mode)")
  @ManagedOperationParameter(
      name = "usageJson",
      description =
          "String representation of Usage json. "
              + "Don't forget to escape quotation marks if you're trying to invoke this endpoint via "
              + "curl command")
  public void submitUsage(String usageJson) throws JsonProcessingException {
    if (!properties.isDevMode() && !mktProperties.isManualMarketplaceSubmissionEnabled()) {
      throw new JmxException("This feature is not currently enabled.");
    }

    UsageRequest usageRequest = mapper.readValue(usageJson, UsageRequest.class);

    log.info("usageRequest to be sent: {}", usageRequest);

    try {
      marketplaceProducer.submitUsageRequest(usageRequest);
    } catch (Exception e) {
      log.error(USAGE_SUBMISSION_ERROR_MESSAGE, e);
      throw new JmxException(USAGE_SUBMISSION_ERROR_MESSAGE, e);
    }
  }

  @ManagedOperation(description = "Fetch a usage event status")
  public String getUsageEventStatus(String batchId) throws ApiException {
    return marketplaceService.getUsageBatchStatus(batchId).toString();
  }
}
