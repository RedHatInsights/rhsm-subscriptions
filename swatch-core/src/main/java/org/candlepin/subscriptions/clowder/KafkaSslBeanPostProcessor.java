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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.Consumer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

/**
 * A bean post-processor to correctly configure the truststore for Kafka. The Clowder JSON can
 * optionally specify the CA certificate. If the CA certificate isn't defined, the system truststore
 * should be used. This is indicated in {@link KafkaProperties.Ssl} by having the truststore set to
 * null. Unfortunately, there is no easy way to specify a default value of null for a property.
 * Currently, our configuration looks like
 *
 * <pre>
 *  kafka:
 *    ssl:
 *      trust-store-certificates: ${clowder.kafka.brokers.cacert:}
 *      trust-store-type: ${clowder.kafka.brokers.cacert.type:}
 * </pre>
 *
 * The {@link ClowderJsonPathPropertySource} will return null for the evaluation of
 * <tt>clowder.kafka.brokers.cacert</tt> (since that key is missing in the Clowder JSON), but Spring
 * will then fall back to the default which is the empty string. If no default is provided, Spring
 * instead uses the literal value of "${clowder.kafka.brokers.cacert}". So this class exists to set
 * the KafkaProperties.Ssl truststore values back to null if any of them are set to the empty
 * string.
 */
public class KafkaSslBeanPostProcessor implements BeanPostProcessor, Ordered {
  private int order = Ordered.LOWEST_PRECEDENCE;

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof KafkaProperties kafkaProperties) {
      var sslProps = kafkaProperties.getSsl();

      checkEmptySetNull(sslProps.getTrustStoreCertificates(), sslProps::setTrustStoreCertificates);
      checkEmptySetNull(sslProps.getTrustStoreLocation(), sslProps::setTrustStoreLocation);
      checkEmptySetNull(sslProps.getTrustStoreType(), sslProps::setTrustStoreType);
      checkEmptySetNull(sslProps.getTrustStorePassword(), sslProps::setTrustStorePassword);
    }
    return bean;
  }

  private void checkEmptySetNull(String currentValue, Consumer<String> setter) {
    if (currentValue != null && currentValue.isEmpty()) {
      setter.accept(null);
    }
  }

  private void checkEmptySetNull(Resource currentValue, Consumer<Resource> setter) {
    try {
      if (currentValue != null
          && currentValue.getContentAsString(Charset.defaultCharset()).isEmpty()) {
        setter.accept(null);
      }
    } catch (IOException e) {
      setter.accept(null);
    }
  }
}
