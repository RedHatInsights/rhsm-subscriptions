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
package com.redhat.swatch.aws.test.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.swatch.clients.swatch.internal.subscription.api.model.AwsUsageContext;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageResponse;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResult;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecordResultStatus;

/** Mocked services for swatch-producer-aws. */
@Slf4j
public class WiremockRunner {
  public static void main(String[] args) {
    int wiremockPort =
        Integer.parseInt(Optional.ofNullable(System.getenv("WIREMOCK_PORT")).orElse("8101"));
    System.out.printf("Running mock services on port %d%n", wiremockPort);
    WireMockServer wireMockServer =
        new WireMockServer(
            WireMockConfiguration.options().port(wiremockPort).notifier(new ConsoleNotifier(true)));
    try {
      configureMockSwatchInternalSubscriptionService(wireMockServer);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    configureAwsBatchMeterApi(wireMockServer);
    wireMockServer.start();
  }

  // NOTE: if this gets unwieldy, we can move stubbing to separate classes
  private static void configureMockSwatchInternalSubscriptionService(WireMockServer wireMockServer)
      throws JsonProcessingException {
    String awsCustomerId =
        Optional.ofNullable(System.getenv("WIREMOCK_AWS_CUSTOMER_ID")).orElse("customer123");
    String awsProductCode =
        Optional.ofNullable(System.getenv("WIREMOCK_AWS_PRODUCT_CODE")).orElse("productCode");
    ObjectMapper mapper = new ObjectMapper();
    wireMockServer.stubFor(
        any(urlMatching("/api/rhsm-subscriptions/v1/?.*"))
            .withQueryParam("accountNumber", equalTo("account123"))
            .withQueryParam("orgId", equalTo("org123"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        mapper.writeValueAsString(
                            new AwsUsageContext()
                                .customerId(awsCustomerId)
                                .productCode(awsProductCode)
                                .awsSellerAccountId("awsSellerAccountId")
                                .rhSubscriptionId("rhSubscriptionId")
                                .subscriptionStartDate(OffsetDateTime.now().minusDays(1))))));
    wireMockServer.stubFor(
        any(urlMatching("/api/rhsm-subscriptions/v1/?.*"))
            .withQueryParam("accountNumber", equalTo("account1234"))
            .withQueryParam("orgId", equalTo("org1234"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        mapper.writeValueAsString(
                            new AwsUsageContext()
                                .customerId(awsCustomerId)
                                .productCode(awsProductCode)
                                .awsSellerAccountId("role_arn_test")
                                .rhSubscriptionId("rhSubscriptionId")
                                .subscriptionStartDate(OffsetDateTime.now().minusDays(1))))));
    wireMockServer.stubFor(
        any(urlMatching("/api/rhsm-subscriptions/v1/?.*"))
            .withQueryParam("accountNumber", equalTo("unconfigured"))
            .withQueryParam("orgId", equalTo("unconfigured"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        mapper.writeValueAsString(
                            new AwsUsageContext()
                                .customerId(awsCustomerId)
                                .productCode(awsProductCode)
                                .awsSellerAccountId("unconfigured")
                                .rhSubscriptionId("rhSubscriptionId")
                                .subscriptionStartDate(OffsetDateTime.now().minusDays(1))))));
    // last stub has highest prio, so this effectively short-circuits any request without the header
    // at 401
    wireMockServer.stubFor(
        any(urlMatching("/api/rhsm-subscriptions/v1/?.*"))
            .withHeader("x-rh-swatch-psk", notMatching("placeholder"))
            .willReturn(aResponse().withStatus(401)));
  }

  // NOTE: if this gets unwieldy, we can move stubbing to separate classes
  private static void configureAwsBatchMeterApi(WireMockServer wireMockServer) {
    // using raw ObjectMapper here because AWS SDK doesn't provide an easy way to serialize these
    ObjectMapper mapper = new ObjectMapper();
    mapper.findAndRegisterModules();
    mapper.setPropertyNamingStrategy(new PropertyNamingStrategies.UpperCamelCaseStrategy());
    BatchMeterUsageResponse response =
        BatchMeterUsageResponse.builder()
            .results(
                UsageRecordResult.builder()
                    .meteringRecordId("record1")
                    .usageRecord(
                        UsageRecord.builder()
                            .customerIdentifier("customer123")
                            .dimension("units")
                            .timestamp(OffsetDateTime.parse("2022-04-04T00:00Z").toInstant())
                            .build())
                    .status(UsageRecordResultStatus.SUCCESS)
                    .build())
            .build();
    try {
      wireMockServer.stubFor(
          any(urlEqualTo("/aws-marketplace/"))
              .willReturn(aResponse().withBody(mapper.writeValueAsString(response.toBuilder()))));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
