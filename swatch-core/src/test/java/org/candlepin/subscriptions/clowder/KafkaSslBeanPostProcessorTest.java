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
package org.candlepin.subscriptions.clowder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.core.io.InputStreamResource;

class KafkaSslBeanPostProcessorTest {
  @Test
  void postProcessAfterInitialization() {
    var kafkaProperties = new KafkaProperties();
    var ssl = kafkaProperties.getSsl();
    ssl.setTrustStoreCertificates("");
    ssl.setTrustStoreType("");
    ssl.setTrustStorePassword("");

    var postProcessor = new KafkaSslBeanPostProcessor();
    postProcessor.postProcessAfterInitialization(kafkaProperties, "kafkaProperties");

    assertNull(ssl.getTrustStoreCertificates());
    assertNull(ssl.getTrustStoreType());
    assertNull(ssl.getTrustStorePassword());
  }

  @Test
  void postProcessAfterInitializationForResource() {
    var kafkaProperties = new KafkaProperties();
    var ssl = kafkaProperties.getSsl();
    ssl.setTrustStoreLocation(new InputStreamResource(new ByteArrayInputStream(new byte[0])));
    ssl.setTrustStoreType("");
    ssl.setTrustStorePassword("");

    var postProcessor = new KafkaSslBeanPostProcessor();
    postProcessor.postProcessAfterInitialization(kafkaProperties, "kafkaProperties");

    assertNull(ssl.getTrustStoreLocation());
    assertNull(ssl.getTrustStoreType());
    assertNull(ssl.getTrustStorePassword());
  }
}
