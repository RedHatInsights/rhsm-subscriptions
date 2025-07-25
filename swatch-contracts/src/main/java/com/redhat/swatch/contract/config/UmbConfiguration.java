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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Slf4j
public class UmbConfiguration {

  @ConfigProperty(name = "UMB_KEYSTORE_PATH")
  Optional<String> keystorePath;

  @ConfigProperty(name = "UMB_KEYSTORE_PASSWORD")
  Optional<String> keystorePassword;

  @ConfigProperty(name = "TRUSTSTORE_PATH")
  Optional<String> truststorePath;

  @ConfigProperty(name = "TRUSTSTORE_PASSWORD")
  Optional<String> truststorePassword;

  @ConfigProperty(name = "UMB_PORT", defaultValue = "5671")
  int umbPort;

  @ConfigProperty(name = "UMB_DISABLE_SSL")
  boolean disableSsl;

  @Produces
  @Identifier("umb")
  AmqpClientOptions amqpClientOptions() {
    var options = new AmqpClientOptions();
    options.setPort(umbPort);
    if (!disableSsl) {
      configureAmqpSslOptions(options);
    }

    return options;
  }

  private void configureAmqpSslOptions(AmqpClientOptions options) {
    if (truststorePath.isPresent() && truststorePassword.isPresent()) {
      var trustOptions = new PfxOptions();
      trustOptions.setPath(truststorePath.get());
      trustOptions.setPassword(truststorePassword.get());
      options.setSsl(true).setPfxTrustOptions(trustOptions);
    }
    if (keystorePath.isPresent() && keystorePassword.isPresent()) {
      var keyCertOptions = new PfxOptions();
      keyCertOptions.setPath(keystorePath.get());
      keyCertOptions.setPassword(keystorePassword.get());
      options.setSsl(true).setPfxKeyCertOptions(keyCertOptions);
    }
  }
}
