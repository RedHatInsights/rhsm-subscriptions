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

import java.util.Objects;
import org.springframework.beans.BeansException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * A bean post-processor to correctly configure JAAS for Kafka. Clowder gives us a value indicating
 * the SASL mechanism to use but it is up to us to determine the classname to send to JAAS for that
 * particular SASL mechanism. This class is responsible for mapping the Clowder provided
 * configuration to what we actually need.
 */
public class KafkaJaasBeanPostProcessor implements BeanPostProcessor, Ordered {
  private final Environment environment;
  private int order = Ordered.LOWEST_PRECEDENCE;

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public KafkaJaasBeanPostProcessor(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof KafkaProperties) {
      var kafkaProperties = (KafkaProperties) bean;
      var jaas = kafkaProperties.getJaas();

      if (jaas.isEnabled()) {
        var saslMechanism =
            Objects.requireNonNullElse(environment.getProperty("KAFKA_SASL_MECHANISM"), "");

        switch (saslMechanism) {
          case "PLAIN":
            jaas.setLoginModule("org.apache.kafka.common.security.plain.PlainLoginModule");
            break;
          case "SCRAM-SHA-512":
          case "SCRAM-SHA-256":
            jaas.setLoginModule("org.apache.kafka.common.security.scram.ScramLoginModule");
            break;
          default:
            throw new InvalidPropertyException(
                kafkaProperties.getClass(),
                "properties.sasl.mechanism",
                "Invalid SASL mechanism defined: " + saslMechanism);
        }
      }

      return kafkaProperties;
    }
    return bean;
  }
}
