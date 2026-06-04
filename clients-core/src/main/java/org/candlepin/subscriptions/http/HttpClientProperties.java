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
package org.candlepin.subscriptions.http;

import java.time.Duration;
import javax.net.ssl.HostnameVerifier;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.candlepin.subscriptions.util.TlsProperties;

/** HTTP service client configuration. */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class HttpClientProperties extends TlsProperties {

  /** Use a stub of the service. */
  private boolean useStub;

  /** The URL of the service. */
  private String url;

  /**
   * The auth token required to connect. This should be passed as part of the Authentication header.
   *
   * <p>"Authentication: bearer &lt;token&gt;"
   */
  @ToString.Exclude private String token;

  /** Maximum number of simultaneous connections to the service. */
  private int maxConnections = 100;

  /** Max time to keep a connection open */
  private Duration connectionTtl = Duration.ofMinutes(5);

  /**
   * -- SETTER -- Allow setting the HostnameVerifier implementation. NoopHostnameVerifier could be
   * used in testing, for example
   */
  @ToString.Exclude private HostnameVerifier hostnameVerifier = new DefaultHostnameVerifier();

  /** PSK used to access the API. */
  @ToString.Exclude private String psk;
}
