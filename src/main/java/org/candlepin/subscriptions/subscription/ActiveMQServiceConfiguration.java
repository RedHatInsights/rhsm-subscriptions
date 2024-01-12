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
package org.candlepin.subscriptions.subscription;

import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.candlepin.subscriptions.umb.UmbProperties;
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@Profile("capacity-ingress")
public class ActiveMQServiceConfiguration {
  private final UmbProperties umbProperties;

  public ActiveMQServiceConfiguration(UmbProperties umbProperties) {
    this.umbProperties = umbProperties;
  }

  @Bean
  @ConfigurationProperties(prefix = "spring.activemq")
  public ActiveMQProperties activeMQProperties() {
    return new ActiveMQProperties();
  }

  @Bean
  public ActiveMQSslConnectionFactory activeMQSslConnectionFactory(
      ActiveMQProperties activeMQProperties) throws Exception {
    ActiveMQSslConnectionFactory factory = new ActiveMQSslConnectionFactory();
    factory.setExceptionListener(e -> log.error("Exception thrown in ActiveMQ connection", e));
    if (StringUtils.hasText(activeMQProperties.getUser())) {
      factory.setUserName(activeMQProperties.getUser());
    }

    if (StringUtils.hasText(activeMQProperties.getPassword())) {
      factory.setPassword(activeMQProperties.getPassword());
    }

    if (StringUtils.hasText(activeMQProperties.getBrokerUrl())) {
      factory.setBrokerURL(activeMQProperties.getBrokerUrl());
      if (!umbProperties.providesTruststore() || !umbProperties.usesClientAuth()) {
        log.warn("UMB config requires keystore and truststore - not provided or not valid.");
      }
    } else {
      // default to an embedded broker
      log.debug("Defaulting to an embedded broker");
      factory.setBrokerURL("vm://localhost?broker.persistent=false");
    }
    if (umbProperties.providesTruststore()) {
      factory.setTrustStore(umbProperties.getTruststore().getFile().getAbsolutePath());
      factory.setTrustStorePassword(String.valueOf(umbProperties.getTruststorePassword()));
    }
    if (umbProperties.usesClientAuth()) {
      factory.setKeyStore(umbProperties.getKeystore().getFile().getAbsolutePath());
      factory.setKeyStorePassword(String.valueOf(umbProperties.getKeystorePassword()));
    }
    return factory;
  }
}
