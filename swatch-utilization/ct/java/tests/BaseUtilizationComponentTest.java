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

import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;

import api.UtilizationUnleashService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.SwatchService;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import domain.Product;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-utilization")
public class BaseUtilizationComponentTest {
  protected static final String OVER_USAGE_METRIC = "swatch_utilization_over_usage_total";

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService().subscribeToTopic(NOTIFICATIONS);

  @Unleash static UtilizationUnleashService unleash = new UtilizationUnleashService();

  @Quarkus(service = "swatch-utilization")
  static SwatchService service = new SwatchService();

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
                .withCurrentTotal(currentTotal)
                .withCapacity(capacity)
                .withUnlimited(unlimited));
  }

  protected static String metricIdTag(MetricId metricId) {
    return String.format("metric_id=\"%s\"", metricId.getValue());
  }
}
