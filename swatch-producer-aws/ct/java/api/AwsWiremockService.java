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
package api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.api.WiremockService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.marketplacemetering.model.BatchMeterUsageRequest;
import software.amazon.awssdk.services.marketplacemetering.model.UsageRecord;

public class AwsWiremockService extends WiremockService {

  private static final String AWS_USAGE_EVENT_PATH = "/mock/aws";
  private static final String AWS_USAGE_CONTEXT_PATH =
      "/mock/contractApi/api/swatch-contracts/internal/subscriptions/awsUsageContext.*";
  private static final String BATCH_METER_USAGE_TARGET = "AWSMPMeteringService.BatchMeterUsage";
  private static final int CONTRACT_API_STUB_PRIORITY = 9;
  private static final String SUBSCRIPTION_RECENTLY_TERMINATED_CODE = "CONTRACTS1005";

  /**
   * Sets up the AWS usage context endpoint (contracts API) and a wiremock stub for the AWS
   * Marketplace BatchMeterUsage endpoint (at /mock/aws).
   */
  public void setupAwsUsageContext(
      String awsAccountId,
      String awsSellerAccountId,
      String rhSubscriptionId,
      String customerId,
      String productCode) {
    setupAwsUsageContext(
        awsAccountId, awsSellerAccountId, rhSubscriptionId, customerId, productCode, null);
  }

  public void setupAwsUsageContext(
      String awsAccountId,
      String awsSellerAccountId,
      String rhSubscriptionId,
      String customerId,
      String productCode,
      String customerAwsAccountId) {
    var contextData = new HashMap<String, Object>();
    contextData.put("rhSubscriptionId", rhSubscriptionId);
    contextData.put("customerId", customerId);
    contextData.put("productCode", productCode);
    contextData.put("awsSellerAccountId", awsSellerAccountId);
    contextData.put("subscriptionStartDate", "2020-01-01T00:00:00Z");
    if (customerAwsAccountId != null) {
      contextData.put("customerAwsAccountId", customerAwsAccountId);
    }

    registerAwsUsageContextStub(awsAccountId, jsonResponse(200, contextData));
    setupAwsBatchMeterUsage();
  }

  public void setupAwsUsageContextToReturnSubscriptionRecentlyTerminated(String awsAccountId) {
    registerAwsUsageContextStub(
        awsAccountId, jsonResponse(404, subscriptionRecentlyTerminatedErrorBody()));
  }

  public void setupAwsUsageContextToReturnSubscriptionNotFound(String awsAccountId) {
    registerAwsUsageContextStub(awsAccountId, statusOnlyResponse(404));
  }

