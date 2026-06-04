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
package org.candlepin.subscriptions.util;

import lombok.Data;
import lombok.ToString;
import org.springframework.core.io.Resource;

@Data
public class TlsProperties {

  /** Certificate authenticate file path */
  private Resource keystore;

  /** Certificate authenticate file password */
  @ToString.Exclude private char[] keystorePassword;

  /** Truststore file path */
  private Resource truststore;

  /** Truststore file password */
  @ToString.Exclude private char[] truststorePassword;

  /** Truststore type */
  @ToString.Exclude private String truststoreType;

  public boolean usesClientAuth() {
    return validFile(keystore);
  }

  /**
   * If no truststore is provided, the security framework should use the truststore built into the
   * JRE.
   *
   * @return true if a custom truststore should be used
   */
  public boolean providesTruststore() {
    return validFile(truststore);
  }

  private boolean validFile(Resource r) {
    return r != null && r.exists() && r.isReadable() && r.isFile();
  }
}
