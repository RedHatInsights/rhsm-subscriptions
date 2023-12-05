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
package org.candlepin.subscriptions.conduit.actuator;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import org.candlepin.subscriptions.actuator.CertInfoInquisitor;
import org.candlepin.subscriptions.conduit.rhsm.client.RhsmApiProperties;
import org.candlepin.subscriptions.http.HttpClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.info.ConditionalOnEnabledInfoContributor;
import org.springframework.boot.actuate.autoconfigure.info.InfoContributorFallback;
import org.springframework.boot.actuate.info.Info.Builder;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/** Endpoint to print basic information about the certificates Conduit is using. */
@Component
@ConditionalOnEnabledInfoContributor(value = "certs", fallback = InfoContributorFallback.DISABLE)
public class CertInfoContributor implements InfoContributor {
  private static final Logger log = LoggerFactory.getLogger(CertInfoContributor.class);
  public static final String CERT_LOAD_ERR = "Could not load certificates";

  private final RhsmApiProperties rhsmApiProperties;

  public CertInfoContributor(RhsmApiProperties rhsmApiProperties) {
    this.rhsmApiProperties = rhsmApiProperties;
  }

  public Map<String, Map<String, String>> keystoreInfo() throws IllegalStateException {
    HttpClientProperties config = rhsmApiProperties;

    try {
      return CertInfoInquisitor.loadStoreInfo(config.getKeystore(), config.getKeystorePassword());
    } catch (IOException | GeneralSecurityException e) {
      log.warn(CERT_LOAD_ERR, e);
      throw new IllegalStateException(CERT_LOAD_ERR, e);
    }
  }

  public Map<String, Map<String, String>> truststoreInfo() throws IllegalStateException {
    HttpClientProperties config = rhsmApiProperties;

    try {
      return CertInfoInquisitor.loadStoreInfo(
          config.getTruststore(), config.getTruststorePassword());
    } catch (IOException | GeneralSecurityException e) {
      log.warn(CERT_LOAD_ERR, e);
      throw new IllegalStateException(CERT_LOAD_ERR, e);
    }
  }

  @Override
  public void contribute(Builder builder) {
    try {
      builder.withDetail("keystore", keystoreInfo());
    } catch (IllegalStateException e) {
      builder.withDetail("keystore", e.getMessage() + ":" + e.getCause().getMessage());
    }

    try {
      builder.withDetail("truststore", truststoreInfo());
    } catch (IllegalStateException e) {
      builder.withDetail("truststore", e.getMessage() + ":" + e.getCause().getMessage());
    }
  }
}
