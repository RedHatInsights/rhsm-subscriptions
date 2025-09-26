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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.api.WiremockService;
import java.util.List;
import java.util.Map;

public class AzureWiremockService extends WiremockService {

  private static final String ANY = "_ANY";
  private static final String AZURE_PLAN_ID = "azure-plan-id";
  private static final String AZURE_OFFER_ID = "azureProductCode";

  public void setupAzureUsageContext(String azureResourceId, String billingAccountId) {
      // 1. Setup Azure Usage Context endpoint (similar to create_get_azure_usage_context_wiremock)
      var contextData =
          Map.of(
              "billing_account_id", billingAccountId,
              "azureResourceId", azureResourceId,
              "planId", AZURE_PLAN_ID,
              "azureOfferId", AZURE_OFFER_ID);
      given()
          .contentType("application/json")
          .body(
              Map.of(
                  "request",
                  Map.of(
                      "method", "GET",
                      "urlPathPattern",
                      "/mock/contractApi/api/swatch-contracts/internal/subscriptions/azureUsageContext.*",
                      "queryParameters",
                      Map.of("azureAccountId", Map.of("equalTo", billingAccountId))),
                  "response",
                  Map.of(
                      "status",
                      200,
                      "headers",
                      Map.of("Content-Type", "application/json"),
                      "jsonBody",
                      contextData),
                  // the default mapping defined in config/wiremock uses priority 10,
                  // so we need an higher priority here.
                  "priority", 9,
                  "metadata", Map.of(METADATA_TAG, "true")))
          .when()
          .post("/__admin/mappings")
          .then()
          .statusCode(201);
      // 2. Setup Azure OAuth Token endpoint (similar to create_get_azure_oauth_token_wiremock)
      setupAzureOAuthToken();
      // 3. Setup Azure Send Usage endpoint (similar to create_send_azure_usage_wiremock)
      setupAzureSendUsage(azureResourceId);
  }

  public void setupAzureUsageContextToReturnSubscriptionNotFound(String billingAccountId) {
      given()
          .contentType("application/json")
          .body(
              Map.of(
                  "request",
                  Map.of(
                      "method",
                      "GET",
                      "urlPathPattern",
                      "/mock/contractApi/api/swatch-contracts/internal/subscriptions/azureUsageContext.*",
                      "queryParameters",
                      Map.of("azureAccountId", Map.of("equalTo", billingAccountId))),
                  "response",
                  Map.of(
                      "status",
                      404),
                  // the default mapping defined in config/wiremock uses priority 10,
                  // so we need a higher priority here.
                  "priority",
                  9,
                  "metadata",
                  Map.of(METADATA_TAG, "true")))
          .when()
          .post("/__admin/mappings")
          .then()
          .statusCode(201);
      // Only setup OAuth for error case (no usage endpoint needed)
      setupAzureOAuthToken();
  }

  public void verifyAzureUsage(
      String azureResourceId, double expectedValue, String expectedDimension) {
    // Get all requests to the Azure usage endpoint
    var response =
        given().when().get("/__admin/requests").then().statusCode(200).extract().response();

    try {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode responseJson = objectMapper.readTree(response.getBody().asString());
      JsonNode requests = responseJson.get("requests");

      if (requests == null || requests.isEmpty()) {
        throw new AssertionError("No requests found in Wiremock");
      }

      // Find Azure usage requests with matching resourceId
      JsonNode matchingRequest = null;
      for (JsonNode requestNode : requests) {
        JsonNode request = requestNode.get("request");
        JsonNode urlNode = request.get("url");
        JsonNode methodNode = request.get("method");
        JsonNode bodyNode = request.get("body");

        if (urlNode == null || methodNode == null || bodyNode == null) {
          continue; // Skip invalid requests
        }

        String url = urlNode.asText();
        String method = methodNode.asText();

        if (url.contains("/mock/azure/api/usageEvent") && "POST".equals(method)) {
          String requestBody = bodyNode.asText();
          if (requestBody != null && !requestBody.isEmpty()) {
            JsonNode usage = objectMapper.readTree(requestBody);
            JsonNode resourceIdNode = usage.get("resourceId");

            if (resourceIdNode != null && azureResourceId.equals(resourceIdNode.asText())) {
              matchingRequest = request;
              break;
            }
          }
        }
      }

      if (matchingRequest == null) {
        throw new AssertionError(
            "No Azure usage requests found for resourceId: " + azureResourceId);
      }

      // Parse the matching request body
      String requestBody = matchingRequest.get("body").asText();
      JsonNode usage = objectMapper.readTree(requestBody);

      // Verify the usage data with null checks
      JsonNode quantityNode = usage.get("quantity");
      JsonNode dimensionNode = usage.get("dimension");
      JsonNode effectiveStartTimeNode = usage.get("effectiveStartTime");
      JsonNode planIdNode = usage.get("planId");

      if (quantityNode == null
          || dimensionNode == null
          || effectiveStartTimeNode == null
          || planIdNode == null) {
        throw new AssertionError(
            "Missing required fields in Azure usage request body: " + requestBody);
      }

      assertEquals(expectedValue, quantityNode.asDouble(), "quantity mismatch");
      assertEquals(expectedDimension, dimensionNode.asText(), "dimension mismatch");
      assertEquals(AZURE_PLAN_ID, planIdNode.asText(), "planId mismatch");

    } catch (Exception e) {
      throw new RuntimeException("Failed to verify Azure usage: " + e.getMessage(), e);
    }
  }

