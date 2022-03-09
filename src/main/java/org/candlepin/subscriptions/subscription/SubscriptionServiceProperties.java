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
package org.candlepin.subscriptions.subscription;

import java.time.Duration;
import java.time.Period;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.candlepin.subscriptions.http.HttpClientProperties;

/** Additional properties related to the Subscription Service */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SubscriptionServiceProperties extends HttpClientProperties {

  /**
   * Number of times we should try requesting info from the Subscription Service if something fails.
   */
  private int maxRetryAttempts = 4;

  /** Page size for subscription queries */
  private int pageSize = 1000;

  /** Do not sync any subs that have expired longer than this much in the past from now. */
  private Period ignoreExpiredOlderThan = Period.ofMonths(2);

  /** Do not sync any subs starting later than this much in the future from now. */
  private Period ignoreStartingLaterThan = Period.ofMonths(2);

  /**
   * The initial sleep interval between retries when retrying fetching info from the Subscription
   * Service
   */
  private Duration backOffInitialInterval = Duration.ofSeconds(1L);
}
