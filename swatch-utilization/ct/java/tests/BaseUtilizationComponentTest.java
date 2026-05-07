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

import static api.MessageValidators.matchesOverageNotification;
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import api.UtilizationSwatchService;
import api.UtilizationUnleashService;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import domain.Product;
import domain.Severity;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-utilization")
public class BaseUtilizationComponentTest {
  protected static final String OVER_USAGE_METRIC = "swatch_utilization_over_usage_total";

  /**
   * Default value for unspecified SLA/usage on threshold metrics; must match {@code
   * BaseThresholdService.DIMENSION_ANY} in swatch-utilization.
   */
  protected static final String DIMENSION_ANY = "_ANY";

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService().subscribeToTopic(NOTIFICATIONS);

  @Unleash static UtilizationUnleashService unleash = new UtilizationUnleashService();

  @Quarkus(service = "swatch-utilization")
  static UtilizationSwatchService service = new UtilizationSwatchService();

  protected String orgId;

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }

  /** Creates a UtilizationSummary with ROSA product, HOURLY granularity, and empty measurements. */
  protected UtilizationSummary givenUtilizationSummaryWithDefaults() {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId(Product.ROSA.getName())
        .withGranularity(UtilizationSummary.Granularity.HOURLY)
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(new ArrayList<>());
  }

  /** Adds a measurement to the UtilizationSummary. */
  protected void givenMeasurement(
      UtilizationSummary summary,
      MetricId metricId,
      double currentTotal,
      double capacity,
      boolean unlimited) {
    summary
        .getMeasurements()
        .add(
            new Measurement()
                .withMetricId(metricId.getValue())
                .withValue(currentTotal)
                .withCurrentTotal(currentTotal)
                .withCapacity(capacity)
                .withUnlimited(unlimited));
  }

  protected static String metricIdTag(MetricId metricId) {
    return String.format("metric_id=\"%s\"", metricId.getValue());
  }

  protected static String overUsageMetricSlaTagFragment(String sla) {
    return String.format("sla=\"%s\"", sla);
  }

  protected static String overUsageMetricUsageTagFragment(String usage) {
    return String.format("usage=\"%s\"", usage);
  }

  /**
   * SLA string as stored on {@link #OVER_USAGE_METRIC}; mirrors swatch-utilization {@code
   * BaseThresholdService} (null, empty, or {@code ANY} → sentinel).
   */
  protected static String slaLabelForOverUsageMetric(UtilizationSummary.Sla sla) {
    if (sla == null
        || sla == UtilizationSummary.Sla.__EMPTY__
        || sla == UtilizationSummary.Sla.ANY) {
      return DIMENSION_ANY;
    }
    return sla.value();
  }

  /**
   * Usage string on {@link #OVER_USAGE_METRIC}; same rules as {@link #slaLabelForOverUsageMetric}.
   */
  protected static String usageLabelForOverUsageMetric(UtilizationSummary.Usage usage) {
    if (usage == null
        || usage == UtilizationSummary.Usage.__EMPTY__
        || usage == UtilizationSummary.Usage.ANY) {
      return DIMENSION_ANY;
    }
    return usage.value();
  }

  /**
   * Over-usage counter for the series where both {@code sla} and {@code usage} are {@code _ANY}
   * (payload has no specific service level or usage—{@code null}, {@code ANY}, or empty for both).
   * Equivalent to {@link #overUsageMetricCount(MetricId, UtilizationSummary.Sla,
   * UtilizationSummary.Usage)} with null {@code sla} and {@code usage}.
   */
  protected double overUsageMetricCount(MetricId metricId) {
    return overUsageMetricCount(metricId, null, null);
  }

  /**
   * Over-usage counter for the given metric and SLA/usage dimensions (after the same mapping as
   * production).
   */
  protected double overUsageMetricCount(
      MetricId metricId, UtilizationSummary.Sla sla, UtilizationSummary.Usage usage) {
    return service.getMetricByTags(
        OVER_USAGE_METRIC,
        metricIdTag(metricId),
        overUsageMetricSlaTagFragment(slaLabelForOverUsageMetric(sla)),
        overUsageMetricUsageTagFragment(usageLabelForOverUsageMetric(usage)));
  }

  /** Verifies notification is sent with correct product, metric, and utilization percentage. */
  protected void thenNotificationShouldBeSent(
      String productId, MetricId metricId, double usageValue, double capacity) {
    thenNotificationShouldBeSent(productId, metricId, usageValue, capacity, null, null, null);
  }

  /**
   * Same as {@link #thenNotificationShouldBeSent(String, MetricId, double, double)}; when non-null,
   * asserts {@code service_level} and {@code usage} in the notification context.
   */
  protected void thenNotificationShouldBeSent(
      String productId,
      MetricId metricId,
      double usageValue,
      double capacity,
      String expectedServiceLevel,
      String expectedUsage,
      Severity expectedSeverity) {
    Action notification =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS, matchesOverageNotification(orgId, productId, metricId.getValue()));

    assertThat("Notification should be sent", notification, notNullValue());
    assertThat("Notification should have timestamp", notification.getTimestamp(), notNullValue());

    var context = notification.getContext();
    assertThat("Context should not be null", context, notNullValue());
    var props = context.getAdditionalProperties();
    if (expectedServiceLevel != null) {
      assertThat(
          "Context should contain expected service_level",
          props.get("service_level"),
          equalTo(expectedServiceLevel));
    }
    if (expectedUsage != null) {
      assertThat(
          "Context should contain expected usage", props.get("usage"), equalTo(expectedUsage));
    }
    if (expectedSeverity != null) {
      assertEquals(
          expectedSeverity.name(),
          notification.getSeverity(),
          "Over-usage notification should declare severity " + expectedSeverity);
    }

    var events = notification.getEvents();
    assertThat("Notification should contain events list", events, notNullValue());
    assertThat("Notification should have at least one event", events.size(), greaterThan(0));

    var payload = events.get(0).getPayload();
    assertThat("Notification event should contain payload", payload, notNullValue());

    double expectedUtilizationPercent = (usageValue / capacity) * 100.0;
    String expectedUtilizationStr = String.format("%.2f", expectedUtilizationPercent);

    assertThat(
        "Payload should contain correct utilization_percentage",
        payload.getAdditionalProperties().get("utilization_percentage"),
        equalTo(expectedUtilizationStr));
  }
}
