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
import static com.redhat.swatch.component.tests.utils.AwaitilityUtils.untilAsserted;
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import com.redhat.swatch.utilization.test.model.UtilizationSummary.Granularity;
import domain.Product;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class CustomerOverUsageComponentTest extends BaseUtilizationComponentTest {

  // Test data constants - aligned with CUSTOMER_OVER_USAGE_DEFAULT_THRESHOLD_PERCENT=5.0
  private static final double BASELINE_CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0; // 10% over capacity
  private static final double USAGE_BELOW_THRESHOLD = 103.0; // 3% over capacity
  private static final double ARBITRARY_USAGE = 200.0;

  // Test timing and assertion constants
  private static final Duration MESSAGE_PROCESSING_DELAY = Duration.ofSeconds(2);
  private static final double EXPECTED_SINGLE_INCREMENT = 1.0;

  private final Map<String, Double> initialCounters = new HashMap<>();
  private UtilizationSummary utilizationSummary;

  @BeforeAll
  static void enableSendNotificationsFeatureFlag() {
    unleash.enableSendNotificationsFlag();
  }

  @AfterAll
  static void disableSendNotificationsFeatureFlag() {
    unleash.disableSendNotificationsFlag();
  }

  /**
   * Verify over-usage counter is incremented when usage exceeds capacity by more than threshold.
   */
  @Test
  void shouldIncrementOverUsageCounter_whenUsageExceedsThreshold() {
    // 10% over capacity exceeds the 5% threshold
    givenUtilizationSummaryForPaygProduct(Granularity.HOURLY);
    givenMetricUsageExceedsThreshold(MetricIdUtils.getCores());

    whenUtilizationEventIsReceived();

    thenOverUsageCounterShouldBeIncremented();
    thenNotificationShouldBeSent();
  }

  /** Verify over-usage counter is not incremented when usage is below threshold. */
  @Test
  void shouldNotIncrementOverUsageCounter_whenUsageBelowThreshold() {
    // 3% over capacity is below the 5% threshold
    givenUtilizationSummaryForPaygProduct(Granularity.HOURLY);
    givenMetricUsageDoesNotExceedThreshold(MetricIdUtils.getCores());

    whenUtilizationEventIsReceived();

    thenOverUsageCounterShouldNotChange();
  }

  /** Verify over-usage counter is not incremented for unlimited capacity subscriptions. */
  @Test
  void shouldNotIncrementOverUsageCounter_whenCapacityIsUnlimited() {
    givenUtilizationSummaryForPaygProduct(Granularity.HOURLY);
    givenMetricIsUnlimited(MetricIdUtils.getCores());

    whenUtilizationEventIsReceived();

    thenOverUsageCounterShouldNotChange();
  }

  @Test
  void shouldIncrementOverUsageCounter_whenGranularityIsDaily() {
    givenUtilizationSummaryForNonPaygProduct(Granularity.DAILY);
    givenMetricUsageExceedsThreshold(MetricIdUtils.getSockets());

    whenUtilizationEventIsReceived();

    thenOverUsageCounterShouldBeIncremented();
    thenNotificationShouldBeSent();
  }

  /** Verify no exceptions thrown when measurements list is null. */
  @Test
  void shouldHandleNullMeasurementsList_withoutErrors() {
    givenUtilizationSummaryForPaygProduct(Granularity.HOURLY);

    whenUtilizationEventIsReceived();

    // Verify counter doesn't change when measurements are null
    thenOverUsageCounterShouldNotChange();
  }

  /** Verify only measurements exceeding threshold increment counter. */
  @Test
  void shouldIncrementCounterOnlyForMeasurementsExceedingThreshold() {
    double initialInstanceHoursCount =
        service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(MetricIdUtils.getInstanceHours()));

    givenUtilizationSummaryForPaygProduct(Granularity.HOURLY);
    givenMetricUsageDoesNotExceedThreshold(MetricIdUtils.getCores());
    givenMetricUsageExceedsThreshold(MetricIdUtils.getInstanceHours());

    whenUtilizationEventIsReceived();

    // Counter should increment by exactly 1 (only the second measurement)
    untilAsserted(
        () -> {
          double currentCount =
              service.getMetricByTags(
                  OVER_USAGE_METRIC, metricIdTag(MetricIdUtils.getInstanceHours()));
          assertThat(
              "Counter should increment by exactly 1 for the one measurement exceeding threshold",
              currentCount - initialInstanceHoursCount,
              equalTo(EXPECTED_SINGLE_INCREMENT));
        });
  }

  // Given helpers
  private void givenMetricUsageExceedsThreshold(MetricId metric) {
    givenMetricInUtilizationSummary(metric, USAGE_EXCEEDING_THRESHOLD, false);
  }

  private void givenMetricUsageDoesNotExceedThreshold(MetricId metric) {
    givenMetricInUtilizationSummary(metric, USAGE_BELOW_THRESHOLD, false);
  }

  private void givenMetricIsUnlimited(MetricId metric) {
    givenMetricInUtilizationSummary(metric, ARBITRARY_USAGE, true);
  }

  private void givenMetricInUtilizationSummary(
      MetricId metric, double currentTotal, boolean unlimited) {
    utilizationSummary
        .getMeasurements()
        .add(
            new Measurement()
                .withMetricId(metric.getValue())
                .withCurrentTotal(currentTotal)
                .withCapacity(BASELINE_CAPACITY)
                .withUnlimited(unlimited));
  }

  void givenUtilizationSummaryForPaygProduct(Granularity granularity) {
    givenUtilizationSummary(Product.ROSA, granularity);
  }

  void givenUtilizationSummaryForNonPaygProduct(Granularity granularity) {
    givenUtilizationSummary(Product.RHEL, granularity);
  }

  void givenUtilizationSummary(Product product, Granularity granularity) {
    utilizationSummary =
        new UtilizationSummary()
            .withOrgId(orgId)
            .withProductId(product.getName())
            .withGranularity(granularity)
            .withBillingAccountId(RandomUtils.generateRandom())
            .withMeasurements(new ArrayList<>());

    for (String metric : List.of(OVER_USAGE_METRIC)) {
      double initialCount =
          service.getMetricByTags(metric, metricIdTag(product.getFirstMetricId()));
      initialCounters.put(metric, initialCount);
    }
  }

  // When helpers
  void whenUtilizationEventIsReceived() {
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);
  }

  // Then helpers
  void thenOverUsageCounterShouldBeIncremented() {
    thenCounterShouldBeIncremented(OVER_USAGE_METRIC);
  }

  void thenCounterShouldBeIncremented(String metric) {
    Double initialCount = initialCounters.getOrDefault(metric, 0.0);
    MetricId metricId = Product.fromString(utilizationSummary.getProductId()).getFirstMetricId();
    untilAsserted(
        () -> {
          double currentCount = service.getMetricByTags(metric, metricIdTag(metricId));
          assertThat(
              metric + " counter should be incremented", currentCount, greaterThan(initialCount));
        });
  }

  void thenOverUsageCounterShouldNotChange() {
    Double initialCount = initialCounters.getOrDefault(OVER_USAGE_METRIC, 0.0);
    MetricId metricId = Product.fromString(utilizationSummary.getProductId()).getFirstMetricId();
    await()
        .pollDelay(MESSAGE_PROCESSING_DELAY)
        .untilAsserted(
            () -> {
              double currentCount =
                  service.getMetricByTags(OVER_USAGE_METRIC, metricIdTag(metricId));
              assertThat(
                  "Over-usage counter should not change", currentCount, equalTo(initialCount));
            });
  }

  void thenNotificationShouldBeSent() {
    Action notification = kafkaBridge.waitForKafkaMessage(NOTIFICATIONS, matchesOrgId(orgId));
    assertThat("Notification should be sent", notification, notNullValue());

    // Verify context contains expected product_id and metric_id
    var context = notification.getContext();
    assertThat("Context should not be null", context, notNullValue());
    assertThat(
        "Context should contain correct product_id",
        context.getAdditionalProperties().get("product_id"),
        equalTo(utilizationSummary.getProductId()));

    MetricId expectedMetricId =
        Product.fromString(utilizationSummary.getProductId()).getFirstMetricId();
    assertThat(
        "Context should contain correct metric_id",
        context.getAdditionalProperties().get("metric_id"),
        equalTo(expectedMetricId.getValue()));

    // Verify event payload contains expected utilization_percentage
    var events = notification.getEvents();
    assertThat("Events should not be null or empty", events, notNullValue());
    assertThat("Events should contain at least one event", events.size(), greaterThan(0));

    var event = events.get(0);
    var payload = event.getPayload();
    assertThat("Event payload should not be null", payload, notNullValue());

    // Calculate expected utilization percentage
    Measurement measurement = utilizationSummary.getMeasurements().get(0);
    double expectedUtilizationPercent =
        (measurement.getCurrentTotal() / measurement.getCapacity()) * 100.0;
    String expectedUtilizationStr = String.format("%.2f", expectedUtilizationPercent);

    assertThat(
        "Payload should contain correct utilization_percentage",
        payload.getAdditionalProperties().get("utilization_percentage"),
        equalTo(expectedUtilizationStr));
  }
}
