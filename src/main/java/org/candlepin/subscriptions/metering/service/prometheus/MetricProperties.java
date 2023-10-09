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
package org.candlepin.subscriptions.metering.service.prometheus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Properties related to all metrics that are to be gathered from the prometheus service. */
@Getter
@Setter
@ConfigurationProperties(prefix = "rhsm-subscriptions.metering.prometheus.metric")
public class MetricProperties {

  /** How long to wait for results from the query. */
  private int queryTimeout = 10000;

  /**
   * Defines the amount of time (in minutes) that will be used to calculate a metric query's start
   * date based on a given end date.
   *
   * <p>For example, given an end date of 2021-01-06T00:00:00Z and a rangeInMinutes of 60, the
   * calculated start date will be: 2021-01-05T23:00:00Z.
   */
  private int rangeInMinutes = 60;

  /**
   * The amount of time for each openshift metric data point for the time range specified in the
   * query. This value should be specified in seconds.
   */
  private int step = 3600; // 1 hour

  /** Number of times the metrics gathering should be retried if something fails. */
  private int maxAttempts = 5;

  /** The maximum sleep interval between retries when retrying metrics gathering. */
  private long backOffMaxInterval = 30000L;

  /** The initial sleep interval between retries when retrying metrics gathering. */
  private long backOffInitialInterval = 2000L;

  /**
   * The multiplier to use to generate the next backoff interval when retrying metrics gathering.
   */
  private double backOffMultiplier = 2;

  private Map<String, String> queryTemplates = new HashMap<>();

  private Map<String, String> accountQueryTemplates = new HashMap<>();

  /**
   * SPEL templates do not support nested expressions so the QueryBuilder will apply template
   * parameters a set number of times to prevent recursion.
   */
  private int templateParameterDepth = 3;

  /** How many attempts before giving up on the MeteringJob. */
  private Integer jobMaxAttempts;

  /** Retry backoff initial interval of the MeteringJob. */
  private Duration jobBackOffInitialInterval;

  /** Retry backoff interval of the MeteringJob. */
  private Duration jobBackOffMaxInterval;

  /** The event source type. */
  private String eventSource;

  public Optional<String> getQueryTemplate(String templateKey) {
    return queryTemplates.containsKey(templateKey)
        ? Optional.of(queryTemplates.get(templateKey))
        : Optional.empty();
  }

  public Optional<String> getAccountQueryTemplate(String templateKey) {
    return accountQueryTemplates.containsKey(templateKey)
        ? Optional.of(accountQueryTemplates.get(templateKey))
        : Optional.empty();
  }
}
