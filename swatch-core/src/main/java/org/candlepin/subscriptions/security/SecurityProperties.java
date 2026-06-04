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
package org.candlepin.subscriptions.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rhsm-subscriptions.security")
public class SecurityProperties {
  private boolean devMode = false;

  /**
   * Whether to allow manual event edits.
   *
   * <p>For development/testing only.
   */
  private boolean manualEventEditingEnabled = false;

  /**
   * Whether resetting an account by account number is enabled. Resetting entails clearing tallies,
   * hosts, and events.
   */
  private boolean resetAccountEnabled = false;

  /**
   * Expected domain suffix for origin or referer headers.
   *
   * @see AntiCsrfFilter
   */
  private String antiCsrfDomainSuffix = ".redhat.com";

  /**
   * Expected port for origin or referer headers.
   *
   * @see AntiCsrfFilter
   */
  private int antiCsrfPort = 443;
}
