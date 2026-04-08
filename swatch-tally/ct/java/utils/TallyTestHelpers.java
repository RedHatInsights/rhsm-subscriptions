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
package utils;

import static com.redhat.swatch.component.tests.utils.Topics.TALLY;

import api.MessageValidators;
import api.TallySwatchService;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;

/**
 * Helper methods for swatch-tally component tests.
 *
 * <p>Methods are organized into sections following the given-when-then pattern:
 *
 * <ul>
 *   <li>Given helpers - Create test data and setup preconditions
 *   <li>When/Then helpers - Perform actions and wait for results
 *   <li>Then helpers - Retrieve and verify test results
 * </ul>
 */
public class TallyTestHelpers {

  // Test configuration constants
  private static final String DEFAULT_BILLING_ACCOUNT =
      String.valueOf(100000000000L + (long) (Math.random() * 900000000000L));
  private static final String DEFAULT_PRODUCT_ID =
      TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String DEFAULT_PRODUCT_TAG =
      TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String DEFAULT_METRIC_ID =
      TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  private static final int EVENT_EXPIRATION_DAYS = 25;

  public TallyTestHelpers() {}

  // --- Given helper methods: Create test data and setup preconditions ---

  /**
   * Creates a PAYG event with default product configuration and specified timestamp.
   *
   * @param orgId the organization ID
   * @param instanceId the instance ID
   * @param timestampStr the timestamp as a string
   * @param eventIdStr the event ID as a string
   * @param value the metric value
   * @return configured Event object
   */
  public Event createPaygEventWithTimestamp(
      String orgId, String instanceId, String timestampStr, String eventIdStr, float value) {

    return createPaygEventWithTimestamp(
        orgId,
        instanceId,
        timestampStr,
        eventIdStr,
        DEFAULT_METRIC_ID,
        value,
        Event.Sla.PREMIUM,
        Event.Usage.PRODUCTION,
        Event.BillingProvider.AWS,
        DEFAULT_BILLING_ACCOUNT,
        Event.HardwareType.CLOUD,
        DEFAULT_PRODUCT_ID,
        DEFAULT_PRODUCT_TAG);
  }

  /**
   * Creates a PAYG event with specified metric, product configuration, and timestamp.
   *
   * @param orgId the organization ID
   * @param instanceId the instance ID
   * @param timestampStr the timestamp as a string
   * @param eventIdStr the event ID as a string
   * @param metricId the metric ID
   * @param value the metric value
   * @param productId the product ID
   * @param productTag the product tag
   * @return configured Event object
   */
  public Event createPaygEventWithTimestamp(
      String orgId,
      String instanceId,
      String timestampStr,
      String eventIdStr,
      String metricId,
      float value,
      String productId,
      String productTag) {

    return createPaygEventWithTimestamp(
        orgId,
        instanceId,
        timestampStr,
        eventIdStr,
        metricId,
        value,
        Event.Sla.PREMIUM,
        Event.Usage.PRODUCTION,
        Event.BillingProvider.AWS,
        DEFAULT_BILLING_ACCOUNT,
        Event.HardwareType.CLOUD,
        productId,
        productTag);
  }

  /**
   * Creates a PAYG event with specified metric, SLA, hardware type, and product configuration.
   *
   * @param orgId the organization ID
   * @param instanceId the instance ID
   * @param timestampStr the timestamp as a string
   * @param eventIdStr the event ID as a string
   * @param metricId the metric ID
   * @param value the metric value
   * @param sla the service level agreement
   * @param hardwareType the hardware type
   * @param productId the product ID
   * @param productTag the product tag
   * @return configured Event object
   */
  public Event createPaygEventWithTimestamp(
      String orgId,
      String instanceId,
      String timestampStr,
      String eventIdStr,
      String metricId,
      float value,
      Event.Sla sla,
      Event.HardwareType hardwareType,
      String productId,
      String productTag) {

    return createPaygEventWithTimestamp(
        orgId,
        instanceId,
        timestampStr,
        eventIdStr,
        metricId,
        value,
        sla,
        Event.Usage.PRODUCTION,
        Event.BillingProvider.AWS,
        DEFAULT_BILLING_ACCOUNT,
        hardwareType,
        productId,
        productTag);
  }

