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
  public void testTallyCorrectlyHandlesPositiveMetricValueUpdates() {
    float initialValue = 10.0f;
    float updatedValue = 25.0f;
    String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

    // Initial event
    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, initialValue);
    service.performHourlyTallyForOrg(setup.orgId);
    double before =
        awaitHourlyTallySum(
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            initialValue);

    // Update event: same instanceId + same timestamp hour, different positive value
    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, updatedValue);
    service.performHourlyTallyForOrg(setup.orgId);
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
  public void testTallyCorrectlyHandlesNegativeMetricValueNoUpdates() {
    float initialValue = 10.0f;
    float updatedValue = -25.0f;
    String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

    // Initial event
    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, initialValue);
    service.performHourlyTallyForOrg(setup.orgId);
    double before =
        awaitHourlyTallySum(
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            metricId,
            setup.start,
            setup.start.plusHours(1),
            initialValue);

    // Update event: same instanceId + same timestamp hour, different negative value
    createEventForProduct(setup.start, RHEL_FOR_X86_ELS_PAYG, metricId, updatedValue);
    service.performHourlyTallyForOrg(setup.orgId);
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
  public void testTallyMultipleProductsSameInstance() {
    // Produce one event per (product, metric) combination.
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      for (String metricId : product.metricIds()) {
        float value = (float) expectedMetricValue(metricId);
        createEventForProduct(setup.start, product, metricId, value);
      }
    }
    service.performHourlyTallyForOrg(setup.orgId);

    // Verify instances + tally totals by product/metric.
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
  public void testTallyMultipleProductsSameInstanceConflictingEvents() {
    // Produce an event for each product in the starting hour
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      for (String metricId : product.metricIds()) {
        float value = (float) expectedMetricValue(metricId);
        createEventForProduct(setup.start, product, metricId, value);
      }
    }
    service.performHourlyTallyForOrg(setup.orgId);

    // Produce another conflicting event for each product in the next hour
    for (TallyTestProducts product : List.of(RHACM, ROSA)) {
      for (String metricId : product.metricIds()) {
        float value = (float) expectedMetricValue(metricId);
        createEventForProduct(setup.start.plusHours(1), product, metricId, value);
      }
    }
    service.performHourlyTallyForOrg(setup.orgId);

    // Verify instances + tally totals by product/metric.
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

  // --- helpers ---

  /*
   * Sets up variables used in each test.
   */
  private TestSetup setupTest() {
    service.createOptInConfig(orgId);

    // Use a fixed hour bucket so events collide (same instance_id + same hour).
    OffsetDateTime start =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);
    String instanceId = UUID.randomUUID().toString();

    return new TestSetup(orgId, start, instanceId);
  }

  /*
   * Creates an event for a given product and metric.
   */
  private void createEventForProduct(
      OffsetDateTime timestamp, TallyTestProducts product, String metricId, float value) {
    Event event =
        helpers.createEventWithTimestamp(
            setup.orgId,
            setup.instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            metricId,
            value,
            Event.Sla.PREMIUM,
            Event.HardwareType.CLOUD,
            product.productId(),
            product.productTag());

    event.setDisplayName(Optional.of(setup.instanceId));

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  /*
   * Returns the expected metric value for a given metric ID.
   * If the metric ID is "Instance-hours", returns 1.0.
   * Otherwise, returns 40.0.
   */
  private double expectedMetricValue(String metricId) {
    return "Instance-hours".equals(metricId) ? 1.0 : 40.0;
  }

  /*
   * Gets the number of instances by display name.
   */
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

  /*
   * Awaits the number of instances to reach the expected count.
   */
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
              getInstancesCountByDisplayName(productTag, beginning, ending, displayNameContains));
        },
        settings);
  }

  /*
   * Gets the hourly tally sum for a given product, metric, and time range.
   */
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

  /*
   * Awaits the hourly tally sum to reach the expected value.
   */
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
              expected, getHourlyTallySum(productTag, metricId, beginning, ending), 0.0001);
        },
        settings);

    return getHourlyTallySum(productTag, metricId, beginning, ending);
  }

  private record TestSetup(String orgId, OffsetDateTime start, String instanceId) {}
}
