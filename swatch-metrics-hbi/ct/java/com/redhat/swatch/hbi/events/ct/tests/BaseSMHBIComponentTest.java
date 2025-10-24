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
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.UnleashService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.Topics;
import com.redhat.swatch.hbi.events.ct.api.SwatchMetricsHbiRestService;
import com.redhat.swatch.hbi.model.FlushResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("metrics-hbi")
public class BaseSMHBIComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge =
      new KafkaBridgeService().subscribeToTopic(Topics.SWATCH_SERVICE_INSTANCE_INGRESS);

  @Unleash static UnleashService unleash = new UnleashService();

  @Quarkus(service = "swatch-metrics-hbi")
  static SwatchMetricsHbiRestService swatchMetricsHbi = new SwatchMetricsHbiRestService();

  protected static final String EMIT_EVENTS = "swatch.swatch-metrics-hbi.emit-events";

  /**
   * Flush the outbox and continue until the expected flush count is reached, or we reach the
   * configured await timeout.
   *
   * @param expectedFlushCount the expected number of records to flush.
   */
  protected void flushOutbox(int expectedFlushCount) {
    AtomicLong counter = new AtomicLong(0);
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(2), Duration.ofSeconds(20))
            .timeoutMessage(
                "Unable to flush the expected number of outbox records in time: %s",
                expectedFlushCount);

    AwaitilityUtils.untilIsTrue(
        () -> {
          FlushResponse response = swatchMetricsHbi.flushOutboxSynchronously();
          return counter.addAndGet(response.getCount()) >= expectedFlushCount;
        },
        settings);
  }
}
