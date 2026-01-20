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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;

/**
 * Tests to verify that Tally Report and Instance report endpoints are not changing persists
 * correctly when called with different time ranges. This test verifies that re-running tally
 * doesn't change the already calculated tally data for previous periods.
 *
 * <p><a href="https://issues.redhat.com/browse/ENT-3713">...</a>
 */
public class TallyPersistenceTest extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final String TEST_PRODUCT_TAG = "OpenShift-dedicated-metrics";
  private static final String TEST_PRODUCT_ID = "openshift-dedicated-metrics";
  private static final String TEST_METRIC_ID = "Instance-hours";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private TestSetup setup;

  @BeforeEach
  public void setUp() throws Exception {
    setup = setupTest();
  }

  @Test
  public void testTallyReportPersistsWithDateTimeRangeVariations() throws Exception {
    // Run initial tally after setup
    helpers.syncTallyHourly(setup.orgId, service);
    Thread.sleep(3000);

    // Get initial tally reports
    JsonNode todayTallyBefore = getTallyReportJson(setup.today);
    JsonNode yesterdayTallyBefore = getTallyReportJson(setup.yesterday);

    // Run hourly tally again
    helpers.syncTallyHourly(setup.orgId, service);
    Thread.sleep(3000);

    // Get tally reports after second tally
    JsonNode todayTallyAfter = getTallyReportJson(setup.today);
    JsonNode yesterdayTallyAfter = getTallyReportJson(setup.yesterday);

    // Verify persistence - tally reports should not change
    Assertions.assertEquals(
        todayTallyBefore, todayTallyAfter, "Today's tally should not change after re-tally");
    Assertions.assertEquals(
        yesterdayTallyBefore,
        yesterdayTallyAfter,
        "Yesterday's tally should not change after re-tally");
  }

  @Test
  public void testInstanceReportPersistsWithDateTimeRangeVariations() throws Exception {
    // Run initial tally
    helpers.syncTallyHourly(setup.orgId, service);
    Thread.sleep(3000);

    // Get initial instances report for yesterday
    JsonNode yesterdayInstancesBefore = getInstancesReportJson(setup.yesterday);

    // Run hourly tally again
    helpers.syncTallyHourly(setup.orgId, service);
    Thread.sleep(3000);

    // Get instances report after second tally
    JsonNode yesterdayInstancesAfter = getInstancesReportJson(setup.yesterday);

    // Verify persistence - instances report should not change (excluding
    // last_applied_event_record_date)
    Assertions.assertEquals(
        removeLastAppliedEventDate(yesterdayInstancesBefore),
        removeLastAppliedEventDate(yesterdayInstancesAfter),
        "Yesterday's instances report should not change after re-tally");
  }

  private TestSetup setupTest() throws Exception {
    String orgId = RandomUtils.generateRandom();
    OffsetDateTime now = OffsetDateTime.now();

    helpers.createOptInConfig(orgId, service);

    // Calculate day ranges (today and yesterday)
    // TallyState initializes with latestEventRecordDate = start of yesterday
    OffsetDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime startOfYesterday = startOfToday.minusDays(1);
    OffsetDateTime endOfToday = startOfToday.plusDays(1).minusSeconds(1).minusNanos(1);
    OffsetDateTime endOfYesterday = startOfToday.minusSeconds(1).minusNanos(1);

    // Create events with timestamps after start of yesterday
    OffsetDateTime yesterdayEventTime = startOfYesterday.plusHours(6).truncatedTo(ChronoUnit.HOURS);
    OffsetDateTime todayEventTime = startOfToday.plusHours(2).truncatedTo(ChronoUnit.HOURS);

    createEvent(orgId, yesterdayEventTime, 3.0f);
    createEvent(orgId, todayEventTime, 2.0f);
    Thread.sleep(2000);

    return new TestSetup(
        orgId,
        new DateRange(startOfToday, endOfToday),
        new DateRange(startOfYesterday, endOfYesterday));
  }

  private void createEvent(String orgId, OffsetDateTime timestamp, float value) {
    Event event =
        helpers.createEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    event.setServiceType("OpenShift Cluster");
    event.setRole(Event.Role.OSD);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private JsonNode getTallyReportJson(DateRange range) throws Exception {
    return objectMapper.readTree(
        helpers
            .getTallyReport(
                setup.orgId,
                TEST_PRODUCT_TAG,
                TEST_METRIC_ID,
                "Hourly",
                range.start(),
                range.end(),
                service)
            .asString());
  }

  private JsonNode getInstancesReportJson(DateRange range) throws Exception {
    return objectMapper.readTree(
        helpers
            .getInstancesReport(setup.orgId, TEST_PRODUCT_TAG, range.start(), range.end(), service)
            .asString());
  }

  private JsonNode removeLastAppliedEventDate(JsonNode json) {
    if (json.has("data") && json.get("data").isArray() && !json.get("data").isEmpty()) {
      com.fasterxml.jackson.databind.node.ObjectNode firstItem =
          (com.fasterxml.jackson.databind.node.ObjectNode) json.get("data").get(0);
      firstItem.remove("last_applied_event_record_date");
    }
    return json;
  }

  private record DateRange(OffsetDateTime start, OffsetDateTime end) {}

  private record TestSetup(String orgId, DateRange today, DateRange yesterday) {}
}
