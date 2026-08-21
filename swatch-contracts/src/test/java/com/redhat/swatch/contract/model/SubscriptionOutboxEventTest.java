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
package com.redhat.swatch.contract.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.openapi.model.SubscriptionOutboxEvent;
import com.redhat.swatch.contract.openapi.model.SubscriptionOutboxPayload;
import org.junit.jupiter.api.Test;

class SubscriptionOutboxEventTest {

  private static final String GUIDE_EVENT =
      """
      {
        "eventId": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
        "entityType": "Subscription",
        "eventType": "CREATE",
        "eventVersion": "v1.0",
        "createdAt": 1705328400000,
        "expireAt": 1712411400000,
        "payload": {
          "subscriptionNumber": "12345678",
          "customerId": "1234567",
          "quantity": 10,
          "effectiveStartDate": 1704067200000,
          "effectiveEndDate": 1735689600000,
          "product": {
            "sku": "RH00001",
            "childProducts": [
              {"sku": "RH00002", "status": "Active"}
            ]
          }
        }
      }
      """;

  private final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void shouldParseGuideEvent() throws Exception {
    SubscriptionOutboxEvent event = mapper.readValue(GUIDE_EVENT, SubscriptionOutboxEvent.class);
    assertEquals("Subscription", event.getEntityType());

    SubscriptionOutboxPayload payload = event.getPayload();

    assertEquals("12345678", payload.getSubscriptionNumber());
    assertEquals("1234567", payload.getCustomerId());
    assertEquals("RH00001", payload.getProduct().getSku());
    assertEquals(10, payload.getQuantity());
    assertEquals(1704067200000L, payload.getEffectiveStartDate());
    assertEquals(1735689600000L, payload.getEffectiveEndDate());
    assertEquals("Active", payload.getProduct().getChildProducts().get(0).getStatus());
  }

  @Test
  void shouldDetectTerminatedChildStatus() throws Exception {
    String json =
        """
        {
          "entityType": "Subscription",
          "payload": {
            "subscriptionNumber": "87654321",
            "customerId": "9876543",
            "quantity": 5,
            "effectiveStartDate": 1706745600000,
            "effectiveEndDate": 1738368000000,
            "product": {
              "sku": "RH00005",
              "childProducts": [
                {"sku": "RH00006", "status": "Terminated"}
              ]
            }
          }
        }
        """;
    SubscriptionOutboxEvent event = mapper.readValue(json, SubscriptionOutboxEvent.class);
    assertEquals(
        "Terminated", event.getPayload().getProduct().getChildProducts().get(0).getStatus());
  }

  @Test
  void shouldHandleNullPayload() throws Exception {
    String json =
        """
        {"entityType": "Subscription"}
        """;
    SubscriptionOutboxEvent event = mapper.readValue(json, SubscriptionOutboxEvent.class);
    assertNull(event.getPayload());
  }

  @Test
  void shouldReturnNullSkuWhenNoProductInfo() throws Exception {
    String json =
        """
        {
          "entityType": "Subscription",
          "payload": {
            "subscriptionNumber": "1234",
            "customerId": "org123"
          }
        }
        """;
    SubscriptionOutboxEvent event = mapper.readValue(json, SubscriptionOutboxEvent.class);
    SubscriptionOutboxPayload payload = event.getPayload();
    assertNull(payload.getProduct());
  }

  @Test
  void shouldIgnoreNonSubscriptionEntityType() throws Exception {
    String json =
        """
        {"entityType": "Offering", "payload": {}}
        """;
    SubscriptionOutboxEvent event = mapper.readValue(json, SubscriptionOutboxEvent.class);
    assertEquals("Offering", event.getEntityType());
  }
}
