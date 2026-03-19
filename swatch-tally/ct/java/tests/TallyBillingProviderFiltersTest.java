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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TallyBillingProviderFiltersTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG_ADDON.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG_ADDON.productId();
  private static final List<BillingProvider> billingProviders =
      List.of(BillingProvider.AWS, BillingProvider.AZURE);

  @Test
  @TestPlanName("tally-billing-provider-filters-TC001")
  public void testTallyBillingProviderFiltersCount() {
    // Given: Events for two different billing account IDs
    OffsetDateTime now = OffsetDateTime.now();
    // First billing provider
    publishEvent(now, billingProviders.get(0), 10.0f);
    // Second billing provider
    publishEvent(now, billingProviders.get(1), 20.0f);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Each billing account should have its own tally value and their sum should equal total
    double account1Value = tallyValue(tallySummaries, billingProviders.get(0));
    double account2Value = tallyValue(tallySummaries, billingProviders.get(1));
    double total = tallyValue(tallySummaries, null);

    Assertions.assertAll(
        () ->
            Assertions.assertEquals(
                10.0, account1Value, 0.0001, "Billing Provider AWS should have value 10.0"),
        () ->
            Assertions.assertEquals(
                20.0, account2Value, 0.0001, "Billing Provider Azure should have value 20.0"),
        () ->
            Assertions.assertEquals(
                account1Value + account2Value,
                total,
                0.0001,
                "Sum of per-billing-provider values should equal total"));
  }

  private void publishEvent(OffsetDateTime now, BillingProvider billingProvider, float value) {
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

  private double tallyValue(List<TallySummary> summaries, BillingProvider billingProvider) {
    return helpers.getTallySummaryValueWithBillingProviderFilter(
        summaries,
        orgId,
        TEST_PRODUCT_TAG,
        TEST_METRIC_ID,
        Granularity.HOURLY,
        billingProvider != null ? billingProvider.toString() : null);
  }
}