  /**
   * Creates a PAYG event with full configuration options.
   *
   * @param orgId the organization ID
   * @param instanceId the instance ID
   * @param timestampStr the timestamp as a string
   * @param eventIdStr the event ID as a string
   * @param metricId the metric ID
   * @param value the metric value
   * @param sla the service level agreement
   * @param usage the usage type
   * @param billingProvider the billing provider (null to derive from hardware type)
   * @param billingAccountId the billing account ID (null to use default)
   * @param hardwareType the hardware type
   * @param productId the product ID
   * @param productTag the product tag
   * @return configured Event object
   */
  public Event createPaygEventWithTimestamp(
      String orgId,
      String instanceId,
      String timestampStr,
      String eventIdStr,
      String metricId,
      float value,
      Event.Sla sla,
      Event.Usage usage,
      Event.BillingProvider billingProvider,
      String billingAccountId,
      Event.HardwareType hardwareType,
      String productId,
      String productTag) {

    OffsetDateTime timestamp = OffsetDateTime.parse(timestampStr);
    OffsetDateTime expiration = timestamp.plusDays(EVENT_EXPIRATION_DAYS);

    Event event = new Event();
    event.setEventId(UUID.fromString(eventIdStr));
    event.setOrgId(orgId);
    event.setInstanceId(instanceId);
    event.setDisplayName(Optional.of("Test Instance"));
    event.setTimestamp(timestamp);
    event.setRecordDate(timestamp);
    event.setExpiration(Optional.of(expiration));
    event.setEventSource("cost-management");
    event.setEventType("snapshot");

    event.setSla(sla);
    event.setUsage(usage);
    event.setHardwareType(hardwareType);

    event.setProductIds(List.of(productId));
    event.setProductTag(Set.of(productTag));

    event.setRole(Event.Role.RED_HAT_ENTERPRISE_LINUX_SERVER);
    event.setServiceType("RHEL System");

    if (hardwareType == Event.HardwareType.CLOUD) {
      event.setCloudProvider(Event.CloudProvider.AWS);
      event.setBillingProvider(
          billingProvider != null ? billingProvider : Event.BillingProvider.AWS);
    } else {
      event.setCloudProvider(null);
      event.setBillingProvider(
          billingProvider != null ? billingProvider : Event.BillingProvider.AWS);
    }

    event.setBillingAccountId(
        Optional.of(billingAccountId != null ? billingAccountId : DEFAULT_BILLING_ACCOUNT));

    var measurement = new Measurement();
    measurement.setValue((double) value);
    measurement.setMetricId(metricId);
    event.setMeasurements(List.of(measurement));

    return event;
  }

  /**
   * Seeds nightly tally host buckets for component tests.
   *
   * <p>Nightly tally expects host-buckets to already exist. In CT we seed them directly into the DB
   * (via {@link TallyDbHostSeeder}). This is intentionally isolated so it can be removed later.
   *
   * <p>Implementation detail: reconciliation deletes swatch-only HBI_HOST systems unless the host
   * is considered "metered" (currently mapped to PAYG eligibility), so we seed one PAYG bucket to
   * keep the host and one non-PAYG bucket that we assert on.
   *
   * @param orgId the organization ID
   * @param productId the product ID
   * @param inventoryId the inventory ID
   * @param service the tally service
   */
  public void seedNightlyTallyHostBuckets(
      String orgId, String productId, String inventoryId, TallySwatchService service) {
    service.createOptInConfig(orgId);

    var hostId = TallyDbHostSeeder.insertHbiHost(orgId, inventoryId);
    // Keep the host from being deleted (PAYG-eligible tag)
    TallyDbHostSeeder.insertBuckets(
        hostId,
        TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.productTag(),
        "Premium",
        "Production",
        4,
        2,
        "AWS");
    // Produce DAILY summary messages (non-PAYG tag)
    TallyDbHostSeeder.insertBuckets(hostId, productId, "Premium", "Production", 4, 2, "PHYSICAL");
  }