  public void verifyNoAzureUsage() {
    verifyNoAzureUsage(ANY);
  }

  public void verifyNoAzureUsage(String azureResourceId) {
    // Get all requests to the Azure usage endpoint
    var response =
        given().when().get("/__admin/requests").then().statusCode(200).extract().response();

    try {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode responseJson = objectMapper.readTree(response.getBody().asString());
      JsonNode requests = responseJson.get("requests");

      if (requests == null || requests.isEmpty()) {
        // No requests found, which means no Azure usage was sent - this is what we expect
        return;
      }

      // Check if any Azure usage requests were made for the given resourceId
      for (JsonNode requestNode : requests) {
        JsonNode request = requestNode.get("request");
        JsonNode urlNode = request.get("url");
        JsonNode methodNode = request.get("method");
        JsonNode bodyNode = request.get("body");

        if (urlNode == null || methodNode == null || bodyNode == null) {
          continue; // Skip invalid requests
        }

        String url = urlNode.asText();
        String method = methodNode.asText();

        if (url.contains("/mock/azure/api/usageEvent") && "POST".equals(method)) {
          String requestBody = bodyNode.asText();
          if (requestBody != null && !requestBody.isEmpty()) {
            JsonNode usage = objectMapper.readTree(requestBody);
            JsonNode resourceIdNode = usage.get("resourceId");

            if (resourceIdNode != null
                && (ANY.equals(azureResourceId)
                    || azureResourceId.equals(resourceIdNode.asText()))) {
              throw new AssertionError(
                  "Azure usage request was found for resourceId: "
                      + resourceIdNode.asText()
                      + " but none was expected due to invalid data");
            }
          }
        }
      }

      // No Azure usage requests found for the resourceId - this is what we expect

    } catch (Exception e) {
      throw new RuntimeException("Failed to verify no Azure usage: " + e.getMessage(), e);
    }
  }

  private void setupAzureOAuthToken() {
    // Setup Azure OAuth token endpoint
    var tokenResponse =
        Map.of(
            "token_type", "Bearer",
            "expires_in", "3599",
            "ext_expires_in", "3599",
            "access_token", "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6Ik5HVEZ2ZEstZnl0aEV1Q");

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method",
                    "POST",
                    "urlPathPattern",
                    "/mock/azure/.*",
                    "headers",
                    Map.of(
                        "Content-Type", Map.of("equalTo", "application/x-www-form-urlencoded"),
                        "Authorization", Map.of("equalTo", "Basic dGVzdDp0ZXN0")),
                    "bodyPatterns",
                    List.of(
                        Map.of(
                            "and",
                            List.of(
                                Map.of("contains", "grant_type=client_credentials"),
                                Map.of("contains", "resource"))))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    tokenResponse),
                "metadata", Map.of(METADATA_TAG, "true")))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }

  private void setupAzureSendUsage(String azureResourceId) {
    // Setup Azure send usage endpoint
    var usageResponse =
        Map.of(
            "usageEventId",
            java.util.UUID.randomUUID().toString(),
            "status",
            "Accepted",
            "messageTime",
            java.time.Instant.now().toString(),
            "resourceId",
            azureResourceId,
            "planId",
            AZURE_PLAN_ID);

    given()
        .contentType("application/json")
        .body(
            Map.of(
                "request",
                Map.of(
                    "method", "POST",
                    "urlPathPattern", "/mock/azure/.*",
                    "headers", Map.of("Authorization", Map.of("matches", "Bearer .*"))),
                "response",
                Map.of(
                    "status",
                    200,
                    "headers",
                    Map.of("Content-Type", "application/json"),
                    "jsonBody",
                    usageResponse),
                "metadata", Map.of(METADATA_TAG, "true")))
        .when()
        .post("/__admin/mappings")
        .then()
        .statusCode(201);
  }
}