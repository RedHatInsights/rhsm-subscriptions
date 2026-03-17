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
package tests;

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG_ADDON;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.BillingProvider;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.candlepin.subscriptions.json.Event.Sla;
import org.candlepin.subscriptions.json.Event.Usage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TallySummaryMessagesByAttributesTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG_ADDON.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG_ADDON.productId();

  private static final List<Sla> slas = List.of(Sla.PREMIUM, Sla.STANDARD, Sla.SELF_SUPPORT);
  private static final List<Usage> usages =
      List.of(Usage.PRODUCTION, Usage.DEVELOPMENT_TEST, Usage.DISASTER_RECOVERY);
  private static final List<String> billingAccountIds = List.of("839214756108", "472061583927");
  private static final List<BillingProvider> billingProviders =
      List.of(BillingProvider.AWS, BillingProvider.AZURE);

  // ---- SLA tests ----

  @Test
  @TestPlanName("tally-summary-by-attributes-TC001")
  public void testTallySummarySeparatesMeasurementsBySla() {
    // Given: Events for each SLA type and one event with no SLA
    OffsetDateTime now = OffsetDateTime.now();
    for (Sla sla : slas) {
      publishSlaEvent(now, sla);
    }
    publishSlaEvent(now, Sla.__EMPTY__);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 4, service, kafkaBridge);

    // Then: Each SLA should have its own tally value and their sum should equal total
    double slaValues =
        slas.stream().mapToDouble(sla -> slaValue(tallySummaries, sla.toString())).sum();
    double noSla = slaValue(tallySummaries, Sla.__EMPTY__.toString());
    double allTallySummaries = slaValue(tallySummaries, null);

    Assertions.assertAll(
        () ->
            Assertions.assertEquals(
                allTallySummaries - slaValues,
                noSla,
                0.0001,
                "No-SLA value should equal total minus SLA values"),
        () ->
            Assertions.assertEquals(
                slaValues + noSla,
                allTallySummaries,
                0.0001,
                "SLA values plus no-SLA should equal total"));
  }

  // ---- Usage tests ----

  @Test
  @TestPlanName("tally-summary-by-attributes-TC002")
  public void testTallySummarySeparatesMeasurementsByUsage() {
    // Given: Events for each Usage type and one event with no Usage
    OffsetDateTime now = OffsetDateTime.now();
    for (Usage usage : usages) {
      publishUsageEvent(now, usage);
    }
    publishUsageEvent(now, Usage.__EMPTY__);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 4, service, kafkaBridge);

    // Then: Each Usage should have its own tally value and their sum should equal total
    double usageValues =
        usages.stream().mapToDouble(usage -> usageValue(tallySummaries, usage.toString())).sum();
    double noUsage = usageValue(tallySummaries, Usage.__EMPTY__.toString());
    double allTallySummaries = usageValue(tallySummaries, null);

    Assertions.assertAll(
        () ->
            Assertions.assertEquals(
                allTallySummaries - usageValues,
                noUsage,
                0.0001,
                "No-Usage value should equal total minus Usage values"),
        () ->
            Assertions.assertEquals(
                usageValues + noUsage,
                allTallySummaries,
                0.0001,
                "Usage values plus no-Usage should equal total"));
  }

  // ---- Billing account ID tests ----

  @Test
  @TestPlanName("tally-summary-by-attributes-TC003")
  public void testTallySummarySeparatesMeasurementsByBillingAccountId() {
    // Given: Events for two different billing account IDs
    OffsetDateTime now = OffsetDateTime.now();
    publishBillingAccountEvent(now, billingAccountIds.get(0), 10.0f);
    publishBillingAccountEvent(now, billingAccountIds.get(1), 20.0f);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Each billing account should have its own tally value and their sum should equal total
    double account1Value = billingAccountValue(tallySummaries, billingAccountIds.get(0));
    double account2Value = billingAccountValue(tallySummaries, billingAccountIds.get(1));
    double total = billingAccountValue(tallySummaries, null);

    Assertions.assertAll(
        () ->
            Assertions.assertEquals(
                10.0, account1Value, 0.0001, "Billing account 1 should have value 10.0"),
        () ->
            Assertions.assertEquals(
                20.0, account2Value, 0.0001, "Billing account 2 should have value 20.0"),
        () ->
            Assertions.assertEquals(
                account1Value + account2Value,
                total,
                0.0001,
                "Sum of per-billing-account values should equal total"));
  }

  @Test
  @TestPlanName("tally-summary-by-attributes-TC004")
  public void testTallySummaryMeasurementsWhenSingleHostChangesBillingAccountId() {
    // Given: Same instance sends events under two different billing account IDs
    OffsetDateTime now = OffsetDateTime.now();
    String instanceId = UUID.randomUUID().toString();
    publishBillingAccountEventForInstance(
        instanceId, now.minusHours(2), billingAccountIds.get(0), 5.0f);
    publishBillingAccountEventForInstance(
        instanceId, now.minusHours(1), billingAccountIds.get(1), 8.0f);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Each billing account should have the value from its respective event
    double account1Value = billingAccountValue(tallySummaries, billingAccountIds.get(0));
    double account2Value = billingAccountValue(tallySummaries, billingAccountIds.get(1));
    double total = billingAccountValue(tallySummaries, null);

    Assertions.assertAll(
        () ->
            Assertions.assertEquals(
                5.0,
                account1Value,
                0.0001,
                "Original billing account should have value from first event"),
        () ->
            Assertions.assertEquals(
                8.0,
                account2Value,
                0.0001,
                "New billing account should have value from second event"),
        () ->
            Assertions.assertEquals(
                account1Value + account2Value,
                total,
                0.0001,
                "Sum of per-billing-account values should equal total"));
  }

  // ---- Billing provider tests ----

  @Test
  @TestPlanName("tally-summary-by-attributes-TC005")
  public void testTallySummarySeparatesMeasurementsByBillingProvider() {
    // Given: Events for two different billing providers
    OffsetDateTime now = OffsetDateTime.now();
    publishBillingProviderEvent(now, billingProviders.get(0), 10.0f);
    publishBillingProviderEvent(now, billingProviders.get(1), 20.0f);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Each billing provider should have its own tally value and their sum should equal total
    double awsValue = billingProviderValue(tallySummaries, billingProviders.get(0));
    double azureValue = billingProviderValue(tallySummaries, billingProviders.get(1));
    double total = billingProviderValue(tallySummaries, null);

    Assertions.assertAll(
        () ->
            Assertions.assertEquals(
                10.0, awsValue, 0.0001, "Billing Provider AWS should have value 10.0"),
        () ->
            Assertions.assertEquals(
                20.0, azureValue, 0.0001, "Billing Provider Azure should have value 20.0"),
        () ->
            Assertions.assertEquals(
                awsValue + azureValue,
                total,
                0.0001,
                "Sum of per-billing-provider values should equal total"));
  }

  // ---- SLA helpers ----

  private void publishSlaEvent(OffsetDateTime now, Sla sla) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            now.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            3.0f,
            sla,
            null,
            null,
            null,
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double slaValue(List<TallySummary> summaries, String sla) {
    return helpers.getTallySummaryValueWithSla(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, sla);
  }

  // ---- Usage helpers ----

  private void publishUsageEvent(OffsetDateTime now, Usage usage) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            now.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            3.0f,
            null,
            usage,
            null,
            null,
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double usageValue(List<TallySummary> summaries, String usage) {
    return helpers.getTallySummaryValueWithUsage(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, usage);
  }

  // ---- Billing account ID helpers ----

  private void publishBillingAccountEvent(
      OffsetDateTime now, String billingAccountId, float value) {
    publishBillingAccountEventForInstance(
        UUID.randomUUID().toString(), now.minusHours(1), billingAccountId, value);
  }

  private void publishBillingAccountEventForInstance(
      String instanceId, OffsetDateTime timestamp, String billingAccountId, float value) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value,
            null,
            null,
            null,
            billingAccountId,
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double billingAccountValue(List<TallySummary> summaries, String billingAccountId) {
    return helpers.getTallySummaryValueWithBillingAccountId(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, billingAccountId);
  }

  // ---- Billing provider helpers ----

  private void publishBillingProviderEvent(
      OffsetDateTime now, BillingProvider billingProvider, float value) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            UUID.randomUUID().toString(),
            now.minusHours(1).toString(),
            UUID.randomUUID().toString(),
            TEST_METRIC_ID,
            value,
            null,
            null,
            billingProvider,
            null,
            HardwareType.CLOUD,
            TEST_PRODUCT_ID,
            TEST_PRODUCT_TAG);
    if (billingProvider == BillingProvider.AZURE) {
      event.setCloudProvider(Event.CloudProvider.AZURE);
    }
    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private double billingProviderValue(
      List<TallySummary> summaries, BillingProvider billingProvider) {
    return helpers.getTallySummaryValueWithBillingProvider(
        summaries,
        orgId,
        TEST_PRODUCT_TAG,
        TEST_METRIC_ID,
        Granularity.HOURLY,
        billingProvider != null ? billingProvider.toString() : null);
  }
}
