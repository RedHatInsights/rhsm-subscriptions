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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.SERVICE_INSTANCE_INGRESS;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

public class TallySummaryComponentTest extends BaseTallyComponentTest {

  private static final String TEST_ORG_ID = "12345678";
  private static final String TEST_PRODUCT_ID = "RHEL for x86";
  private static final String TEST_METRIC_ID = "vCPUs";
  private static final String TEST_INSTANCE_ID = "test-instance-123";

  @Test
  public void testTallySummaryHourlyGranularity() {
    // Create test event payload
    TallyTestHelpers helpers = new TallyTestHelpers();
    Event event =
        helpers.createEventPayload(
            "test-source",
            "test-event-type",
            TEST_ORG_ID,
            TEST_INSTANCE_ID,
            "Test Instance",
            10.0f,
            TEST_METRIC_ID);

    // Produce event to Kafka
    kafkaBridge.produceKafkaMessage(SERVICE_INSTANCE_INGRESS, event);

    // Sync tally for the organization
    try {
      helpers.syncTallyByOrgId(TEST_ORG_ID, service);
    } catch (Exception e) {
      throw new RuntimeException("Failed to sync tally", e);
    }

    // Wait for tally message to be produced
    kafkaBridge.waitForKafkaMessage(
        TALLY, message -> message.contains(TEST_ORG_ID) && message.contains(TEST_METRIC_ID), 1);

    // Call tally endpoint with hourly granularity
    OffsetDateTime ending = OffsetDateTime.now();
    OffsetDateTime beginning = ending.minusHours(24);
    String beginningStr = beginning.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    String endingStr = ending.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    service
        .given()
        .header("x-rh-identity", createTestIdentityHeader(TEST_ORG_ID))
        .queryParam("granularity", "HOURLY")
        .queryParam("beginning", beginningStr)
        .queryParam("ending", endingStr)
        .when()
        .get("/v1/tally/products/{productId}/{metricId}", TEST_PRODUCT_ID, TEST_METRIC_ID)
        .then()
        .statusCode(200);
  }

  @Test
  public void testTallySummaryDailyGranularity() {
    // Create test event payload
    TallyTestHelpers helpers = new TallyTestHelpers();
    Event event =
        helpers.createEventPayload(
            "test-source",
            "test-event-type",
            TEST_ORG_ID,
            TEST_INSTANCE_ID,
            "Test Instance",
            15.0f,
            TEST_METRIC_ID);

    // Produce event to Kafka
    kafkaBridge.produceKafkaMessage(SERVICE_INSTANCE_INGRESS, event);

    // Sync tally for the organization
    try {
      helpers.syncTallyByOrgId(TEST_ORG_ID, service);
    } catch (Exception e) {
      throw new RuntimeException("Failed to sync tally", e);
    }

    // Wait for tally message to be produced
    kafkaBridge.waitForKafkaMessage(
        TALLY, message -> message.contains(TEST_ORG_ID) && message.contains(TEST_METRIC_ID), 1);

    // Call tally endpoint with daily granularity
    OffsetDateTime ending = OffsetDateTime.now();
    OffsetDateTime beginning = ending.minusDays(30);
    String beginningStr = beginning.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    String endingStr = ending.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    service
        .given()
        .header("x-rh-identity", createTestIdentityHeader(TEST_ORG_ID))
        .queryParam("granularity", "DAILY")
        .queryParam("beginning", beginningStr)
        .queryParam("ending", endingStr)
        .when()
        .get("/v1/tally/products/{productId}/{metricId}", TEST_PRODUCT_ID, TEST_METRIC_ID)
        .then()
        .statusCode(200);
  }

  private String createTestIdentityHeader(String orgId) {
    // Create a base64 encoded identity header for testing
    String identityJson =
        String.format(
            "{\"identity\":{\"org_id\":\"%s\",\"user\":{\"username\":\"test-user\"}}}", orgId);
    return java.util.Base64.getEncoder().encodeToString(identityJson.getBytes());
  }
}
