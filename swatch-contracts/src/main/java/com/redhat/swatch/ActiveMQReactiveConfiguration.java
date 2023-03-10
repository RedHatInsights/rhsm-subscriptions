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
package com.redhat.swatch;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;

// import org.apache.activemq.ActiveMQSslConnectionFactory;

@ApplicationScoped
@Slf4j
// @ConfigProperties(prefix = "quarkus.activemq")
public class ActiveMQReactiveConfiguration {

  /*  private UmbProperties umbProperties;

  private String brokerUrl = "vm://localhost?broker.persistent=false";



  public ActiveMQReactiveConfiguration(UmbProperties umbProperties*/
  /*, String brokerUrl*/
  /*) {
  this.umbProperties = umbProperties;
  */
  /*this.brokerUrl = brokerUrl;*/
  /*

  }*/

  @Produces
  ConnectionFactory factory() {
    return new ActiveMQJMSConnectionFactory("tcp://localhost:61616", "quarkus", "quarkus");
  }

  /*@Produces
  public ActiveMQSslConnectionFactory activeMQSslConnectionFactory() throws Exception {
      ActiveMQSslConnectionFactory factory = new ActiveMQSslConnectionFactory();
      factory.setExceptionListener(e -> log.error("Exception thrown in ActiveMQ connection", e));
      if (StringUtil.isNullOrEmpty(brokerUrl)) {
          factory.setBrokerURL(brokerUrl);
          if (!umbProperties.providesTruststore() || !umbProperties.usesClientAuth()) {
              log.warn("UMB config requires keystore and truststore - not provided or not valid.");
          }
      } else {
          // default to an embedded broker
          log.debug("Defaulting to an embedded broker");
          factory.setBrokerURL("vm://localhost?broker.persistent=false");
      }
      if (umbProperties.providesTruststore()) {
          factory.setTrustStore(ActiveMQReactiveConfiguration.class.getResource("truststore.jks").toURI().getPath());
          factory.setTrustStorePassword(String.valueOf(umbProperties.getTruststorePassword()));
      }
      if (umbProperties.usesClientAuth()) {
          factory.setKeyStore(ActiveMQReactiveConfiguration.class.getResource("keystore.jks").toURI().getPath());
          factory.setKeyStorePassword(String.valueOf(umbProperties.getKeystorePassword()));
      }
      return factory;
  }*/
}
