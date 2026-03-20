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

import api.MessageValidators;
import api.MetricsSwatchService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.List;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-metrics")
public class BaseMetricsComponentTest {

  static final MetricId VCPUS = MetricIdUtils.getVCpus();

  @KafkaBridge static KafkaBridgeService kafkaBridge = new KafkaBridgeService();

  @Quarkus(service = "swatch-metrics")
  static MetricsSwatchService service = new MetricsSwatchService();

  protected String orgId;

  @BeforeAll
  static void subscribeToTopics() {
    kafkaBridge.subscribeToTopic(SWATCH_SERVICE_INSTANCE_INGRESS);
  }

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }

  protected void whenMeteringIsPerformed(String productTag, String metricId) {
    service.performMeteringOperation(productTag, metricId, orgId);
  }

  protected List<Event> thenEventsAreProduced(String instanceId, String metricId) {
    return kafkaBridge.waitForKafkaMessage(
        SWATCH_SERVICE_INSTANCE_INGRESS,
        MessageValidators.isEventForInstance(instanceId, metricId),
        1);
  }
}
