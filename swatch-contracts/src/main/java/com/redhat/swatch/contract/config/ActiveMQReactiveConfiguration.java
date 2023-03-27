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
package com.redhat.swatch.contract.config;

import io.quarkus.arc.Priority;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Produces;
import javax.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.utils.uri.URISupport;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class ActiveMQReactiveConfiguration {

  @ConfigProperty(name = "quarkus.artemis.url")
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

  @Produces
  @Alternative
  @Priority(Integer.MAX_VALUE)
  ConnectionFactory factory() throws Exception {
    var factory = new ActiveMQJMSConnectionFactory(); // NOSONAR

    var options = new HashMap<String, Object>();
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

  void onStart(@Observes StartupEvent ev) throws Exception {
    if (Objects.equals("vm", brokerUrl.getScheme())) {
      synchronized (this) {
        Configuration configuration = new ConfigurationImpl();
        configuration.addAcceptorConfiguration("in-vm", brokerUrl.toString());
        configuration.setMaxDiskUsage(100);
        configuration.setSecurityEnabled(false);
        configuration.setAddressConfigurations(
            List.of(
                new CoreAddressConfiguration()
                    .setName("*")
                    .setQueueConfigs(
                        List.of(new QueueConfiguration().setName("*").setAutoDelete(false)))));
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
