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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UtilizationSummaryConsumerComponentTest extends BaseUtilizationComponentTest {

  protected static final String RECEIVED_METRIC = "swatch_utilization_received_total";

  @BeforeAll
  static void enableSendNotificationsFeatureFlag() {
    unleash.enableFlag(SEND_NOTIFICATIONS);
  }

  @AfterAll
  static void disableSendNotificationsFeatureFlag() {
    unleash.disableFlag(SEND_NOTIFICATIONS);
  }

  @Test
  public void testReceivedMetricIsIncremented() {
    // arrange
    double before = service.getMetricByTags(RECEIVED_METRIC);
    var utilizationSummary = createValidPaygPayload();

    // act
    kafkaBridge.produceKafkaMessage(UTILIZATION, utilizationSummary);

    // assert
    AwaitilityUtils.untilAsserted(
        () -> {
          double after = service.getMetricByTags(RECEIVED_METRIC);
          assertTrue(
              after > before,
              "The metric should have been incremented. "
                  + "Previous value: "
                  + before
                  + ", new value: "
                  + after
                  + ".");
        });
  }

  private UtilizationSummary createValidPaygPayload() {
    return new UtilizationSummary()
        .withOrgId(orgId)
        .withProductId("rosa")
        .withBillingAccountId(RandomUtils.generateRandom())
        .withMeasurements(
            List.of(new Measurement().withMetricId(MetricIdUtils.getCores().getValue())));
  }
}
