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
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Event.HardwareType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TallyBillingAccountIdFiltersTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86_ELS_PAYG.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86_ELS_PAYG.metricIds().get(1);
  private static final String TEST_PRODUCT_ID = RHEL_FOR_X86_ELS_PAYG.productId();
  private static final List<String> billingAccountIds = List.of("839214756108", "472061583927");

  @Test
  @TestPlanName("tally-billing-account-id-filters-TC001")
  public void testTallyBillingAccountIdFiltersCount() {
    // Given: Events for two different billing account IDs
    OffsetDateTime now = OffsetDateTime.now();
    // First billing account ID
    publishEvent(now, billingAccountIds.get(0), 10.0f);
    // Second billing account ID
    publishEvent(now, billingAccountIds.get(1), 20.0f);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Each billing account should have its own tally value and their sum should equal total
    double account1Value = tallyValue(tallySummaries, billingAccountIds.get(0));
    double account2Value = tallyValue(tallySummaries, billingAccountIds.get(1));
    double total = tallyValue(tallySummaries, null);

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
  @TestPlanName("tally-billing-account-id-filters-TC002")
  public void testTallyReportValuesForDifferentBillingAccountIds() {
    // Given: Events for two different billing account IDs
    service.createOptInConfig(orgId);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
    publishEvent(now, billingAccountIds.get(0), 15.0f);
    publishEvent(now, billingAccountIds.get(1), 7.0f);

    OffsetDateTime beginning = now.minusHours(2);
    OffsetDateTime ending = now.plusHours(1);

    // When/Then: Tally report for each billing account should show only its own value
    AwaitilitySettings settings =
        AwaitilitySettings.using(Duration.ofSeconds(1), Duration.ofSeconds(30))
            .withService(service)
            .timeoutMessage(
                "Timed out waiting for tally reports to show expected billing account values");

    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);

          double account1Report = getHourlyReportSum(beginning, ending, billingAccountIds.get(0));
          double account2Report = getHourlyReportSum(beginning, ending, billingAccountIds.get(1));
          double unfilteredReport = getHourlyReportSum(beginning, ending);

          Assertions.assertAll(
              () ->
                  Assertions.assertEquals(
                      15.0,
                      account1Report,
                      0.0001,
                      "Tally report for billing account 1 should show value 15.0"),
              () ->
                  Assertions.assertEquals(
                      7.0,
                      account2Report,
                      0.0001,
                      "Tally report for billing account 2 should show value 7.0"),
              () ->
                  Assertions.assertEquals(
                      account1Report + account2Report,
                      unfilteredReport,
                      0.0001,
                      "Unfiltered tally report should equal sum of per-billing-account values"));
        },
        settings);
  }

  @Test
  @TestPlanName("tally-billing-account-id-filters-TC003")
  public void testTallySnapshotsSeparateValuesWhenSingleHostChangesBillingAccountId() {
    // Given: Same instance sends events under two different billing account IDs
    OffsetDateTime now = OffsetDateTime.now();
    String instanceId = UUID.randomUUID().toString();
    publishEventForInstance(instanceId, now.minusHours(2), billingAccountIds.get(0), 5.0f);
    publishEventForInstance(instanceId, now.minusHours(1), billingAccountIds.get(1), 8.0f);

    // When: Polling for tally summaries
    List<TallySummary> tallySummaries =
        helpers.pollForTallySyncAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, 2, service, kafkaBridge);

    // Then: Each billing account should have the value from its respective event
    double account1Value = tallyValue(tallySummaries, billingAccountIds.get(0));
    double account2Value = tallyValue(tallySummaries, billingAccountIds.get(1));
    double total = tallyValue(tallySummaries, null);

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

  private double getHourlyReportSum(OffsetDateTime beginning, OffsetDateTime ending) {
    return getHourlyReportSum(beginning, ending, null);
  }

  private double getHourlyReportSum(
      OffsetDateTime beginning, OffsetDateTime ending, String billingAccountId) {
    Map<String, String> queryParams =
        new java.util.HashMap<>(
            Map.of(
                "granularity", "Hourly",
                "beginning", beginning.toString(),
                "ending", ending.toString()));
    if (billingAccountId != null) {
      queryParams.put("billing_account_id", billingAccountId);
    }
    TallyReportData resp =
        service.getTallyReportData(orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, queryParams);
    if (resp.getData() == null) {
      return 0.0;
    }
    return resp.getData().stream()
        .collect(Collectors.summarizingInt(TallyReportDataPoint::getValue))
        .getSum();
  }

  private void publishEvent(OffsetDateTime now, String billingAccountId, float value) {
    publishEventForInstance(
        UUID.randomUUID().toString(), now.minusHours(1), billingAccountId, value);
  }

  private void publishEventForInstance(
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

  private double tallyValue(List<TallySummary> summaries, String billingAccountId) {
    return helpers.getTallySummaryValueWithBillingAccountIdFilter(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.HOURLY, billingAccountId);
  }
}
