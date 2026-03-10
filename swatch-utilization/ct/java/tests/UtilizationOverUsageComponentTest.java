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

import static api.MessageValidators.matchesOrgId;
import static api.MessageValidators.matchesOverageNotification;
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Component tests for overusage detection with 5% threshold. */
public class UtilizationOverUsageComponentTest extends BaseUtilizationComponentTest {

  // Baseline test values
  protected static final double STANDARD_CAPACITY = 100.0;
  protected static final double USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD = 120.0;

  private UtilizationSummary utilizationSummary;

  @BeforeAll
  static void enableSendNotificationsFeatureFlag() {
    unleash.enableSendNotificationsFlag();
  }

  @AfterAll
  static void disableSendNotificationsFeatureFlag() {
    unleash.disableSendNotificationsFlag();
  }

  @BeforeEach
  void setUpUtilizationSummary() {
    utilizationSummary = givenUtilizationSummaryWithDefaults();
  }

  /** Verify overusage detection triggers notification when usage exceeds threshold. */
  @TestPlanName("utilization-overusage-TC001")
  @Test
  void shouldDetectOverageAndSendNotification_whenUsageExceedsThreshold() {
    // Given: An organization has capacity for metric A of product B
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, 110.0, STANDARD_CAPACITY, false); // 10% over capacity

    // When: Trigger utilization calculation process
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: Notification event contains correct information
    thenNotificationShouldBeSent(metricId, 110.0, STANDARD_CAPACITY);
  }

  /** Verify no notification is sent when usage is below capacity. */
  @TestPlanName("utilization-overusage-TC002")
  @Test
  void shouldNotSendNotification_whenUsageIsBelowCapacity() {
    // Given: An organization has capacity for metric A of product B
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, 80.0, STANDARD_CAPACITY, false); // 80% of capacity

    // When: Trigger utilization calculation process
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: No notification event created
    thenNoNotificationShouldBeSent();
  }

  /** Verify no notification when usage exceeds capacity but stays within threshold. */
  @TestPlanName("utilization-overusage-TC003")
  @Test
  void shouldNotSendNotification_whenUsageAboveCapacityButBelowThreshold() {
    // Given: An organization has capacity for metric A of product B
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, 103.0, STANDARD_CAPACITY, false); // 3% over capacity

    // When: Trigger utilization calculation process
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: No notification event created
    thenNoNotificationShouldBeSent();
  }

  /** Verify overusage persists when capacity increase is insufficient. */
  @TestPlanName("utilization-overusage-TC004")
  @Test
  void shouldContinueDetectingOverage_afterInsufficientCapacityIncrease() {
    // Given: Current usage exceeds threshold
    MetricId metricId = MetricIdUtils.getCores();

    // When: Increase capacity by insufficient amount (still ~109% utilization)
    givenMeasurement(
        utilizationSummary, metricId, USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD, 110.0, false);
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: Notification event still created (overage persists)
    thenNotificationShouldBeSent(metricId, USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD, 110.0);
  }

  /** Verify overusage resolves when capacity increase brings overage below threshold. */
  @TestPlanName("utilization-overusage-TC005")
  @Test
  void shouldResolveOverage_whenCapacityIncreaseBringsUsageBelowThreshold() {
    // Given: Current usage is above threshold
    MetricId metricId = MetricIdUtils.getCores();

    // When: Increase capacity enough to bring overage below threshold (102.5% utilization, but
    // usage still above 100% capacity)
    givenMeasurement(
        utilizationSummary, metricId, USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD, 117.0, false);
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: No notification event created (overage below threshold despite usage > 100% capacity)
    thenNoNotificationShouldBeSent();
  }

  /** Verify overusage resolves when capacity increase brings usage below 100%. */
  @TestPlanName("utilization-overusage-TC006")
  @Test
  void shouldResolveOverage_afterSufficientCapacityIncrease() {
    // Given: Current usage is above threshold
    MetricId metricId = MetricIdUtils.getCores();

    // When: Increase capacity enough to bring overage below 100% capacity (92% utilization)
    givenMeasurement(
        utilizationSummary, metricId, USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD, 130.0, false);
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: No notification event created
    thenNoNotificationShouldBeSent();
  }

  /** Verify no notification when usage is exactly at threshold boundary. */
  @TestPlanName("utilization-overusage-TC007")
  @Test
  void shouldNotSendNotification_whenUsageExactlyAtThresholdBoundary() {
    // Given: An organization has capacity for metric A of product B
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, 105.0, STANDARD_CAPACITY, false); // Exactly 5% over capacity

    // When: Trigger utilization calculation process
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: No notification event created (threshold logic uses strict greater-than,
    // so exactly 5% over capacity does not trigger notification)
    thenNoNotificationShouldBeSent();
  }

  /** Verify overusage detection triggers after capacity reduction below usage. */
  @TestPlanName("utilization-overusage-TC008")
  @Test
  void shouldDetectOverage_afterCapacityReductionBelowCurrentUsage() {
    // Given: Current usage is within capacity limits
    MetricId metricId = MetricIdUtils.getCores();

    // When: Reduce capacity to below current usage level (120% utilization)
    givenMeasurement(
        utilizationSummary, metricId, USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD, 100.0, false);
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // Then: Notification event created (usage now exceeds reduced capacity + threshold)
    thenNotificationShouldBeSent(metricId, USAGE_SIGNIFICANTLY_ABOVE_THRESHOLD, 100.0);
  }

  // Helper methods - Then

  private void thenNotificationShouldBeSent(
      MetricId metricId, double currentTotal, double capacity) {
    // Use the validator that checks org_id, product_id, and metric_id
    Action notification =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesOverageNotification(
                orgId, utilizationSummary.getProductId(), metricId.getValue()));

    assertThat("Notification should be sent", notification, notNullValue());

    // Verify event payload contains expected utilization_percentage
    var events = notification.getEvents();
    assertThat("Notification should contain events list", events, notNullValue());
    assertThat("Notification should have at least one event", events.size(), greaterThan(0));

    var event = events.get(0);
    var payload = event.getPayload();
    assertThat("Notification event should contain payload", payload, notNullValue());

    // Calculate expected utilization percentage
    double expectedUtilizationPercent = (currentTotal / capacity) * 100.0;
    String expectedUtilizationStr = String.format("%.2f", expectedUtilizationPercent);

    assertThat(
        "Payload should contain correct utilization_percentage",
        payload.getAdditionalProperties().get("utilization_percentage"),
        equalTo(expectedUtilizationStr));

    // Verify timestamp reflects current calculation time
    assertThat(
        "Notification timestamp should not be null", notification.getTimestamp(), notNullValue());
  }

  private void thenNoNotificationShouldBeSent() {
    // Use a short timeout to verify no notification messages arrive
    var notifications =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesOrgId(orgId),
            0, // Expected count is 0
            AwaitilitySettings.usingTimeout(Duration.ofSeconds(5)));

    assertThat("No notification should be sent to Kafka topic", notifications, empty());
  }
}
