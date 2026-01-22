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
package api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.swatch.contract.test.model.OperationalProductEvent;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.EventTypeEnum;
import com.redhat.swatch.contract.test.model.OperationalProductEvent.ProductCategoryEnum;
import domain.Offering;
import io.restassured.http.ContentType;
import java.time.OffsetDateTime;

/**
 * Builder for creating and sending OperationalProductEvent messages via Artemis. Provides methods
 * for constructing product update event messages from test domain objects.
 */
public class OperationalProductArtemisSender {

  private static final String PRODUCT_EVENT_CHANNEL =
      "VirtualTopic.services.productservice.Product";
  private final ContractsArtemisService artemisService;
  private final ObjectMapper mapper = new ObjectMapper();

  protected OperationalProductArtemisSender(ContractsArtemisService artemisService) {
    this.artemisService = artemisService;
  }

  /**
   * Build and send an OperationalProductEvent message for an offering.
   *
   * @param offering the offering to send a product event for
   */
  public void send(Offering offering) {
    sendMessage(fromOffering(offering));
  }

  /**
   * Send a malformed JSON message that cannot be deserialized.
   *
   * @param malformedJson the malformed JSON string
   */
  public void sendMalformed(String malformedJson) {
    artemisService.sendText(PRODUCT_EVENT_CHANNEL, malformedJson, ContentType.JSON.toString());
  }

  /**
   * Build an OperationalProductEvent object from an Offering domain object.
   *
   * @param offering the offering test data
   * @return OperationalProductEvent object
   */
  private OperationalProductEvent fromOffering(Offering offering) {
    OperationalProductEvent event = new OperationalProductEvent();
    event.setProductCode(offering.getSku());
    event.setProductCategory(ProductCategoryEnum.PARENT_SKU);
    event.setEventType(EventTypeEnum.UPDATE);
    event.setOccurredOn(OffsetDateTime.now().toString());
    return event;
  }

  /**
   * Send an OperationalProductEvent via Artemis.
   *
   * @param event the OperationalProductEvent to send
   */
  private void sendMessage(OperationalProductEvent event) {
    try {
      artemisService.sendText(
          PRODUCT_EVENT_CHANNEL, mapper.writeValueAsString(event), ContentType.JSON.toString());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize OperationalProductEvent", e);
    }
  }
}
