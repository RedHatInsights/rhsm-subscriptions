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
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.redhat.swatch.contract.product.umb.CanonicalMessage;
import domain.Subscription;
import io.restassured.http.ContentType;
import utils.CanonicalMessageMapper;

/**
 * Builder for creating and sending CanonicalMessage messages via Artemis. Provides a fluent API for
 * constructing subscriptions messages from test domain objects.
 */
public class CanonicalMessageArtemisSender {

  public static final String SUBSCRIPTION_CHANNEL = "VirtualTopic.canonical.subscription";
  private final ContractsArtemisService artemisService;
  private final XmlMapper mapper = CanonicalMessage.createMapper();

  protected CanonicalMessageArtemisSender(ContractsArtemisService artemisService) {
    this.artemisService = artemisService;
    this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  /**
   * Build and send a CanonicalMessage message from a Subscription domain object.
   *
   * @param subscription the subscription test data
   */
  public void send(Subscription subscription) {
    sendMessage(CanonicalMessageMapper.mapActiveSubscription(subscription));
  }

  /**
   * Build and send a CanonicalMessage for a terminated subscription. This sets the product status
   * to "Terminated" which signals the system to update the subscription's end date to the
   * termination date.
   *
   * @param subscription the subscription test data with termination end date
   */
  public void sendTerminated(Subscription subscription) {
    sendMessage(CanonicalMessageMapper.mapTerminatedSubscription(subscription));
  }

  /**
   * Send a CanonicalMessage via Artemis.
   *
   * @param message the CanonicalMessage to send
   */
  private void sendMessage(CanonicalMessage message) {
    try {
      artemisService.send(
          SUBSCRIPTION_CHANNEL, mapper.writeValueAsString(message), ContentType.XML.toString());
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to serialize CanonicalMessage", e);
    }
  }
}
