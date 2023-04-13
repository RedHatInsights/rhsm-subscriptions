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

import io.smallrye.common.annotation.Identifier;
import io.vertx.amqp.AmqpClientOptions;
import io.vertx.core.net.PfxOptions;
import java.net.URI;
import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import lombok.extern.slf4j.Slf4j;
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
  @Identifier("umb")
  AmqpClientOptions amqpClientOptions() {
    var options = new AmqpClientOptions();
    if (truststorePath.isPresent() && truststorePassword.isPresent()) {
      var trustOptions = new PfxOptions();
      trustOptions.setPath(truststorePath.get());
      trustOptions.setPassword(truststorePassword.get());
      options
          .setSsl(true)
          .setPfxTrustOptions(trustOptions);
    }
    if (keystorePath.isPresent() && keystorePassword.isPresent()) {
      var keyCertOptions = new PfxOptions();
      keyCertOptions.setPath(keystorePath.get());
      keyCertOptions.setPassword(keystorePassword.get());
      options
          .setSsl(true)
          .setPfxKeyCertOptions(keyCertOptions);
    }
    return options;
  }

  // TODO figure out how to get smallrye messaging config to call this

}
