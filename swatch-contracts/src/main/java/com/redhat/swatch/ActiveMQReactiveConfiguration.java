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

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.utils.uri.URISupport;
import org.eclipse.microprofile.config.inject.ConfigProperty;

// import org.apache.activemq.ActiveMQSslConnectionFactory;

@ApplicationScoped
@Slf4j
// @ConfigProperties(prefix = "quarkus.activemq")
public class ActiveMQReactiveConfiguration {

  @ConfigProperty(name = "ACTIVEMQ_BROKER_URL", defaultValue = "vm://0")
  URI brokerUrl;

  @ConfigProperty(name = "ACTIVEMQ_SSL_ENABLED", defaultValue = "false")
  boolean sslEnabled;

  @ConfigProperty(name = "KEYSTORE_PATH")
  Optional<String> keystorePath;

  @ConfigProperty(name = "KEYSTORE_PASSWORD")
  Optional<String> keystorePassword;

  @ConfigProperty(name = "TRUSTSTORE_PATH")
  Optional<String> truststorePath;

  @ConfigProperty(name = "TRUSTSTORE_PASSWORD")
  Optional<String> truststorePassword;

  EmbeddedActiveMQ embeddedActiveMQ;

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
  ConnectionFactory factory() throws Exception {
    var factory =  new ActiveMQJMSConnectionFactory();

    var options = new HashMap<String,Object>();
    if (!Objects.equals(brokerUrl.getScheme(), "vm")) {
      options.put("sslEnabled", String.valueOf(sslEnabled));
      keystorePath.ifPresent(s -> options.put("keyStorePath", s));
      keystorePassword.ifPresent(s -> options.put("keyStorePassword", s));
      truststorePath.ifPresent(s -> options.put("trustStorePath", s));
      truststorePassword.ifPresent(s -> options.put("trustStorePassword", s));
    }

    var fullUrl = URISupport.createURIWithQuery(brokerUrl, URISupport.createQueryString(options));
    log.info("broker URL: {}", fullUrl);
    factory.setBrokerURL(fullUrl.toString());
    return factory;
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
  void onStart(@Observes StartupEvent ev) throws Exception {
    if (Objects.equals("vm", brokerUrl.getScheme())) {
      synchronized (this) {
        Configuration configuration = new ConfigurationImpl();
        configuration.addAcceptorConfiguration("in-vm", brokerUrl.toString());
        configuration.setMaxDiskUsage(100);
        configuration.setSecurityEnabled(false);
        configuration.setAddressConfigurations(List.of(new CoreAddressConfiguration().setName("*").setQueueConfigs(List.of(new QueueConfiguration().setName("*").setAutoDelete(false)))));
        embeddedActiveMQ = new EmbeddedActiveMQ();
        embeddedActiveMQ.setConfiguration(configuration);
        embeddedActiveMQ.start();
      }
    }
  }

  void onStop(@Observes ShutdownEvent ev) throws Exception {
    if (embeddedActiveMQ != null) {
      synchronized (this) {
        embeddedActiveMQ.stop();
      }
    }
  }
}
