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
package org.candlepin.subscriptions.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.task.TaskQueueProperties;
import org.candlepin.subscriptions.umb.CanonicalMessage;
import org.candlepin.subscriptions.umb.UmbOperationalProduct;
import org.candlepin.subscriptions.umb.UmbProperties;
import org.candlepin.subscriptions.util.KafkaConsumerRegistry;
import org.candlepin.subscriptions.util.SeekableKafkaConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@Profile("capacity-ingress")
public class OfferingWorker extends SeekableKafkaConsumer {

  private final OfferingSyncController controller;
  private final UmbProperties umbProperties;
  private final XmlMapper xmlMapper;

  @Autowired
  protected OfferingWorker(
      @Qualifier("offeringSyncTasks") TaskQueueProperties taskQueueProperties,
      KafkaConsumerRegistry kafkaConsumerRegistry,
      OfferingSyncController controller,
      UmbProperties umbProperties) {
    super(taskQueueProperties, kafkaConsumerRegistry);

    this.controller = controller;
    this.umbProperties = umbProperties;
    this.xmlMapper = CanonicalMessage.createMapper();
  }

  @KafkaListener(
      id = "#{__listener.groupId}",
      topics = "#{__listener.topic}",
      containerFactory = "offeringSyncListenerContainerFactory")
  public void receive(OfferingSyncTask task) {
    String sku = task.getSku();
    log.info("Sync for offeringSku={} triggered by OfferingSyncTask", sku);

    controller.syncOffering(sku);
  }

  @JmsListener(destination = "#{@umbProperties.productTopic}")
  public void receive(String productMessageXml) throws JsonProcessingException {
    log.debug("Received message from UMB offering product{}", productMessageXml);
    if (!umbProperties.isProcessingEnabled()) {
      log.debug("UMB processing is not enabled");
      return;
    }
    CanonicalMessage productMessage =
        xmlMapper.readValue(productMessageXml, CanonicalMessage.class);
    UmbOperationalProduct operationalProduct =
        productMessage.getPayload().getSync().getOperationalProduct();
    log.info("Received UMB message for productSku={}", operationalProduct.getSku());
    controller.syncUmbProduct(operationalProduct);
  }
}
