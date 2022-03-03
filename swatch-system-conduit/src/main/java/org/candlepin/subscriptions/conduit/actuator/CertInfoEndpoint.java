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
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/** Endpoint to print basic information about the certificates Conduit is using. */
@Component
@Endpoint(id = "certs")
public class CertInfoEndpoint {

  private static final Logger log = LoggerFactory.getLogger(CertInfoEndpoint.class);
  public static final String CERT_LOAD_ERR = "Could not load certificates";

  private final RhsmApiProperties rhsmApiProperties;

  public CertInfoEndpoint(RhsmApiProperties rhsmApiProperties) {
    this.rhsmApiProperties = rhsmApiProperties;
  }

  @ReadOperation
  public Map<String, Map<String, String>> keystoreInfo() throws IllegalStateException {
    HttpClientProperties config = rhsmApiProperties;

    try {
      return CertInfoInquisitor.loadStoreInfo(
          config.getKeystoreStream(), config.getKeystorePassword());
    } catch (IOException | GeneralSecurityException e) {
      log.error(CERT_LOAD_ERR, e);
    }
    throw new IllegalStateException(CERT_LOAD_ERR);
  }

  @ReadOperation
  public Map<String, Map<String, String>> truststoreInfo() throws IllegalStateException {
    HttpClientProperties config = rhsmApiProperties;

    try {
      return CertInfoInquisitor.loadStoreInfo(
          config.getTruststoreStream(), config.getTruststorePassword());
    } catch (IOException | GeneralSecurityException e) {
      log.error(CERT_LOAD_ERR, e);
    }
    throw new IllegalStateException(CERT_LOAD_ERR);
  }
}
