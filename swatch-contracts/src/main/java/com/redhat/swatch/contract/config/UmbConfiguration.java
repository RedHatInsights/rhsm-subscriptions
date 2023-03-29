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
import java.net.URI;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Produces;
import javax.jms.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.apache.qpid.jms.util.PropertyUtil;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class UmbConfiguration {

  @ConfigProperty(name = "quarkus.qpid-jms.url")
  URI brokerUrl;

  @ConfigProperty(name = "KEYSTORE_PATH")
  Optional<String> keystorePath;

  @ConfigProperty(name = "KEYSTORE_PASSWORD")
  Optional<String> keystorePassword;

  @ConfigProperty(name = "TRUSTSTORE_PATH")
  Optional<String> truststorePath;

  @ConfigProperty(name = "TRUSTSTORE_PASSWORD")
  Optional<String> truststorePassword;

  @ConfigProperty(name = "UMB_NAMESPACE")
  Optional<String> namespace;

  @ConfigProperty(name = "UMB_SERVICE_ACCOUNT_NAME")
  Optional<String> serviceAccountName;

  @Produces
  @Alternative
  @Priority(Integer.MAX_VALUE)
  @ApplicationScoped
  ConnectionFactory factory() throws Exception { // NOSONAR
    var options = new HashMap<String, String>();
    if (!Objects.equals(brokerUrl.getHost(), "localhost")) {
      options.put("transport.useOpenSSL", "true");
      options.put("transport.trustStoreType", "PKCS12");
      options.put("transport.keyStoreType", "PKCS12");
      keystorePath.ifPresent(s -> options.put("transport.keyStoreLocation", s));
      keystorePassword.ifPresent(s -> options.put("transport.keyStorePassword", s));
      truststorePath.ifPresent(s -> options.put("transport.trustStoreLocation", s));
      truststorePassword.ifPresent(s -> options.put("transport.trustStorePassword", s));
    }

    var fullUrl = PropertyUtil.replaceQuery(brokerUrl, options);
    log.info("broker URL: {}", brokerUrl);
    return new JmsConnectionFactory(fullUrl);
  }

  public String getConsumerTopic(String topic) {
    if (!topic.startsWith("VirtualTopic")) {
      return topic;
    }
    if (namespace.isEmpty() || serviceAccountName.isEmpty()) {
      throw new IllegalStateException(
          "VirtualTopic usage requires both UMB_NAMESPACE and UMB_SERVICE_ACCOUNT_NAME to be set.");
    }
    String subscriptionId = String.format("swatch-%s-%s", namespace.get(), topic.replace(".", "_"));
    return String.format("Consumer.%s.%s.%s", serviceAccountName.get(), subscriptionId, topic);
  }
}
