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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

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
      String saslMechanism = environment.getProperty("KAFKA_SASL_MECHANISM");
      // if sasl mechanism isn't specified, kafka communication is unauthenticated, nothing to do
      if (!StringUtils.hasText(saslMechanism)) {
        return bean;
      }

      kafkaProperties.getSecurity().setProtocol(environment.getProperty("KAFKA_SASL_PROTOCOL"));
      String truststoreCertificate = environment.getProperty("KAFKA_SSL_TRUSTSTORE_CERTIFICATE");

      // NOTE: if the truststore certificate isn't specified, then the system truststore will be
      // used (which is necessary for managed kafka).
      if (StringUtils.hasText(truststoreCertificate)) {
        kafkaProperties.getSsl().setTrustStoreCertificates(truststoreCertificate);
        kafkaProperties.getSsl().setTrustStoreType("PEM");
      }
      kafkaProperties.getProperties().put("sasl.mechanism", saslMechanism);

      // NOTE: here we manually construct sasl.jaas.config rather than using the
      // KafkaProperties.Jaas object, since KafkaProperties.Jaas is ignored if
      // spring.kafka.jaas.enabled is true, and we do not have a clean way of setting jaas.enabled
      // early enough.
      kafkaProperties.getProperties().put("sasl.jaas.config", getJaasConfig(saslMechanism));
    }
    return bean;
  }

  public String getJaasConfig(String saslMechanism) {
    // configure the sasl.jaas.config property, inspired by ClowderConfigSource
    String username = environment.getProperty("KAFKA_USERNAME");
    String password = environment.getProperty("KAFKA_PASSWORD");
    switch (saslMechanism) {
      case "PLAIN":
        return "org.apache.kafka.common.security.plain.PlainLoginModule required "
            + "username=\""
            + username
            + "\" password=\""
            + password
            + "\";";
      case "SCRAM-SHA-512":
      case "SCRAM-SHA-256":
        return "org.apache.kafka.common.security.scram.ScramLoginModule required "
            + "username=\""
            + username
            + "\" password=\""
            + password
            + "\";";
      default:
        throw new IllegalArgumentException("Invalid SASL mechanism defined: " + saslMechanism);
    }
  }
}
