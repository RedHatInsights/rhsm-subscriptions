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
import org.junit.jupiter.api.Test;

public class SimpleUtilizationComponentTest extends BaseUtilizationComponentTest {

  protected static final String RECEIVED_METRIC = "swatch_utilization_received_total";

  @Test
  public void testServiceIsUpAndRunning() {
    service.managementServer().get("/health").then().statusCode(200);
  }

  @Test
  public void testReceivedMetricIsIncremented() {
    // arrange
    double before = service.getMetricByTags(RECEIVED_METRIC);

    // act
    kafkaBridge.produceKafkaMessage(UTILIZATION, "any");

    // assert
    AwaitilityUtils.untilAsserted(() -> {
      double after = service.getMetricByTags(RECEIVED_METRIC);
      assertTrue(after > before, "The metric should have been incremented. "
          + "Previous value: " + before + ", new value: " + after + ".");
    });
  }
}
