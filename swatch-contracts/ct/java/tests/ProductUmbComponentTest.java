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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import org.junit.jupiter.api.Test;

public class ProductUmbComponentTest extends BaseProductUmbComponentTest {

  @TestPlanName("product-umb-TC001")
  @Test
  void shouldProcessValidParentSkuMessage() {
    // Given: A valid parent SKU operational product event
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: Message is consumed and the offering sync service is invoked
    thenMessageIsConsumedAndSynced(event, "umb");
  }

  @TestPlanName("product-umb-TC002")
  @Test
  void shouldProcessValidChildSkuMessage() {
    // Given: A valid child SKU operational product event (starts with "SVC")
    OperationalProductEvent event = givenChildSkuEvent("SVC" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: Message is consumed with childSku flag as true and sync is invoked
    thenMessageIsConsumedAndSynced(event, "umb");
  }

  @TestPlanName("product-umb-TC003")
  @Test
  void shouldHandleMalformedJsonMessage() {
    // Given: A malformed JSON message and baseline consume log count
    String malformedJson = "not-valid-json";
    int consumeLogsBefore = countLogsContaining("IT Product message consumed: source=umb");

    // When: Publishing malformed message to UMB
    whenMalformedUmbMessageIsPublished(malformedJson);

    // Then: Error is logged and the bad payload is not consumed
    service.logs().assertContains("Unable to read UMB product message for JSON");
    assertEquals(
        consumeLogsBefore,
        countLogsContaining("IT Product message consumed: source=umb"),
        "Malformed JSON must not produce a consume log");

    // And: a subsequent valid message is still consumed
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenUmbMessageIsPublished(probe);
    thenMessageIsConsumedAndSynced(probe, "umb");
  }

  @TestPlanName("product-umb-TC005")
  @Test
  void shouldIgnoreMessageWhenFeatureFlagDisabled() {
    // Given: Feature flag is disabled and a valid parent SKU event
    unleash.disableProductServiceConsumer();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: Consumer is disabled and the message is not processed
    thenUmbConsumerReportsDisabled();
    thenMessageIsNotConsumed(event, "umb");
    thenSyncServiceWasNotInvoked(event);
  }

  @TestPlanName("product-umb-TC006")
  @Test
  void shouldIgnoreMessageWhenUmbDisabledViaVariant() {
    // Given: Flag enabled with Kafka enabled and UMB disabled via variant
    unleash.enableProductServiceConsumerKafkaOnly();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: UMB is independently disabled
    thenUmbConsumerReportsDisabled();
    thenMessageIsNotConsumed(event, "umb");
    thenSyncServiceWasNotInvoked(event);
  }

  @TestPlanName("product-umb-TC007")
  @Test
  void shouldProcessWhenUmbEnabledAndKafkaDisabled() {
    // Given: Flag enabled with UMB enabled and Kafka disabled via variant
    unleash.enableProductServiceConsumerUmbOnly();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: UMB consumes independently and the sync service is invoked
    thenMessageIsConsumedAndSynced(event, "umb");
  }

  @TestPlanName("product-umb-TC008")
  @Test
  void shouldProcessMessageWhenBothConsumersEnabled() {
    // Given: Flag enabled with both Kafka and UMB consumers enabled via variant
    unleash.enableProductServiceConsumerBothConsumers();
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: UMB consumes and the sync service is invoked
    thenMessageIsConsumedAndSynced(event, "umb");
  }

  private void thenUmbConsumerReportsDisabled() {
    service.logs().assertContains("IT Product UMB consumer is disabled by feature flag.");
  }
}
