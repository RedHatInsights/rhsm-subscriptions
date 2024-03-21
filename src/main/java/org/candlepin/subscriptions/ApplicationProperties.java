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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;
import org.candlepin.subscriptions.jobs.JobProperties;
import org.candlepin.subscriptions.subscription.SubscriptionServiceProperties;
import org.candlepin.subscriptions.tally.admin.InternalTallyResource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

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
   * Resource location of a file containing a list of products (SKUs) not to process. If not
   * specified, all products will be processed.
   */
  private String productDenylistResourceLocation;

  /** Resource location of a file containing the allowlisted accounts allowed to run reports. */
  private String reportingAccountAllowlistResourceLocation;

  /**
   * An hour based threshold used to determine whether an inventory host record's rhsm facts are
   * outdated. The host's rhsm.SYNC_TIMESTAMP fact is checked against this threshold. The default is
   * 24 hours.
   */
  private Duration hostLastSyncThreshold = Duration.ofHours(24);

  /**
   * The batch size of account numbers that will be processed at a time while producing snapshots.
   * Default: 500
   */
  private int accountBatchSize = 500;

  /** Amount of time to cache the account list, before allowing a re-read from the filesystem. */
  private Duration accountListCacheTtl = Duration.ofMinutes(5);

  /**
   * Amount of time to cache the product denylist, before allowing a re-read from the filesystem.
   */
  private Duration productDenyListCacheTtl = Duration.ofMinutes(5);

  /**
   * Amount of time to cache the API access allowlist, before allowing a re-read from the
   * filesystem.
   */
  private Duration reportingAccountAllowlistCacheTtl = Duration.ofMinutes(5);

  /**
   * The number of days after the inventory's stale_timestamp that the record will be culled.
   * Currently, HBI is calculating this value and setting it on messages. Right now the default is:
   * stale_timestamp + 14 days. Adding this as a configuration setting since we may need to adjust
   * it at some point to match.
   */
  private int cullingOffsetDays = 14;

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

  /**
   * Interval for system update flush when for reconciliation of HBI data w/ swatch system data.
   *
   * <p>Lower values will cause more frequent flushing, and keep memory usage low, while higher
   * values flush less often, but consume more memory.
   */
  private Long hbiReconciliationFlushInterval;

  /**
   * Since the two parameters sent to {@link InternalTallyResource#performHourlyTallyForOrg(String,
   * OffsetDateTime, OffsetDateTime, Boolean)} are actually ISO 8601 timestamps we are using a
   * Duration rather than a Period since Duration captures time and not just dates. However, the
   * default ChronoUnit we're using is days since that's what the range is meant to be on the order
   * of. If the value is specified in hours (which Spring will allow: e.g. 2160h) the behavior will
   * be strict: e.g. Daylight Saving Time will not affect it.
   *
   * <p>From the docs: "Durations and periods differ in their treatment of daylight savings time
   * when added to ZonedDateTime. A Duration will add an exact number of seconds, thus a duration of
   * one day is always exactly 24 hours. By contrast, a Period will add a conceptual day, trying to
   * maintain the local time."
   */
  @DurationUnit(ChronoUnit.DAYS)
  private Duration hourlyTallyDurationLimitDays;

  /**
   * This property enables that all the products use the new formula to calculate the number of
   * virtual CPUs (vCPUs). If disabled, the new formula will only be used for the OpenShift
   * Container products. See more in {@link
   * org.candlepin.subscriptions.tally.facts.FactNormalizer#normalize}. The formula to calculate the
   * number of virtual CPUs (vCPUs) is based on the number of threads per core which previously was
   * hard-coded to 2.0. After <a href="https://issues.redhat.com/browse/SWATCH-80">SWATCH-80</a>,
   * the number of threads per core is given from a new system profile fact. If absent, then we can
   * also calculate it from another new system profile fact which is the number of CPUs. However, we
   * are not sure if the new system profile facts are only valid for the OpenShift Container
   * products. Therefore, we see an extreme risk of applying the new system facts to all products,
   * so if we detect this is not the desired behaviour, we can disable this property to only apply
   * the new system profile facts to the OpenShift Container products.
   */
  private boolean useCpuSystemFactsToAllProducts = true;

  /**
   * Defines the number of Events to process in a batch during the hourly tally. Since a Host update
   * can require many DB inserts (host,buckets,measurements,monthly totals), increasing the batch
   * size too high has a direct negative impact hourly tally performance due to the increase of
   * resulting DB insert/update statements.
   */
  private int hourlyTallyEventBatchSize;
}