  // --- When/Then helper methods: Perform actions and wait for results ---

  /**
   * Polls for tally sync and waits for expected Kafka messages with custom retry configuration.
   *
   * <p>This method repeatedly attempts to sync tally and wait for the expected number of messages,
   * retrying on failure up to maxAttempts times with the specified poll interval.
   *
   * @param testOrgId the organization ID to sync
   * @param productId the product ID to match
   * @param metricId the metric ID to match
   * @param granularity the granularity to match
   * @param expectedMessageCount the expected number of messages
   * @param maxAttempts maximum number of retry attempts
   * @param pollInterval delay between retry attempts
   * @param awaitTimeout timeout for waiting for Kafka messages per attempt
   * @param service the tally service
   * @param kafkaBridge the Kafka bridge service
   * @return list of matching TallySummary messages
   * @throws RuntimeException if sync fails after all retry attempts
   */
  public List<TallySummary> pollForTallySyncAndMessages(
      String testOrgId,
      String productId,
      String metricId,
      Granularity granularity,
      int expectedMessageCount,
      int maxAttempts,
      Duration pollInterval,
      Duration awaitTimeout,
      TallySwatchService service,
      KafkaBridgeService kafkaBridge) {
    int attempts = 0;
    Exception lastException = null;

    AwaitilitySettings kafkaConsumerTimeout =
        AwaitilitySettings.using(Duration.ofMillis(500), awaitTimeout);

    while (attempts < maxAttempts) {
      attempts++;
      try {
        service.performHourlyTallyForOrg(testOrgId);

        return kafkaBridge.waitForKafkaMessage(
            TALLY,
            MessageValidators.tallySummaryMatches(testOrgId, productId, metricId, granularity),
            expectedMessageCount,
            kafkaConsumerTimeout);
      } catch (Exception e) {
        lastException = e;
        if (attempts < maxAttempts) {
          try {
            Thread.sleep(pollInterval.toMillis());
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Polling interrupted", ie);
          }
        }
      }
    }

    throw new RuntimeException(
        String.format("Failed to sync tally after %d attempts", maxAttempts), lastException);
  }

  /**
   * Polls for tally sync and waits for expected Kafka messages with default retry configuration.
   *
   * <p>Uses default values: 10 max attempts, 2 second poll interval, 3 second await timeout.
   *
   * @param testOrgId the organization ID to sync
   * @param productId the product ID to match
   * @param metricId the metric ID to match
   * @param granularity the granularity to match
   * @param expectedMessageCount the expected number of messages
   * @param service the tally service
   * @param kafkaBridge the Kafka bridge service
   * @return list of matching TallySummary messages
   */
  public List<TallySummary> pollForTallySyncAndMessages(
      String testOrgId,
      String productId,
      String metricId,
      Granularity granularity,
      int expectedMessageCount,
      TallySwatchService service,
      KafkaBridgeService kafkaBridge) {
    return pollForTallySyncAndMessages(
        testOrgId,
        productId,
        metricId,
        granularity,
        expectedMessageCount,
        10,
        Duration.ofSeconds(2),
        Duration.ofSeconds(3),
        service,
        kafkaBridge);
  }

  // --- Then helper methods: Retrieve and verify test results ---

  /** Extracts and sums tally measurement values by SLA value only. */
  public double getTallySummaryValueWithSla(
      List<TallySummary> tallySummaries,
      String orgId,
      String productId,
      String metricId,
      Granularity granularity,
      String sla) {
    return getTallySummaryValue(
        tallySummaries, orgId, productId, metricId, granularity, sla, null, null, null);
  }

