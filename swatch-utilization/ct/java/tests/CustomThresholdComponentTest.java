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

import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.openapi.model.OrgPreferencesRequest;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import domain.Severity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Component tests for custom usage threshold notification (TC001-TC005). */
public class CustomThresholdComponentTest extends BaseUtilizationComponentTest {

  private static final String CUSTOM_THRESHOLD_EVENT_TYPE = "exceeded-custom-utilization-threshold";
  private static final String DEFAULT_THRESHOLD_EVENT_TYPE = "exceeded-utilization-threshold";

  private static final int CUSTOM_THRESHOLD = 80;
  private static final double FULL_CAPACITY = 100.0;
  private static final double USAGE_ABOVE_CUSTOM_THRESHOLD = 85.0;
  private static final double USAGE_AT_CUSTOM_THRESHOLD = 80.0;
  private static final double USAGE_BELOW_CUSTOM_THRESHOLD = 70.0;
  private static final double USAGE_OVER_CAPACITY = 120.0;

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
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_ABOVE_CUSTOM_THRESHOLD, FULL_CAPACITY, false);

    whenUtilizationEventIsReceived();

    var notification =
        thenThresholdNotificationShouldBeSent(
            CUSTOM_THRESHOLD_EVENT_TYPE,
            Severity.MODERATE,
            utilizationSummary.getProductId(),
            metricId,
            USAGE_ABOVE_CUSTOM_THRESHOLD,
            FULL_CAPACITY,
            DIMENSION_ANY,
            DIMENSION_ANY);
    thenLastUpdatedHashShouldBePresent(notification);
  }

  @TestPlanName("custom-threshold-TC002")
  @Test
  void shouldSendNotification_whenUsageExactlyAtOrgCustomThreshold() {
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(utilizationSummary, metricId, USAGE_AT_CUSTOM_THRESHOLD, FULL_CAPACITY, false);

    whenUtilizationEventIsReceived();

    var notification =
        thenThresholdNotificationShouldBeSent(
            CUSTOM_THRESHOLD_EVENT_TYPE,
            Severity.MODERATE,
            utilizationSummary.getProductId(),
            metricId,
            USAGE_AT_CUSTOM_THRESHOLD,
            FULL_CAPACITY,
            DIMENSION_ANY,
            DIMENSION_ANY);
    thenLastUpdatedHashShouldBePresent(notification);
  }

  @TestPlanName("custom-threshold-TC003")
  @Test
  void shouldNotSendNotification_whenNoOrgPreferenceExists() {
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_ABOVE_CUSTOM_THRESHOLD, FULL_CAPACITY, false);

    whenUtilizationEventIsReceived();

    thenNoThresholdNotificationShouldBeSent(CUSTOM_THRESHOLD_EVENT_TYPE);
  }

  @TestPlanName("custom-threshold-TC004")
  @Test
  void shouldNotSendNotification_whenUsageBelowCustomThreshold() {
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(
        utilizationSummary, metricId, USAGE_BELOW_CUSTOM_THRESHOLD, FULL_CAPACITY, false);

    whenUtilizationEventIsReceived();

    thenNoThresholdNotificationShouldBeSent(CUSTOM_THRESHOLD_EVENT_TYPE);
  }

  @TestPlanName("custom-threshold-TC005")
  @Test
  void shouldSendBothNotifications_whenUsageExceedsBothThresholds() {
    givenOrgCustomThreshold(CUSTOM_THRESHOLD);
    MetricId metricId = MetricIdUtils.getCores();
    givenMeasurement(utilizationSummary, metricId, USAGE_OVER_CAPACITY, FULL_CAPACITY, false);

    whenUtilizationEventIsReceived();

    thenThresholdNotificationShouldBeSent(
        CUSTOM_THRESHOLD_EVENT_TYPE,
        Severity.MODERATE,
        utilizationSummary.getProductId(),
        metricId,
        USAGE_OVER_CAPACITY,
        FULL_CAPACITY,
        DIMENSION_ANY,
        DIMENSION_ANY);
    thenThresholdNotificationShouldBeSent(
        DEFAULT_THRESHOLD_EVENT_TYPE,
        Severity.IMPORTANT,
        utilizationSummary.getProductId(),
        metricId,
        USAGE_OVER_CAPACITY,
        FULL_CAPACITY,
        DIMENSION_ANY,
        DIMENSION_ANY);
  }

  private void givenOrgCustomThreshold(int threshold) {
    OrgPreferencesRequest request = new OrgPreferencesRequest();
    request.setCustomThreshold(threshold);
    service.updateOrgPreferencesExpectSuccess(orgId, request);
  }

  private void whenUtilizationEventIsReceived() {
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);
  }

  private void thenLastUpdatedHashShouldBePresent(Action notification) {
    assertThat(
        notification
            .getEvents()
            .get(0)
            .getPayload()
            .getAdditionalProperties()
            .get("last_updated_hash"),
        notNullValue());
  }
}
