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
package com.redhat.swatch.hbi.events.ct.tests;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.SwatchMetricsHbiRestService;
import java.time.Duration;
import org.candlepin.subscriptions.json.Event;

@ComponentTest(name = "swatch-metrics-hbi")
public class BaseSMHBIComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService().subscribeToTopic(Topics.SWATCH_SERVICE_INSTANCE_INGRESS);

  @Unleash static UnleashService unleash = new UnleashService();

  @Quarkus(service = "swatch-metrics-hbi")
  static SwatchMetricsHbiRestService swatchMetricsHbi = new SwatchMetricsHbiRestService();

  protected static final String EMIT_EVENTS = "swatch.swatch-metrics-hbi.emit-events";

  /**
   * Wait for the expected Swatch events to appear in Kafka, flushing the outbox as needed.
   *
   * @param expectedMessages the message validators for the expected Swatch events
   */
  @SafeVarargs
  protected final void waitForSwatchEvents(DefaultMessageValidator<Event>... expectedMessages) {
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(2), Duration.ofSeconds(30))
            .timeoutMessage(
                "Expected Swatch events did not appear in Kafka within the timeout: %s events expected",
                expectedMessages.length);

    AwaitilityUtils.untilAsserted(
        () -> {
          // Flush the outbox to ensure events are sent to Kafka
          swatchMetricsHbi.flushOutboxSynchronously();

          // Verify each expected message appears in Kafka
          for (var expectedMessage : expectedMessages) {
            kafkaBridge.waitForKafkaMessage(
                Topics.SWATCH_SERVICE_INSTANCE_INGRESS, expectedMessage, 1);
          }
        },
        settings);
  }
}