  private void registerAwsUsageContextStub(String awsAccountId, Map<String, Object> response) {
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                awsUsageContextRequest(awsAccountId),
                "response",
                response,
                // the default mapping defined in config/wiremock uses priority 10,
                // so we need a higher priority here.
                "priority",
                CONTRACT_API_STUB_PRIORITY,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  private static Map<String, Object> awsUsageContextRequest(String awsAccountId) {
    return Map.of(
        "method",
        "GET",
        "urlPathPattern",
        AWS_USAGE_CONTEXT_PATH,
        "queryParameters",
        Map.of("awsAccountId", Map.of("equalTo", awsAccountId)));
  }

  private static Map<String, Object> jsonResponse(int status, Object jsonBody) {
    return Map.of(
        "status",
        status,
        "headers",
        Map.of("Content-Type", "application/json"),
        "jsonBody",
        jsonBody);
  }

  private static Map<String, Object> statusOnlyResponse(int status) {
    return Map.of("status", status);
  }

  private static Map<String, Object> subscriptionRecentlyTerminatedErrorBody() {
    return Map.of(
        "code",
        SUBSCRIPTION_RECENTLY_TERMINATED_CODE,
        "status",
        "404",
        "title",
        "Subscription recently terminated",
        "detail",
        "");
  }

  public void verifyBatchMeterUsageCustomerIdentifier(String expectedCustomerIdentifier) {
    UsageRecord usageRecord = findLatestBatchMeterUsageUsageRecord();
    assertEquals(
        expectedCustomerIdentifier,
        usageRecord.customerIdentifier(),
        "customerIdentifier mismatch");
    assertFieldAbsent("customerAWSAccountId", usageRecord.customerAWSAccountId());
  }

  public void verifyBatchMeterUsageCustomerAwsAccountId(String expectedCustomerAwsAccountId) {
    UsageRecord usageRecord = findLatestBatchMeterUsageUsageRecord();
    assertEquals(
        expectedCustomerAwsAccountId,
        usageRecord.customerAWSAccountId(),
        "customerAWSAccountId mismatch");
    assertFieldAbsent("customerIdentifier", usageRecord.customerIdentifier());
  }

  private static void assertFieldAbsent(String fieldName, String value) {
    assertTrue(
        value == null || value.isBlank(),
        "Unexpected %s in BatchMeterUsage UsageRecord: %s".formatted(fieldName, value));
  }

  private UsageRecord findLatestBatchMeterUsageUsageRecord() {
    BatchMeterUsageRequest batchRequest = findLatestBatchMeterUsageRequest();
    if (batchRequest.usageRecords() == null || batchRequest.usageRecords().isEmpty()) {
      throw new AssertionError("BatchMeterUsage request missing UsageRecords: " + batchRequest);
    }
    return batchRequest.usageRecords().get(0);
  }

  private BatchMeterUsageRequest findLatestBatchMeterUsageRequest() {
    var response =
        given().when().get("/__admin/requests").then().statusCode(200).extract().response();

    try {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode responseJson = objectMapper.readTree(response.getBody().asString());
      JsonNode requests = responseJson.get("requests");

      if (requests == null || requests.isEmpty()) {
        throw new AssertionError("No requests found in Wiremock");
      }

      BatchMeterUsageRequest matchingRequest = null;
      for (JsonNode requestNode : requests) {
        JsonNode request = requestNode.get("request");
        if (request == null) {
          continue;
        }

        JsonNode urlNode = request.get("url");
        JsonNode methodNode = request.get("method");
        JsonNode bodyNode = request.get("body");
        if (urlNode == null || methodNode == null || bodyNode == null) {
          continue;
        }

        String url = urlNode.asText();
        String method = methodNode.asText();
        if (!url.contains(AWS_USAGE_EVENT_PATH) || !"POST".equals(method)) {
          continue;
        }

        String requestBody = bodyNode.asText();
        if (requestBody == null || requestBody.isBlank()) {
          continue;
        }

        matchingRequest = BatchMeterUsageRequestParser.parse(requestBody);
      }

      if (matchingRequest == null) {
        throw new AssertionError("No BatchMeterUsage requests found at " + AWS_USAGE_EVENT_PATH);
      }

      return matchingRequest;
    } catch (AssertionError e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to verify BatchMeterUsage request: " + e.getMessage(), e);
    }
  }

  private void setupAwsBatchMeterUsage() {
    // The AWS SDK v2 sends JSON (application/x-amz-json-1.1) with the X-Amz-Target header
    // to identify the operation. We return a minimal valid BatchMeterUsage JSON response.
    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "POST",
                    "urlPathPattern",
                    AWS_USAGE_EVENT_PATH + ".*",
                    "headers",
                    Map.of("X-Amz-Target", Map.of("equalTo", BATCH_METER_USAGE_TARGET))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/x-amz-json-1.1"),
                    "jsonBody",
                    Map.of(
                        "Results",
                        List.of(Map.of("MeteringRecordId", "test-record-id", "Status", "Success")),
                        "UnprocessedRecords",
                        List.of())),
                "priority",
                9,
                "metadata",
                getMetadataTags()))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  public void verifyNoAwsUsage() {
    var count = countRequests(AWS_USAGE_EVENT_PATH);
    if (count > 0) {
      throw new AssertionError(
          "Unexpected "
              + count
              + " BatchMeterUsage request(s) found at "
              + AWS_USAGE_EVENT_PATH
              + " but none were expected");
    }
  }
}
