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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static utils.TallyTestProducts.OPENSHIFT_DEDICATED;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.TallyReportData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify that Tally Report and Instance report endpoints are not changing persists
 * correctly when called with different time ranges. This test verifies that re-running tally
 * doesn't change the already calculated tally data for previous periods.
 *
 * <p><a href="https://issues.redhat.com/browse/ENT-3713">...</a>
 */
@Disabled(value = "Tests disabled due to timing flakiness to be fixed in SWATCH-4567.")
public class TallyPersistenceTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = OPENSHIFT_DEDICATED.productTag();
  private static final String TEST_METRIC_ID = OPENSHIFT_DEDICATED.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = OPENSHIFT_DEDICATED.productId();

  private TestSetup setup;

  @BeforeEach
  public void setUp() {
    super.setUp();
    setup = setupTest();
  }

  @Test
  @TestPlanName("tally-persistence-TC001")
  public void testTallyReportPersistsWithDateTimeRangeVariations() {
    // Given: Initial tally reports for today and yesterday
    service.performHourlyTallyForOrg(setup.orgId);
    TallyReportData todayTallyBefore = getTallyReport(setup.today);
    TallyReportData yesterdayTallyBefore = getTallyReport(setup.yesterday);

    // When: Running hourly tally again
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Tally reports should not change after re-tally
    TallyReportData todayTallyAfter = getTallyReport(setup.today);
    TallyReportData yesterdayTallyAfter = getTallyReport(setup.yesterday);

    assertEquals(
        todayTallyBefore, todayTallyAfter, "Today's tally should not change after re-tally");
    assertEquals(
        yesterdayTallyBefore,
        yesterdayTallyAfter,
        "Yesterday's tally should not change after re-tally");
  }

  @Test
  @TestPlanName("tally-persistence-TC002")
  public void testInstanceReportPersistsWithDateTimeRangeVariations() {
    // Given: Initial instances report for yesterday
    service.performHourlyTallyForOrg(setup.orgId);
    OffsetDateTime yesterdayInstancesBefore =
        AwaitilityUtils.untilIsNotNull(
            () -> getLastAppliedEventDate(getInstancesReport(setup.yesterday)));

    // When: Running hourly tally again
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Instances report should not change after re-tally
    OffsetDateTime yesterdayInstancesAfter =
        AwaitilityUtils.untilIsNotNull(
            () -> getLastAppliedEventDate(getInstancesReport(setup.yesterday)));

    assertEquals(
        yesterdayInstancesBefore,
        yesterdayInstancesAfter,
        "Yesterday's instances report should not change after re-tally");
  }

  // --- Given helper methods ---

  private TestSetup setupTest() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    service.createOptInConfig(orgId);

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

  // --- Then helper methods ---

  private TallyReportData getTallyReport(DateRange range) {
    return service.getTallyReportData(
        setup.orgId,
        TEST_PRODUCT_TAG,
        TEST_METRIC_ID,
        Map.of(
            "granularity",
            "Hourly",
            "beginning",
            range.start().toString(),
            "ending",
            range.end().toString()));
  }

  private InstanceResponse getInstancesReport(DateRange range) {
    return service.getInstancesByProduct(setup.orgId, TEST_PRODUCT_TAG, range.start(), range.end());
  }

  private OffsetDateTime getLastAppliedEventDate(InstanceResponse response) {
    if (response.getData() == null || response.getData().isEmpty()) {
      return null;
    }

    return response.getData().get(0).getLastAppliedEventRecordDate();
  }

  private record DateRange(OffsetDateTime start, OffsetDateTime end) {}

  private record TestSetup(String orgId, DateRange today, DateRange yesterday) {}
}
