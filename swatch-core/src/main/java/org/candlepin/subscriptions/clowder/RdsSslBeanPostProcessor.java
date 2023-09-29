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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * A bean post-processor to correctly configure Ssl for Postgres on RDS. Clowder gives us a value
 * indicating the SSL mode to use and a value of 'verify-full' will cause a certificate file to be
 * generated and the path added to the jdbc url.
 */
public class RdsSslBeanPostProcessor implements BeanPostProcessor, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RdsSslBeanPostProcessor.class);

  private final Environment environment;
  private int order = Ordered.LOWEST_PRECEDENCE;

  @Override
  public int getOrder() {
    return order;
  }

  public void setOrder(int order) {
    this.order = order;
  }

  public RdsSslBeanPostProcessor(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof DataSourceProperties && beanName.equals("rhsmDataSourceProperties")) {
      String sslMode = environment.getProperty("DATABASE_SSL_MODE");
      // if ssl is disabled no need to change url
      if (sslMode == null || sslMode.isEmpty() || sslMode.equals("disable")) {
        log.info("Connecting to database with SSL Mode: disable");
        return bean;
      }

      boolean verifyFull = sslMode.equals("verify-full");
      String jdbcUrl = ((DataSourceProperties) bean).getUrl();
      jdbcUrl = jdbcUrl + "&sslmode=" + sslMode;
      log.info("Connecting to database with SSL Mode: {}", sslMode);

      if (verifyFull) {
        String rdsCa = environment.getProperty("DATABASE_SSL_CERT");
        jdbcUrl = jdbcUrl + "&sslrootcert=" + createTempRdsCertFile(rdsCa);
      }

      ((DataSourceProperties) bean).setUrl(jdbcUrl);
    }
    return bean;
  }

  private String createTempRdsCertFile(String certData) {
    if (certData != null) {
      return createTempCertFile("rds-ca-root", certData);
    } else {
      throw new IllegalStateException(
          "'database.sslMode' is set to 'verify-full' in the Clowder config but the 'database.rdsCa' field is missing");
    }
  }

  private String createTempCertFile(String fileName, String certData) {
    byte[] cert = certData.getBytes(UTF_8);
    try {
      File certFile = File.createTempFile(fileName, ".crt");
      setDeleteTempCertFileOnExit(certFile);
      return Files.write(Path.of(certFile.getAbsolutePath()), cert).toString();
    } catch (IOException e) {
      throw new UncheckedIOException("Certificate file creation failed", e);
    }
  }

  private void setDeleteTempCertFileOnExit(File certFile) {
    try {
      certFile.deleteOnExit();
    } catch (SecurityException e) {
      log.warn(
          String.format(
              "Delete on exit of the %s cert file denied by the security manager",
              certFile.getName()),
          e);
    }
  }
}