  /** Extracts and sums tally measurement values by usage value only. */
  public double getTallySummaryValueWithUsage(
      List<TallySummary> tallySummaries,
      String orgId,
      String productId,
      String metricId,
      Granularity granularity,
      String usage) {
    return getTallySummaryValue(
        tallySummaries, orgId, productId, metricId, granularity, null, usage, null, null);
  }

  /** Extracts and sums tally measurement values by billing account ID value only. */
  public double getTallySummaryValueWithBillingAccountId(
      List<TallySummary> tallySummaries,
      String orgId,
      String productId,
      String metricId,
      Granularity granularity,
      String billingAccountId) {
    return getTallySummaryValue(
        tallySummaries,
        orgId,
        productId,
        metricId,
        granularity,
        null,
        null,
        billingAccountId,
        null);
  }

  /** Extracts and sums tally measurement values by billing provider value only. */
  public double getTallySummaryValueWithBillingProvider(
      List<TallySummary> tallySummaries,
      String orgId,
      String productId,
      String metricId,
      Granularity granularity,
      String billingProvider) {
    return getTallySummaryValue(
        tallySummaries, orgId, productId, metricId, granularity, null, null, null, billingProvider);
  }

  /**
   * Extracts and sums tally measurement values from TallySummary messages.
   *
   * <p>Grabs summaries by organization, product, metric, granularity, and optionally by SLA, usage,
   * billing account ID, and billing provider. Pass {@code null} for any optional attribute to skip
   * it.
   *
   * @param tallySummaries the list of tally summaries to search
   * @param orgId the organization ID to match
   * @param productId the product ID to match
   * @param metricId the metric ID to match
   * @param granularity the granularity to match
   * @param sla the SLA to match (null to include all SLAs)
   * @param usage the usage to match (null to include all usages)
   * @param billingAccountId the billing account ID to match (null to include all)
   * @param billingProvider the billing provider to match (null to include all)
   * @return the sum of all matching measurement values
   */
  public double getTallySummaryValue(
      List<TallySummary> tallySummaries,
      String orgId,
      String productId,
      String metricId,
      Granularity granularity,
      String sla,
      String usage,
      String billingAccountId,
      String billingProvider) {
    return tallySummaries.stream()
        .filter(summary -> orgId.equalsIgnoreCase(summary.getOrgId()))
        .flatMap(
            summary ->
                summary.getTallySnapshots() == null
                    ? Stream.empty()
                    : summary.getTallySnapshots().stream())
        .filter(snapshot -> productId.equalsIgnoreCase(snapshot.getProductId()))
        .filter(snapshot -> granularity.equals(snapshot.getGranularity()))
        .filter(
            snapshot -> {
              if (sla == null) {
                return true;
              }
              String snapshotSla = snapshot.getSla() == null ? "" : snapshot.getSla().toString();
              return snapshotSla.equalsIgnoreCase(sla);
            })
        .filter(
            snapshot -> {
              if (usage == null) {
                return true;
              }
              String snapshotUsage =
                  snapshot.getUsage() == null ? "" : snapshot.getUsage().toString();
              return snapshotUsage.equalsIgnoreCase(usage);
            })
        .filter(
            snapshot -> {
              if (billingAccountId == null) {
                return true;
              }
              String snapshotBillingAccountId =
                  snapshot.getBillingAccountId() == null ? "" : snapshot.getBillingAccountId();
              return snapshotBillingAccountId.equalsIgnoreCase(billingAccountId);
            })
        .filter(
            snapshot -> {
              if (billingProvider == null) {
                return true;
              }
              String snapshotBillingProvider =
                  snapshot.getBillingProvider() == null
                      ? ""
                      : snapshot.getBillingProvider().toString();
              return snapshotBillingProvider.equalsIgnoreCase(billingProvider);
            })
        .flatMap(
            snapshot ->
                snapshot.getTallyMeasurements() == null
                    ? Stream.empty()
                    : snapshot.getTallyMeasurements().stream())
        .filter(m -> metricId.equalsIgnoreCase(m.getMetricId()))
        .mapToDouble(m -> m.getValue() == null ? 0.0 : m.getValue())
        .sum();
  }
}
