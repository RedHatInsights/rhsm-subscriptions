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

import static com.redhat.swatch.component.tests.utils.Topics.USAGE;

import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.UsageEventBuilder;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.UsageEvent;
import org.junit.jupiter.api.Test;

@Slf4j
@ComponentTest(name = "swatch-tally")
class UsageEventConsumerComponentTest extends BaseTallyComponentTest {

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService().subscribeToTopic(USAGE);

  @Test
  @TestPlanName("usage-consumer-TC001")
  void shouldConsumeUsageEventFromKafka() throws Exception {
    String orgId = "test-org-123";
    UsageEvent event =
        UsageEventBuilder.createUsageEvent()
            .withOrgId(orgId)
            .withServiceType("RHEL System")
            .withInstanceId("instance-001")
            .withTimestamp(OffsetDateTime.now())
            .withMeasurement("Cores", 4.0)
            .build();

    log.info("Publishing UsageEvent to kafka: {}", event);
    kafkaBridge.produceKafkaMessage(USAGE, event);
    log.info("Successfully sent UsageEvent message to topic: {}", USAGE);

    // Verify the message was consumed by checking logs
    logsVerifier.assertContains("Received", "usage event(s) from kafka");
    logsVerifier.assertContains("Processing usage event for org_id: " + orgId);
  }

  @Test
  @TestPlanName("usage-consumer-TC002")
  void shouldConsumeMultipleUsageEvents() throws Exception {
    String orgId = "test-org-456";
    OffsetDateTime now = OffsetDateTime.now();

    for (int i = 0; i < 3; i++) {
      UsageEvent event =
          UsageEventBuilder.createUsageEvent()
              .withOrgId(orgId)
              .withServiceType("OpenShift Cluster")
              .withInstanceId("cluster-" + i)
              .withTimestamp(now.plusHours(i))
              .withMeasurement("Cores", 8.0 + i)
              .build();

      kafkaBridge.produceKafkaMessage(USAGE, event);
      log.info("Published UsageEvent {} for org_id: {}", i, event.getOrgId());
    }

    log.info("Successfully sent 3 UsageEvent messages to topic: {}", USAGE);

    // Verify at least one batch of messages was consumed
    logsVerifier.assertContains("Received", "usage event(s) from kafka");
    logsVerifier.assertContains("Processing usage event for org_id: " + orgId);
  }
}
