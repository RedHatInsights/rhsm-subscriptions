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

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TallyTestHelpers;

/**
 * Test to verify that tally data persists correctly when running hourly tally sync with different
 * time ranges. This test verifies that re-running tally with a different time range doesn't change
 * the already calculated tally data for previous periods.
 *
 * <p>Based on test_verify_rhel_els_payg_tally_not_changed_with_datetime_range_variations from
 * iqe-rhsm-subscriptions-plugin.
 * 
 * https://issues.redhat.com/browse/ENT-3713
 */
public class TallyPersistenceTest extends BaseTallyComponentTest {

  private static final TallyTestHelpers helpers = new TallyTestHelpers();
  private static final String TEST_PRODUCT_TAG = "RHEL for x86";
  private static final String TEST_PRODUCT_ID = "69";
  private static final String TEST_METRIC_ID = "Sockets";
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private TestSetup setup;

  @BeforeEach
  public void setUp() throws Exception { 
    setup = setupTest();
}

  @Test
  public void testTallyReportPersistsWithDateTimeRangeVariations() throws Exception {
    OffsetDateTime now = OffsetDateTime.now();

    // Run initial tally with 312 hours (13 days) to cover both months
    helpers.syncTallyHourly(setup.orgId, now.minusHours(312), now, service);
    Thread.sleep(5000);

    // Get initial tally reports
    JsonNode currentMonthTallyBefore =
        objectMapper.readTree(
            helpers
                .getTallyReport(
                    setup.orgId,
                    TEST_PRODUCT_TAG,
                    TEST_METRIC_ID,
                    "Daily",
                    setup.currentMonth.start(),
                    setup.currentMonth.end(),
                    service)
                .asString());
    JsonNode lastMonthTallyBefore =
        objectMapper.readTree(
            helpers
                .getTallyReport(
                    setup.orgId,
                    TEST_PRODUCT_TAG,
                    TEST_METRIC_ID,
                    "Daily",
                    setup.lastMonth.start(),
                    setup.lastMonth.end(),
                    service)
                .asString());

    // Run hourly tally again with 48 hours (2 days) - different time range
    helpers.syncTallyHourly(setup.orgId, now.minusHours(48), now, service);
    Thread.sleep(5000);

    // Get tally reports after second tally
    JsonNode currentMonthTallyAfter =
        objectMapper.readTree(
            helpers
                .getTallyReport(
                    setup.orgId,
                    TEST_PRODUCT_TAG,
                    TEST_METRIC_ID,
                    "Daily",
                    setup.currentMonth.start(),
                    setup.currentMonth.end(),
                    service)
                .asString());
    JsonNode lastMonthTallyAfter =
        objectMapper.readTree(
            helpers
                .getTallyReport(
                    setup.orgId,
                    TEST_PRODUCT_TAG,
                    TEST_METRIC_ID,
                    "Daily",
                    setup.lastMonth.start(),
                    setup.lastMonth.end(),
                    service)
                .asString());

    // Verify persistence - tally reports should not change
    Assertions.assertEquals(
        currentMonthTallyBefore,
        currentMonthTallyAfter,
        "Current month tally should not change after re-tally with different time range");
    Assertions.assertEquals(
        lastMonthTallyBefore,
        lastMonthTallyAfter,
        "Last month tally should not change after re-tally with different time range");
  }

  @Test
  public void testInstanceReportPersistsWithDateTimeRangeVariations() throws Exception {
    OffsetDateTime now = OffsetDateTime.now();

    // Run initial tally with 312 hours (13 days) to cover both months
    helpers.syncTallyHourly(setup.orgId, now.minusHours(312), now, service);
    Thread.sleep(5000);

    // Get initial instances report for last month
    JsonNode lastMonthInstancesBefore =
        objectMapper.readTree(
            helpers
                .getInstancesReport(
                    setup.orgId,
                    TEST_PRODUCT_TAG,
                    setup.lastMonth.start(),
                    setup.lastMonth.end(),
                    service)
                .asString());

    // Run hourly tally again with 48 hours (2 days) - different time range
    helpers.syncTallyHourly(setup.orgId, now.minusHours(48), now, service);
    Thread.sleep(5000);

    // Get instances report after second tally
    JsonNode lastMonthInstancesAfter =
        objectMapper.readTree(
            helpers
                .getInstancesReport(
                    setup.orgId,
                    TEST_PRODUCT_TAG,
                    setup.lastMonth.start(),
                    setup.lastMonth.end(),
                    service)
                .asString());

    // Verify persistence - instances report should not change (excluding
    // last_applied_event_record_date)
    Assertions.assertEquals(
        removeLastAppliedEventDate(lastMonthInstancesBefore),
        removeLastAppliedEventDate(lastMonthInstancesAfter),
        "Last month instances report should not change after re-tally with different time range");
  }

  private TestSetup setupTest() throws Exception {
    String orgId = RandomUtils.generateRandom();
    OffsetDateTime now = OffsetDateTime.now();

    // Create org config to allow access to reporting endpoints
    helpers.createOptInConfig(orgId, service);
    

    // Calculate monthly ranges
    OffsetDateTime currentMonthStart =
        now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    OffsetDateTime lastHour = now.minusHours(1);
    OffsetDateTime currentMonthEnd =
        lastHour.truncatedTo(ChronoUnit.HOURS).plusHours(1).minusSeconds(1).minusNanos(1);

    // Last month range
    OffsetDateTime lastMonthStart = currentMonthStart.minusMonths(1);
    OffsetDateTime lastMonthEnd = currentMonthStart.minusSeconds(1).minusNanos(1);

    // Create events for current and last month
    createEvent(orgId, currentMonthStart.plusDays(1), 2.0f);
    createEvent(orgId, lastMonthStart.plusDays(1), 3.0f);
    Thread.sleep(2000);

    return new TestSetup(
        orgId,
        new TallyTestHelpers.MonthlyRange(currentMonthStart, currentMonthEnd),
        new TallyTestHelpers.MonthlyRange(lastMonthStart, lastMonthEnd));
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
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private JsonNode removeLastAppliedEventDate(JsonNode json) {
    if (json.has("data") && json.get("data").isArray() && json.get("data").size() > 0) {
      com.fasterxml.jackson.databind.node.ObjectNode firstItem =
          (com.fasterxml.jackson.databind.node.ObjectNode) json.get("data").get(0);
      firstItem.remove("last_applied_event_record_date");
    }
    return json;
  }

  private record TestSetup(
      String orgId,
      TallyTestHelpers.MonthlyRange currentMonth,
      TallyTestHelpers.MonthlyRange lastMonth) {}
}
