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

import static com.redhat.swatch.component.tests.utils.Topics.IT_PRODUCT_SYNC;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ProductEdgeComponentTest extends BaseProductUmbComponentTest {

  @TestPlanName("product-edge-TC001")
  @Test
  void shouldProcessMessageWhenProductCodeIsMissing() {
    // Given: A parent SKU event with a null productCode
    OperationalProductEvent event = givenParentSkuEvent(null);

    // When: Publishing message to Kafka
    whenKafkaMessageIsPublished(event);

    // Then: Message is consumed with productCode=null and childSku=false
    thenMessageIsConsumed(event, "kafka");

    // And: a subsequent valid message is still consumed
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenKafkaMessageIsPublished(probe);
    thenMessageIsConsumedAndSynced(probe, "kafka");
  }

  @TestPlanName("product-edge-TC002")
  @Test
  void shouldProcessMessageWhenEventTypeIsMissing() {
    // Given: A parent SKU event with a null eventType
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    event.setEventType(null);

    // When: Publishing message to UMB
    whenUmbMessageIsPublished(event);

    // Then: Message is consumed with eventType=null and sync is invoked
    thenMessageIsConsumedAndSynced(event, "umb");
  }

  @TestPlanName("product-edge-TC003")
  @Test
  void shouldProcessEmptyJsonObjectMessage() {
    // Given: An empty JSON object
    OperationalProductEvent event = givenEmptyProductEvent();

    // When: Publishing {} to Kafka
    whenKafkaPayloadIsPublished(Map.of());

    // Then: Message is consumed with null fields
    thenMessageIsConsumed(event, "kafka");

    // And: a subsequent valid message is still consumed
    OperationalProductEvent probe = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    whenKafkaMessageIsPublished(probe);
    thenMessageIsConsumedAndSynced(probe, "kafka");
  }

  @TestPlanName("product-edge-TC004")
  @Test
  void shouldProcessMessageWithUnexpectedFields() {
    // Given: A valid parent SKU event plus fields that are not in the schema
    OperationalProductEvent event = givenParentSkuEvent("RH" + RandomUtils.generateRandom());
    Map<String, Object> payload = givenPayloadWithUnexpectedFields(event);

    // When: Publishing the payload to Kafka
    whenKafkaPayloadIsPublished(payload);

    // Then: Expected fields are consumed and extra fields are ignored
    thenMessageIsConsumedAndSynced(event, "kafka");
    thenUnknownFieldsWereIgnored();
  }

  private OperationalProductEvent givenEmptyProductEvent() {
    return new OperationalProductEvent();
  }

  private Map<String, Object> givenPayloadWithUnexpectedFields(OperationalProductEvent event) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("productCode", event.getProductCode());
    payload.put("productCategory", "Parent SKU");
    payload.put("eventType", "Update");
    payload.put("occurredOn", event.getOccurredOn());
    payload.put("unexpectedField", "should-be-ignored");
    payload.put("extraNumber", 42);
    return payload;
  }

  private void whenKafkaPayloadIsPublished(Object payload) {
    kafkaBridge.produceKafkaMessage(IT_PRODUCT_SYNC, payload);
  }

  private void thenUnknownFieldsWereIgnored() {
    service.logs().assertDoesNotContain("Unrecognized field");
  }
}
