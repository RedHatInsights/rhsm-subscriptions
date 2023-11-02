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
package com.redhat.swatch.metrics.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Properties related to all metrics that are to be gathered from the prometheus service. */
@ConfigMapping(
    prefix = "rhsm-subscriptions.metering.prometheus.metric",
    namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface MetricProperties {

  /** How long to wait for results from the query. */
  @WithDefault("10000")
  int queryTimeout();

  /**
   * Defines the amount of time (in minutes) that will be used to calculate a metric query's start
   * date based on a given end date.
   *
   * <p>For example, given an end date of 2021-01-06T00:00:00Z and a rangeInMinutes of 60, the
   * calculated start date will be: 2021-01-05T23:00:00Z.
   */
  @WithDefault("60")
  int rangeInMinutes();

  /**
   * The amount of time for each openshift metric data point for the time range specified in the
   * query. This value should be specified in seconds.
   */
  @WithDefault("3600") // 1 hour
  int step();

  /** Number of times the metrics gathering should be retried if something fails. */
  @WithDefault("5")
  int maxAttempts();

  /** The maximum sleep interval between retries when retrying metrics gathering. */
  @WithDefault("30000")
  long backOffMaxInterval();

  /** The initial sleep interval between retries when retrying metrics gathering. */
  @WithDefault("2000")
  long backOffInitialInterval();

  /**
   * The multiplier to use to generate the next backoff interval when retrying metrics gathering.
   */
  @WithDefault("2")
  double backOffMultiplier();

  Map<String, String> queryTemplates();

  Map<String, String> accountQueryTemplates();

  /**
   * SPEL templates do not support nested expressions so the QueryBuilder will apply template
   * parameters a set number of times to prevent recursion.
   */
  @WithDefault("3")
  int templateParameterDepth();

  /** How many attempts before giving up on the MeteringJob. */
  Integer jobMaxAttempts();

  /** Retry backoff initial interval of the MeteringJob. */
  Duration jobBackOffInitialInterval();

  /** Retry backoff interval of the MeteringJob. */
  Duration jobBackOffMaxInterval();

  /** The event source type. */
  String eventSource();

  default Optional<String> getQueryTemplate(String templateKey) {
    return queryTemplates().containsKey(templateKey)
        ? Optional.of(queryTemplates().get(templateKey))
        : Optional.empty();
  }

  default Optional<String> getAccountQueryTemplate(String templateKey) {
    return accountQueryTemplates().containsKey(templateKey)
        ? Optional.of(accountQueryTemplates().get(templateKey))
        : Optional.empty();
  }
}
