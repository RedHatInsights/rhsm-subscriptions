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

import static api.MessageValidators.matchesNotificationByEventType;
import static api.MessageValidators.matchesOrgId;
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import domain.Severity;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Component tests for custom usage threshold notification (TC001-TC006). */
public class CustomThresholdComponentTest extends BaseUtilizationComponentTest {

  private static final String CUSTOM_THRESHOLD_EVENT_TYPE = "exceeded-custom-utilization-threshold";
  private static final String OVER_USAGE_EVENT_TYPE = "exceeded-utilization-threshold";

  private static final int CUSTOM_THRESHOLD = 80;
  private static final double STANDARD_CAPACITY = 100.0;
  private static final double USAGE_ABOVE_CUSTOM_THRESHOLD = 85.0;
  private static final double USAGE_AT_CUSTOM_THRESHOLD = 80.0;
  private static final double USAGE_BELOW_CUSTOM_THRESHOLD = 70.0;
  private static final double USAGE_TRIGGERING_OVER_USAGE_ALERT = 110.0;
  private static final double USAGE_AT_OVER_USAGE_GUARD_BOUNDARY = 105.0;

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

  @TestPlanName("custom-threshold-TC001")
  @Test
  void shouldSendNotification_whenUsageExceedsOrgCustomThreshold() {
    // Given: Org has custom threshold at 80%, usage is 85% (above custom, below over-usage)
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_ABOVE_CUSTOM_THRESHOLD, STANDARD_CAPACITY, false);

    // When: Send utilization summary to the utilization topic
    whenUtilizationEventIsReceived();

