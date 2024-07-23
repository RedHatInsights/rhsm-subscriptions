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
package com.redhat.swatch.azure.test.resources;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventOkResponse;
import com.redhat.swatch.clients.azure.marketplace.api.model.UsageEventStatusEnum;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AzureUsageContext;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.billable.usage.BillableUsageAggregate;
import org.junit.jupiter.api.Assertions;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

public class WireMockResource implements QuarkusTestResourceLifecycleManager {

  private static final String SUBSCRIPTIONS_AZURE_USAGE_CONTEXT =
      "/api/rhsm-subscriptions/v1/internal/subscriptions/azureUsageContext";
  private static final String AZURE_AUTHORIZATION = "/";
  private static final String AZURE_SUBMIT_USAGE = "/api/usageEvent";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private WireMockServer wireMockServer;

  @Override
  public Map<String, String> start() {
    wireMockServer =
        new WireMockServer(
            WireMockConfiguration.options().dynamicPort().notifier(new ConsoleNotifier(true)));
    wireMockServer.start();
    var config = new HashMap<String, String>();
    config.put("AZURE_MARKETPLACE_BASE_URL", wireMockServer.baseUrl());
    config.put("AZURE_OAUTH_TOKEN_URL", wireMockServer.baseUrl());
    config.put("SWATCH_INTERNAL_SUBSCRIPTION_ENDPOINT", wireMockServer.baseUrl());
    return config;
  }

  @Override
  public void stop() {
    wireMockServer.stop();
    wireMockServer.resetAll();
  }

  @Override
  public void inject(TestInjector testInjector) {
    testInjector.injectIntoFields(
        this,
        new TestInjector.AnnotatedAndMatchesType(InjectWireMock.class, WireMockResource.class));
  }

  public AzureUsageContext stubInternalSubscriptionAzureMarketPlaceContextForUsage(
      BillableUsageAggregate usage) {
    AzureUsageContext context = new AzureUsageContext();
    context.setAzureResourceId(UUID.randomUUID().toString());
    context.setAzureTenantId(UUID.randomUUID().toString());
    context.setOfferId(UUID.randomUUID().toString());
    context.setPlanId(UUID.randomUUID().toString());
    wireMockServer.stubFor(
        get(urlPathEqualTo(SUBSCRIPTIONS_AZURE_USAGE_CONTEXT))
            .withQueryParam("orgId", equalTo(usage.getAggregateKey().getOrgId()))
            .willReturn(okJson(toJson(context))));
    return context;
  }

  public void stubAzureMarketplaceSubmitUsageEventForReturnsOk(AzureUsageContext contextForUsage) {
    stubAzureMarketplaceAuthorization();
    UsageEventOkResponse response = new UsageEventOkResponse();
    response.setStatus(UsageEventStatusEnum.ACCEPTED);
    wireMockServer.stubFor(
        post(urlPathEqualTo(AZURE_SUBMIT_USAGE))
            .withRequestBody(containing(contextForUsage.getPlanId()))
            .willReturn(okJson(toJson(response))));
  }

  public void stubAzureMarketplaceSubmitUsageEventForReturnsStatus(
      AzureUsageContext contextForUsage, int status) {
    stubAzureMarketplaceAuthorization();
    wireMockServer.stubFor(
        post(urlPathEqualTo(AZURE_SUBMIT_USAGE))
            .withRequestBody(containing(contextForUsage.getPlanId()))
            .willReturn(aResponse().withStatus(status)));
  }

  public void stubAzureMarketplaceAuthorization() {
    wireMockServer.stubFor(
        post(urlPathEqualTo(AZURE_AUTHORIZATION))
            .withRequestBody(containing("client_credentials"))
            .willReturn(
                okJson(
                    """
{
  "access_token":"%s",
  "token_type":"Bearer",
  "expires_in":3600,
  "refresh_token":"%s",
  "scope":"create"
}
"""
                        .formatted(UUID.randomUUID().toString(), UUID.randomUUID().toString()))));
  }

  public void verifyUsageIsSentToAzureMarketplace(AzureUsageContext contextForUsage) {
    wireMockServer.verify(
        postRequestedFor(urlPathEqualTo(AZURE_SUBMIT_USAGE))
            .withRequestBody(containing(contextForUsage.getPlanId())));
  }

  private String toJson(Object object) {
    try {
      return OBJECT_MAPPER.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      Assertions.fail(e);
      return null;
    }
  }
}
