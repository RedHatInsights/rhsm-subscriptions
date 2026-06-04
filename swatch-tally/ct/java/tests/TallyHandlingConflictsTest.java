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
import static utils.TallyTestProducts.RHACM;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;
import static utils.TallyTestProducts.ROSA;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.TallyTestProducts;

public class TallyHandlingConflictsTest extends BaseTallyComponentTest {
  private TestSetup setup;

  @BeforeEach
  public void setUp() {
    super.setUp();
    setup = setupTest();
  }

  @Test
  @TestPlanName("tally-conflicts-TC001")
  public void testTallyCorrectlyHandlesPositiveMetricValueUpdates() {
    // Given: An initial event with a metric value and a subsequent event with a higher positive
    // value
    float initialValue = 10.0f;
    float updatedValue = 25.0f;
    String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, initialValue);
    service.performHourlyTallyForOrg(setup.orgId);
    double before =
        awaitHourlyTallySum(
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            initialValue);

    // When: An update event with same instanceId and timestamp hour but different positive value
    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, updatedValue);
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Tally should reflect the updated positive measurement
    double after =
        awaitHourlyTallySum(
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            updatedValue);
    assertEquals(
        updatedValue, after, 0.0001, "Updated tally should reflect the updated measurement");
  }

  @Test
  @TestPlanName("tally-conflicts-TC002")
  public void testTallyCorrectlyHandlesNegativeMetricValueNoUpdates() {
    // Given: An initial event with a positive metric value
    float initialValue = 10.0f;
    float updatedValue = -25.0f;
    String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, initialValue);
    service.performHourlyTallyForOrg(setup.orgId);
    double before =
        awaitHourlyTallySum(
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            initialValue);

    // When: An update event with same instanceId and timestamp hour but negative value
    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, updatedValue);
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Tally should not reflect the negative measurement
    double after =
        awaitHourlyTallySum(
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            before);
    assertEquals(
        before, after, 0.0001, "Tally should not reflect the updated negative measurement");
  }

  @Test
  @TestPlanName("tally-conflicts-TC003")
  public void testTallyMultipleProductsSameInstance() {
    // Given: One event per (product, metric) combination for the same instance
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      for (String metricId : product.metricIds()) {
        float value = (float) expectedMetricValue(metricId);
        createEventForProduct(setup.start, product, metricId, value);
      }
    }

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Each product should have correct instance count and tally totals per metric
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      awaitInstancesCount(
          product.productTag(), setup.start, setup.start.plusHours(1), setup.instanceId, 1);

      for (String metricId : product.metricIds()) {
        awaitHourlyTallySum(
            product.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            expectedMetricValue(metricId));
      }
    }
  }

  @Test
  @TestPlanName("tally-conflicts-TC004")
  public void testTallyMultipleProductsSameInstanceConflictingEvents() {
    // Given: Events for each product in the starting hour and conflicting events in the next hour
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      for (String metricId : product.metricIds()) {
        float value = (float) expectedMetricValue(metricId);
        createEventForProduct(setup.start, product, metricId, value);
      }
    }
    service.performHourlyTallyForOrg(setup.orgId);

    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      for (String metricId : product.metricIds()) {
        float value = (float) expectedMetricValue(metricId);
        createEventForProduct(setup.start.plusHours(1), product, metricId, value);
      }
    }

    // When: Performing hourly tally after conflicting events
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Each product should have one instance and doubled tally totals across both hours
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      awaitInstancesCount(
          product.productTag(), setup.start, setup.start.plusHours(2), setup.instanceId, 1);
      for (String metricId : product.metricIds()) {
        awaitHourlyTallySum(
            product.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(2),
            expectedMetricValue(metricId) * 2.0);
      }
    }
  }

  // --- Given helper methods ---

  private TestSetup setupTest() {
    service.createOptInConfig(orgId);

    // Use a fixed hour bucket so events collide (same instance_id + same hour).
    OffsetDateTime start =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);
    String instanceId = UUID.randomUUID().toString();

    return new TestSetup(orgId, start, instanceId);
  }

  private void createEventForProduct(
      OffsetDateTime timestamp, TallyTestProducts product, String metricId, float value) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            setup.orgId,
            setup.instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            metricId,
            value,
            product.productId(),
            product.productTag());

    event.setDisplayName(Optional.of(setup.instanceId));

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double expectedMetricValue(String metricId) {
    return "Instance-hours".equals(metricId) ? 1.0 : 40.0;
  }

  // --- Then helper methods ---

  private long getInstancesCountByDisplayName(
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String displayNameContains) {
    InstanceResponse response =
        service.getInstancesByProduct(setup.orgId, productTag, beginning, ending);
    if (response.getData() == null) {
      return 0;
    }

    return response.getData().stream()
        .map(InstanceData::getDisplayName)
        .filter(Objects::nonNull)
        .filter(d -> d.contains(displayNameContains))
        .count();
  }

  private void awaitInstancesCount(
      String productTag,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      String displayNameContains,
      int expectedCount) {
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(30))
            .withService(service)
            .timeoutMessage(
                "Timed out waiting for instances count %d (product=%s)", expectedCount, productTag);

    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(setup.orgId);
          assertEquals(
              expectedCount,
              getInstancesCountByDisplayName(productTag, beginning, ending, displayNameContains),
              "Instance count should match expected value");
        },
        settings);
  }

  private double getHourlyTallySum(
      String productTag, String metricId, OffsetDateTime beginning, OffsetDateTime ending) {
    Map<String, ?> queryParams =
        Map.of(
            "granularity", "Hourly",
            "beginning", beginning.toString(),
            "ending", ending.toString());

    TallyReportData resp =
        service.getTallyReportData(setup.orgId, productTag, metricId, queryParams);
    if (resp.getData() == null) {
      return 0.0;
    }

    return resp.getData().stream()
        .collect(Collectors.summarizingInt(TallyReportDataPoint::getValue))
        .getSum();
  }

  private double awaitHourlyTallySum(
      String productTag,
      String metricId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      double expected) {
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(30))
            .withService(service)
            .timeoutMessage(
                "Timed out waiting for hourly tally to reach expected value %.4f (product=%s metric=%s)",
                expected, productTag, metricId);

    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(setup.orgId);
          assertEquals(
              expected,
              getHourlyTallySum(productTag, metricId, beginning, ending),
              0.0001,
              "Hourly tally sum should match expected value");
        },
        settings);

    return getHourlyTallySum(productTag, metricId, beginning, ending);
  }

  private record TestSetup(String orgId, OffsetDateTime start, String instanceId) {}
}