    // Then: Custom threshold notification with MODERATE severity and correct payload
    thenCustomThresholdNotificationShouldBeSent(
        metricId, USAGE_ABOVE_CUSTOM_THRESHOLD, STANDARD_CAPACITY);
  }

  @TestPlanName("custom-threshold-TC002")
  @Test
  void shouldSendNotification_whenUsageExactlyAtOrgCustomThreshold() {
    // Given: Org has custom threshold at 80%, usage is exactly 80%
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_AT_CUSTOM_THRESHOLD, STANDARD_CAPACITY, false);

    // When: Send utilization summary to the utilization topic
    whenUtilizationEventIsReceived();

    // Then: Custom threshold notification sent (threshold comparison uses >=)
    thenCustomThresholdNotificationShouldBeSent(
        metricId, USAGE_AT_CUSTOM_THRESHOLD, STANDARD_CAPACITY);
  }

  @TestPlanName("custom-threshold-TC003")
  @Test
  void shouldNotSendNotification_whenNoOrgPreferenceExists() {
    // Given: No custom threshold configured, usage below over-usage threshold
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_ABOVE_CUSTOM_THRESHOLD, STANDARD_CAPACITY, false);

    // When: Send utilization summary to the utilization topic
    whenUtilizationEventIsReceived();

    // Then: No notification of any type
    thenNoNotificationShouldBeSent();
  }

  @TestPlanName("custom-threshold-TC004")
  @Test
  void shouldNotSendNotification_whenUsageBelowCustomThreshold() {
    // Given: Org has custom threshold at 80%, usage is 70%
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_BELOW_CUSTOM_THRESHOLD, STANDARD_CAPACITY, false);

    // When: Send utilization summary to the utilization topic
    whenUtilizationEventIsReceived();

    // Then: No notification of any type
    thenNoNotificationShouldBeSent();
  }

  @TestPlanName("custom-threshold-TC005")
  @Test
  void shouldSendOverUsageNotification_whenBothThresholdsExceeded() {
    // Given: Org has custom threshold at 80%, usage is 110% (exceeds both thresholds)
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_TRIGGERING_OVER_USAGE_ALERT, STANDARD_CAPACITY, false);

    // When: Send utilization summary to the utilization topic
    whenUtilizationEventIsReceived();

    // Then: Over-usage notification takes precedence, custom threshold is suppressed
    thenOverUsageNotificationShouldBeSent();
    thenNoCustomThresholdNotificationShouldBeSent();
  }

  @TestPlanName("custom-threshold-TC006")
  @Test
  void shouldSendCustomThresholdNotification_whenUsageIsExactlyAtOverUsageGuardBoundary() {
    // Given: Org has custom threshold at 80%, usage is exactly 105% (= 100% + 5% over-usage
    // threshold)
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_AT_OVER_USAGE_GUARD_BOUNDARY, STANDARD_CAPACITY, false);

    // When: Send utilization summary to the utilization topic
    whenUtilizationEventIsReceived();

    // Then: Custom threshold fires (guard is exclusive >), over-usage does not (also exclusive >)
    thenCustomThresholdNotificationShouldBeSent(
        metricId, USAGE_AT_OVER_USAGE_GUARD_BOUNDARY, STANDARD_CAPACITY);
    thenNoOverUsageNotificationShouldBeSent();
  }

  // Given helpers

  private void givenOrgCustomThreshold(int threshold) {
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(threshold);
    service.updateOrgPreferencesExpectSuccess(orgId, request);
  }

  // When helpers

  private void whenUtilizationEventIsReceived() {
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);
  }

  // Then helpers

  private void thenCustomThresholdNotificationShouldBeSent(
      MetricId metricId, double usageValue, double capacity) {
    Action notification =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS, matchesNotificationByEventType(orgId, CUSTOM_THRESHOLD_EVENT_TYPE));

    assertThat("Notification should be sent", notification, notNullValue());
    assertEquals(
        Severity.MODERATE.name(),
        notification.getSeverity(),
        "Custom threshold notification should declare MODERATE severity");
    assertEquals(
        CUSTOM_THRESHOLD_EVENT_TYPE,
        notification.getEventType(),
        "Event type should be exceeded-custom-utilization-threshold");

    var context = notification.getContext();
    assertThat("Context should not be null", context, notNullValue());
    assertThat(
        "Context should contain correct product_id",
        context.getAdditionalProperties().get("product_id"),
        equalTo(utilizationSummary.getProductId()));
    assertThat(
        "Context should contain correct metric_id",
        context.getAdditionalProperties().get("metric_id"),
        equalTo(metricId.getValue()));

    var events = notification.getEvents();
    assertThat("Events should not be null", events, notNullValue());
    assertThat("Events should have at least one event", events.size(), greaterThan(0));

    var payload = events.get(0).getPayload();
    assertThat("Payload should not be null", payload, notNullValue());

    double expectedUtilizationPercent = (usageValue / capacity) * 100.0;
    String expectedUtilizationStr = String.format("%.2f", expectedUtilizationPercent);
    assertThat(
        "Payload should contain correct utilization_percentage",
        payload.getAdditionalProperties().get("utilization_percentage"),
        equalTo(expectedUtilizationStr));
    assertThat(
        "Payload should contain last_updated_hash",
        payload.getAdditionalProperties().get("last_updated_hash"),
        notNullValue());
  }

  private void thenNoNotificationShouldBeSent() {
    var notifications =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesOrgId(orgId),
            0,
            AwaitilitySettings.usingTimeout(Duration.ofSeconds(5)));
    assertThat("No notification should be sent", notifications, empty());
  }

  private void thenOverUsageNotificationShouldBeSent() {
    Action notification =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS, matchesNotificationByEventType(orgId, OVER_USAGE_EVENT_TYPE));

    assertThat("Over-usage notification should be sent", notification, notNullValue());
    assertEquals(
        Severity.IMPORTANT.name(),
        notification.getSeverity(),
        "Over-usage notification should declare IMPORTANT severity");
  }

  private void thenNoOverUsageNotificationShouldBeSent() {
    var notifications =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesNotificationByEventType(orgId, OVER_USAGE_EVENT_TYPE),
            0,
            AwaitilitySettings.usingTimeout(Duration.ofSeconds(5)));
    assertThat("No over-usage notification should be sent", notifications, empty());
  }

  private void thenNoCustomThresholdNotificationShouldBeSent() {
    var notifications =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesNotificationByEventType(orgId, CUSTOM_THRESHOLD_EVENT_TYPE),
            0,
            AwaitilitySettings.usingTimeout(Duration.ofSeconds(5)));
    assertThat("No custom threshold notification should be sent", notifications, empty());
  }
}
