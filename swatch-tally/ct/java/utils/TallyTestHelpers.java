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

public class TallyTestHelpers {

  // Test configuration constants
  private static final String DEFAULT_BILLING_ACCOUNT = "746157280291";
  private static final String DEFAULT_PRODUCT_ID =
      TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.productId();
  private static final String DEFAULT_PRODUCT_TAG =
      TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String DEFAULT_METRIC_ID =
      TallyTestProducts.RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  private static final int EVENT_EXPIRATION_DAYS = 25;

  /** Default constructor. */
  public TallyTestHelpers() {}

  public Event createEventWithTimestamp(
      String orgId, String instanceId, String timestampStr, String eventIdStr, float value) {

    return createEventWithTimestamp(
        orgId,
        instanceId,
        timestampStr,
        eventIdStr,
        DEFAULT_METRIC_ID,
        value,
        Event.Sla.PREMIUM,
        Event.HardwareType.CLOUD,
        DEFAULT_PRODUCT_ID,
        DEFAULT_PRODUCT_TAG);
  }

  public Event createEventWithTimestamp(
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
    event.setHardwareType(hardwareType);

    // Product id and tag from parameters
    event.setProductIds(List.of(productId));
    event.setProductTag(Set.of(productTag));

    event.setRole(Event.Role.RED_HAT_ENTERPRISE_LINUX_SERVER);
    event.setUsage(Event.Usage.PRODUCTION);
    event.setServiceType("RHEL System");

    // Only set cloud/billing when hardware type is CLOUD
    if (hardwareType == Event.HardwareType.CLOUD) {
      event.setCloudProvider(Event.CloudProvider.AWS);
      event.setBillingProvider(Event.BillingProvider.AWS);
    } else {
      event.setCloudProvider(null);
      event.setBillingProvider(null);
    }

    event.setBillingAccountId(Optional.of(DEFAULT_BILLING_ACCOUNT));

    var measurement = new Measurement();
    measurement.setValue((double) value);
    measurement.setMetricId(metricId);
    event.setMeasurements(List.of(measurement));

    return event;
  }

  /**
   * Temporary nightly-tally pre-req seeding for CTs.
   *
   * <p>Nightly tally expects host-buckets to already exist. In CT we seed them directly into the DB
   * (via {@link TallyDbHostSeeder}). This is intentionally isolated so it can be removed later.
   *
   * <p>Implementation detail: reconciliation deletes swatch-only HBI_HOST systems unless the host
   * is considered "metered" (currently mapped to PAYG eligibility), so we seed one PAYG bucket to
   * keep the host and one non-PAYG bucket that we assert on.
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
        // Run hourly tally sync
        service.performHourlyTallyForOrg(testOrgId);

        // Wait for tally messages with a short timeout

        // If successful, return
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
        10, // max attempts
        Duration.ofSeconds(2), // delay between attempts
        Duration.ofSeconds(3), // await timeout per attempt
        service,
        kafkaBridge);
  }

  public double getTallySummaryValue(
      List<TallySummary> tallySummaries,
      String orgId,
      String productId,
      String metricId,
      Granularity granularity,
      String sla) {
    return tallySummaries.stream()
        // Only consider summaries for the requested org
        .filter(summary -> orgId.equalsIgnoreCase(summary.getOrgId()))
        // Flatten to snapshots
        .flatMap(
            summary ->
                summary.getTallySnapshots() == null
                    ? Stream.empty()
                    : summary.getTallySnapshots().stream())
        // Match product and granularity
        .filter(snapshot -> productId.equalsIgnoreCase(snapshot.getProductId()))
        .filter(snapshot -> granularity.equals(snapshot.getGranularity()))
        // Optional SLA filter:
        //  - if sla == null  -> include all SLAs
        //  - if sla != null -> only include snapshots whose SLA string matches,
        //                      including the empty-string SLA bucket.
        .filter(
            snapshot -> {
              if (sla == null) {
                return true;
              }
              String snapshotSla = snapshot.getSla() == null ? "" : snapshot.getSla().toString();
              return snapshotSla.equalsIgnoreCase(sla);
            })
        // Flatten to measurements
        .flatMap(
            snapshot ->
                snapshot.getTallyMeasurements() == null
                    ? Stream.empty()
                    : snapshot.getTallyMeasurements().stream())
        // Match the desired metric
        .filter(m -> metricId.equalsIgnoreCase(m.getMetricId()))
        // Sum all matching values
        .mapToDouble(m -> m.getValue() == null ? 0.0 : m.getValue())
        .sum();
  }
}
