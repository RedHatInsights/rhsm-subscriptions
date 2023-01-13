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
package org.candlepin.subscriptions;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.jobs.JobProperties;
import org.candlepin.subscriptions.subscription.SubscriptionServiceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * POJO to hold property values via Spring's "Type-Safe Configuration Properties" pattern
 *
 * <p>NOTE: not annotated with @Component, as this doesn't live in a package that is picked up with
 * package scanning.
 *
 * @see ApplicationConfiguration
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "rhsm-subscriptions")
public class ApplicationProperties {
  private String version;

  private boolean prettyPrintJson = false;

  /** Job schedules when running in dev mode. */
  private JobProperties jobs;

  /** Resource location of a file containing a list of accounts to process. */
  private String accountListResourceLocation;

  /**
   * Resource location of a file containing a list of products (SKUs) to process. If not specified,
   * all products will be processed.
   */
  private String productAllowlistResourceLocation;

  /** Resource location of a file containing the allowlisted accounts allowed to run reports. */
  private String reportingAccountAllowlistResourceLocation;

  /**
   * An hour based threshold used to determine whether an inventory host record's rhsm facts are
   * outdated. The host's rhsm.SYNC_TIMESTAMP fact is checked against this threshold. The default is
   * 24 hours.
   */
  private int hostLastSyncThresholdHours = 24;

  /**
   * The batch size of account numbers that will be processed at a time while producing snapshots.
   * Default: 500
   */
  private int accountBatchSize = 500;

  /** Amount of time to cache the account list, before allowing a re-read from the filesystem. */
  private Duration accountListCacheTtl = Duration.ofMinutes(5);

  /**
   * Amount of time to cache the product allowlist, before allowing a re-read from the filesystem.
   */
  private Duration productAllowListCacheTtl = Duration.ofMinutes(5);

  /**
   * Amount of time to cache the API access allowlist, before allowing a re-read from the
   * filesystem.
   */
  private Duration reportingAccountAllowlistCacheTtl = Duration.ofMinutes(5);

  /**
   * The number of days after the inventory's stale_timestamp that the record will be culled.
   * Currently HBI is calculating this value and setting it on messages. Right now the default is:
   * stale_timestamp + 14 days. Adding this as a configuration setting since we may need to adjust
   * it at some point to match.
   */
  private int cullingOffsetDays = 14;

  /** Enable or disable cloudigrade integration. */
  private boolean cloudigradeEnabled = false;

  /** Number of times to attempt query against cloudigrade for Tally integration. */
  private int cloudigradeMaxAttempts = 2;

  /**
   * Offsets the range to look at metrics to account for delay in prometheus having metrics
   * available
   */
  private Duration prometheusLatencyDuration = Duration.ofHours(0L);

  /**
   * Amount of time from current timestamp to start looking for metrics during a tally, independent
   * of the prometheus latency duration
   */
  private Duration metricLookupRangeDuration = Duration.ofHours(1L);

  /**
   * Latency offset: how far back to set the hourly tally window.
   *
   * <p>The offset is subtracted from the beginning and ending times of the latency window, to delay
   * the entire processing window. This ensures more metering tasks finish and report their totals
   * before tallying begins.
   */
  private Duration hourlyTallyOffset = Duration.ofMinutes(60L);

  /** Additional properties related to the Subscription Service */
  private SubscriptionServiceProperties subscription = new SubscriptionServiceProperties();

  /** If enabled, will sync Subscriptions with the upstream subscription service. */
  private boolean subscriptionSyncEnabled = false;

  /** If enabled, will allow synchronous operations when requested. */
  private boolean enableSynchronousOperations = false;

  /** Sets a hard limit on the size of accounts that HBI-based tally will attempt to process. */
  private int tallyMaxHbiAccountSize;
}
