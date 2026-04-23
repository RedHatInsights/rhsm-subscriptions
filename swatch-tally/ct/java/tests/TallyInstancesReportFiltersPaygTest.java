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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.tally.test.model.BillingProviderType;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.UsageType;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallyInstancesReportFiltersPaygTest extends BaseTallyComponentTest {

  private static String testOrgId;
  private static OffsetDateTime start;
  private static OffsetDateTime firstOfCurrentMonth;
  private static OffsetDateTime firstOfPreviousMonth;
  private static OffsetDateTime eventHour;
  private static String metricId;

  private static String billingTc003;
  private static String instanceTc003;
  private static String eventIdTc003;

  private static String instanceTc004Premium;
  private static String instanceTc004Standard;
  private static String eventIdTc004a;
  private static String eventIdTc004b;
  private static String billingTc004;

  private static String instanceTc005Prod;
  private static String instanceTc005Dev;
  private static String eventIdTc005a;
  private static String eventIdTc005b;
  private static String billingTc005;

  private static String instanceTc006Aws;
  private static String instanceTc006Azure;
  private static String eventIdTc006a;
  private static String eventIdTc006b;
  private static String billingTc006Aws;
  private static String billingTc006Azure;

  private static String billingTc007;
  private static String wrongBillingTc007;
  private static String instanceTc007;
  private static String eventIdTc007;

  private static String billingTc008;
  private static String instanceTc008;
  private static String eventIdTc008;

  private static String instanceTc009a;
  private static String instanceTc009b;
  private static String billingTc009a;
  private static String billingTc009b;

  private static String instanceTc010a;
  private static String instanceTc010b;
  private static String billingTc010a;
  private static String billingTc010b;

  private static String instanceTc011a;
  private static String instanceTc011b;
  private static String instanceTc011c;
  private static String billingTc011a;
  private static String billingTc011b;
  private static String billingTc011c;

  private static String instanceTc012Prev;
  private static String instanceTc012Curr;
  private static String billingTc012Prev;
  private static String billingTc012Curr;

  private static final int EXPECTED_CURRENT_MONTH_INSTANCE_ROWS = 16;

  @BeforeAll
  static void setupSharedFixture() {
    testOrgId = RandomUtils.generateRandom();
    start = OffsetDateTime.now(ZoneOffset.UTC);
    firstOfCurrentMonth =
        start.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    firstOfPreviousMonth = firstOfCurrentMonth.minusMonths(1);
    eventHour = start.minusHours(2).truncatedTo(ChronoUnit.HOURS);
    metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

    billingTc003 = UUID.randomUUID().toString();
    instanceTc003 = UUID.randomUUID().toString();
    eventIdTc003 = UUID.randomUUID().toString();

    instanceTc004Premium = UUID.randomUUID().toString();
    instanceTc004Standard = UUID.randomUUID().toString();
    eventIdTc004a = UUID.randomUUID().toString();
    eventIdTc004b = UUID.randomUUID().toString();
    billingTc004 = UUID.randomUUID().toString();

    instanceTc005Prod = UUID.randomUUID().toString();
    instanceTc005Dev = UUID.randomUUID().toString();
    eventIdTc005a = UUID.randomUUID().toString();
    eventIdTc005b = UUID.randomUUID().toString();
    billingTc005 = UUID.randomUUID().toString();

    instanceTc006Aws = UUID.randomUUID().toString();
    instanceTc006Azure = UUID.randomUUID().toString();
    eventIdTc006a = UUID.randomUUID().toString();
    eventIdTc006b = UUID.randomUUID().toString();
    billingTc006Aws = UUID.randomUUID().toString();
    billingTc006Azure = UUID.randomUUID().toString();

    billingTc007 = UUID.randomUUID().toString();
    wrongBillingTc007 = UUID.randomUUID().toString();
    instanceTc007 = UUID.randomUUID().toString();
    eventIdTc007 = UUID.randomUUID().toString();

    billingTc008 = UUID.randomUUID().toString();
    instanceTc008 = UUID.randomUUID().toString();
    eventIdTc008 = UUID.randomUUID().toString();

    instanceTc009a = UUID.randomUUID().toString();
    instanceTc009b = UUID.randomUUID().toString();
    billingTc009a = UUID.randomUUID().toString();
    billingTc009b = UUID.randomUUID().toString();

    instanceTc010a = UUID.randomUUID().toString();
    instanceTc010b = UUID.randomUUID().toString();
    billingTc010a = UUID.randomUUID().toString();
    billingTc010b = UUID.randomUUID().toString();

    instanceTc011a = UUID.randomUUID().toString();
    instanceTc011b = UUID.randomUUID().toString();
    instanceTc011c = UUID.randomUUID().toString();
    billingTc011a = UUID.randomUUID().toString();
    billingTc011b = UUID.randomUUID().toString();
    billingTc011c = UUID.randomUUID().toString();

    instanceTc012Prev = UUID.randomUUID().toString();
    instanceTc012Curr = UUID.randomUUID().toString();
    billingTc012Prev = UUID.randomUUID().toString();
    billingTc012Curr = UUID.randomUUID().toString();

    OffsetDateTime tc003EventTime = firstOfPreviousMonth.plusHours(1);
    OffsetDateTime tc007EventTime = start.minusHours(1);
    OffsetDateTime tc008EventTime = start.minusHours(1);
    OffsetDateTime tc012PrevTime = firstOfPreviousMonth.plusHours(3);
    OffsetDateTime tc012CurrTime = start.minusHours(1);

    List<Event> events = new ArrayList<>();
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc003,
            tc003EventTime.toString(),
            eventIdTc003,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc003,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc004Premium,
            eventHour.toString(),
            eventIdTc004a,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc004,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc004Standard,
            eventHour.toString(),
            eventIdTc004b,
            metricId,
            1.0f,
            Event.Sla.STANDARD,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc004,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc005Prod,
            eventHour.toString(),
            eventIdTc005a,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc005,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc005Dev,
            eventHour.toString(),
            eventIdTc005b,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            billingTc005,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc006Aws,
            eventHour.toString(),
            eventIdTc006a,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc006Aws,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    Event eventTc006Azure =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc006Azure,
            eventHour.toString(),
            eventIdTc006b,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AZURE,
            billingTc006Azure,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventTc006Azure.setCloudProvider(Event.CloudProvider.AZURE);
    events.add(eventTc006Azure);

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc007,
            tc007EventTime.toString(),
            eventIdTc007,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc007,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc008,
            tc008EventTime.toString(),
            eventIdTc008,
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc008,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc009a,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc009a,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc009b,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc009b,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc010a,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc010a,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc010b,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc010b,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    Stream.of(
            new String[] {instanceTc011a, billingTc011a},
            new String[] {instanceTc011b, billingTc011b},
            new String[] {instanceTc011c, billingTc011c})
        .forEach(
            pair ->
                events.add(
                    helpers.createPaygEventWithTimestamp(
                        testOrgId,
                        pair[0],
                        eventHour.toString(),
                        UUID.randomUUID().toString(),
                        metricId,
                        1.0f,
                        Event.Sla.PREMIUM,
                        Event.Usage.PRODUCTION,
                        Event.BillingProvider.AWS,
                        pair[1],
                        Event.HardwareType.CLOUD,
                        RHEL_FOR_X86_ELS_PAYG.productId(),
                        RHEL_FOR_X86_ELS_PAYG.productTag())));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc012Prev,
            tc012PrevTime.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc012Prev,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceTc012Curr,
            tc012CurrTime.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingTc012Curr,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    helpers.ingestPaygEventsAndSyncOnceForOrg(
        service,
        kafkaBridge,
        testOrgId,
        () -> {
          InstanceResponse r =
              service.getInstancesByProduct(
                  testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, null);
          return r.getData() != null && r.getData().size() >= EXPECTED_CURRENT_MONTH_INSTANCE_ROWS;
        },
        events.toArray(Event[]::new));
  }

  @Test
  @TestPlanName("tally-instances-payg-TC001")
  public void shouldReportPaygInstancesOnlyInEventCalendarMonth() {
    Map<String, Object> queryParams = Map.of("billing_account_id", billingTc003);
    InstanceResponse currentMonthResponse =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, queryParams);

    assertEquals(
        0.0,
        sumMeteredValues(currentMonthResponse),
        0.001,
        "Current month should have no metered value for event from previous month");

    InstanceResponse previousMonthResponse =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfPreviousMonth,
            firstOfPreviousMonth.plusDays(1),
            queryParams);

    double meteredValueLastMonth = sumMeteredValues(previousMonthResponse);
    assertTrue(
        meteredValueLastMonth > 0.0,
        "Previous month should have metered value greater than 0. Got: " + meteredValueLastMonth);
  }

  @Test
  @TestPlanName("tally-instances-payg-TC002")
  public void shouldFilterInstancesReportBySla() {
    Map<String, Object> standardParams = new HashMap<>();
    standardParams.put("sla", ServiceLevelType.STANDARD);
    standardParams.put("billing_account_id", billingTc004);
    InstanceResponse standardOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            standardParams);
    assertNotNull(standardOnly.getData());
    assertEquals(1, standardOnly.getData().size(), "STANDARD SLA filter should return one row");
    assertEquals(
        instanceTc004Standard,
        standardOnly.getData().get(0).getInstanceId(),
        "Row should be the STANDARD instance");
    assertEquals(1.0, sumMeteredValues(standardOnly), 0.001);
    assertNotNull(standardOnly.getMeta());
    assertEquals(ServiceLevelType.STANDARD, standardOnly.getMeta().getServiceLevel());

    Map<String, Object> premiumParams = new HashMap<>();
    premiumParams.put("sla", ServiceLevelType.PREMIUM);
    premiumParams.put("billing_account_id", billingTc004);
    InstanceResponse premiumOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            premiumParams);
    assertNotNull(premiumOnly.getData());
    assertEquals(1, premiumOnly.getData().size());
    assertEquals(instanceTc004Premium, premiumOnly.getData().get(0).getInstanceId());
    assertEquals(ServiceLevelType.PREMIUM, premiumOnly.getMeta().getServiceLevel());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC003")
  public void shouldFilterInstancesReportByUsage() {
    Map<String, Object> productionParams = new HashMap<>();
    productionParams.put("usage", UsageType.PRODUCTION);
    productionParams.put("billing_account_id", billingTc005);
    InstanceResponse productionOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            productionParams);
    assertNotNull(productionOnly.getData());
    assertEquals(1, productionOnly.getData().size());
    assertEquals(instanceTc005Prod, productionOnly.getData().get(0).getInstanceId());
    assertEquals(UsageType.PRODUCTION, productionOnly.getMeta().getUsage());

    Map<String, Object> developmentParams = new HashMap<>();
    developmentParams.put("usage", UsageType.DEVELOPMENT_TEST);
    developmentParams.put("billing_account_id", billingTc005);
    InstanceResponse developmentOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            developmentParams);
    assertNotNull(developmentOnly.getData());
    assertEquals(1, developmentOnly.getData().size());
    assertEquals(instanceTc005Dev, developmentOnly.getData().get(0).getInstanceId());
    assertEquals(UsageType.DEVELOPMENT_TEST, developmentOnly.getMeta().getUsage());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC004")
  public void shouldFilterInstancesReportByBillingProvider() {
    Map<String, Object> azureParams = new HashMap<>();
    azureParams.put("billing_provider", BillingProviderType.AZURE);
    azureParams.put("billing_account_id", billingTc006Azure);
    InstanceResponse azureOnly =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, azureParams);
    assertNotNull(azureOnly.getData());
    assertEquals(1, azureOnly.getData().size());
    assertEquals(instanceTc006Azure, azureOnly.getData().get(0).getInstanceId());
    assertEquals(BillingProviderType.AZURE, azureOnly.getMeta().getBillingProvider());

    Map<String, Object> awsParams = new HashMap<>();
    awsParams.put("billing_provider", BillingProviderType.AWS);
    awsParams.put("billing_account_id", billingTc006Aws);
    InstanceResponse awsOnly =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, awsParams);
    assertNotNull(awsOnly.getData());
    assertEquals(1, awsOnly.getData().size());
    assertEquals(instanceTc006Aws, awsOnly.getData().get(0).getInstanceId());
    assertEquals(BillingProviderType.AWS, awsOnly.getMeta().getBillingProvider());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC005")
  public void shouldExcludeInstancesWithNonMatchingBillingAccountId() {
    Map<String, Object> wrongAccount = Map.of("billing_account_id", wrongBillingTc007);
    InstanceResponse noMatch =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            wrongAccount);
    assertEquals(
        0.0,
        sumMeteredValues(noMatch),
        0.001,
        "Non-matching billing_account_id should yield no metered value");

    Map<String, Object> correctAccount = Map.of("billing_account_id", billingTc007);
    InstanceResponse match =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            correctAccount);
    assertTrue(
        sumMeteredValues(match) > 0.0, "Matching billing_account_id should return metered data");
  }

  @Test
  @TestPlanName("tally-instances-payg-TC006")
  public void shouldReturnInstancesReportWithAllOptionalFilters() {
    Map<String, Object> allFilters = new HashMap<>();
    allFilters.put("sla", ServiceLevelType.PREMIUM);
    allFilters.put("usage", UsageType.PRODUCTION);
    allFilters.put("billing_provider", BillingProviderType.AWS);
    allFilters.put("billing_account_id", billingTc008);

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, allFilters);
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size());
    InstanceData row = response.getData().get(0);
    assertEquals(instanceTc008, row.getInstanceId());
    assertTrue(sumMeteredValues(response) > 0.0);

    assertNotNull(response.getMeta());
    assertEquals(ServiceLevelType.PREMIUM, response.getMeta().getServiceLevel());
    assertEquals(UsageType.PRODUCTION, response.getMeta().getUsage());
    assertEquals(BillingProviderType.AWS, response.getMeta().getBillingProvider());
    assertEquals(billingTc008, response.getMeta().getBillingAccountId());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC007")
  public void shouldReturnInstancesReportWithPartialFiltersAndDifferentBillingAccountIds() {
    Map<String, Object> partial =
        Map.of("sla", ServiceLevelType.PREMIUM, "usage", UsageType.PRODUCTION);

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, partial);
    assertNotNull(response.getData());
    Set<String> ids =
        response.getData() == null
            ? Set.of()
            : response.getData().stream()
                .map(InstanceData::getInstanceId)
                .collect(Collectors.toSet());
    assertTrue(
        ids.contains(instanceTc009a) && ids.contains(instanceTc009b),
        "sla+usage filter should include both fixture instances with different billing accounts");
    long fixtureRows =
        response.getData().stream()
            .filter(
                d ->
                    instanceTc009a.equals(d.getInstanceId())
                        || instanceTc009b.equals(d.getInstanceId()))
            .count();
    assertEquals(2, fixtureRows, "Each fixture instance should appear once under partial filters");
  }

  @Test
  @TestPlanName("tally-instances-payg-TC008")
  public void shouldReturnInstancesReportWithPartialFiltersAndSameBillingAccountId() {
    Map<String, Object> narrowed = new HashMap<>();
    narrowed.put("sla", ServiceLevelType.PREMIUM);
    narrowed.put("usage", UsageType.PRODUCTION);
    narrowed.put("billing_account_id", billingTc010a);

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, narrowed);
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size());
    assertEquals(instanceTc010a, response.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC009")
  public void shouldReturnInstancesReportWithNoOptionalFilters() {
    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, null);
    assertNotNull(response.getData());
    Set<String> returned =
        response.getData() == null
            ? Set.of()
            : response.getData().stream()
                .map(InstanceData::getInstanceId)
                .collect(Collectors.toSet());
    for (String id : List.of(instanceTc011a, instanceTc011b, instanceTc011c)) {
      assertTrue(returned.contains(id), "Expected instance id " + id);
    }
    assertTrue(
        response.getData().size() >= 3,
        "Unfiltered report should include at least the three TC011 instances");
    assertNotNull(response.getMeta());
    assertNotNull(response.getMeta().getCount());
    assertEquals(
        response.getData().size(),
        response.getMeta().getCount().intValue(),
        "Meta count should match row count for the unfiltered query");
  }

  @Test
  @TestPlanName("tally-instances-payg-TC010")
  public void shouldReturnInstancesReportWithTwoEventsInDifferentMonths() {
    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, null);
    Set<String> ids =
        response.getData() == null
            ? Set.of()
            : response.getData().stream()
                .map(InstanceData::getInstanceId)
                .collect(Collectors.toSet());
    assertTrue(
        ids.contains(instanceTc012Curr),
        "Current-month instance should appear for current-month window");
    assertFalse(
        ids.contains(instanceTc012Prev),
        "Previous-month-only instance should not appear in current-month window");
  }

  @Test
  @TestPlanName("tally-instances-payg-TC011")
  public void shouldRejectCrossMonthInstancesQuery() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime beginning = now.withDayOfMonth(10).truncatedTo(ChronoUnit.DAYS).withHour(12);
    OffsetDateTime ending = beginning.plusMonths(1).withDayOfMonth(10).withHour(12);

    Response response =
        service.getInstancesByProductRaw(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), beginning, ending, null);

    assertEquals(
        HttpStatus.SC_BAD_REQUEST,
        response.getStatusCode(),
        "PAYG instances must reject beginning/ending in different calendar months: "
            + response.getBody().asString());
    assertTrue(
        response.getBody().asString().toLowerCase().contains("month"),
        "Error body should mention month restriction: " + response.getBody().asString());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC012")
  public void shouldReturnMeteredInstancesForFullCalendarMonthWindow() {
    ZoneOffset utc = ZoneOffset.UTC;
    OffsetDateTime monthStart =
        start.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    OffsetDateTime monthEnd =
        YearMonth.from(start).atEndOfMonth().atTime(23, 59, 59, 999_000_000).atOffset(utc);

    Map<String, Object> filters = Map.of("billing_account_id", billingTc004);

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), monthStart, monthEnd, filters);

    assertNotNull(response.getData());
    assertFalse(response.getData().isEmpty(), "Full-month window should return instance rows");
    assertTrue(
        sumMeteredValues(response) > 0.0,
        "Metered total should be positive for TC004 fixture rows in the current month");
  }

  // --- Methods to simplify test assertions ---

  private double sumMeteredValues(InstanceResponse response) {
    if (response.getData() == null) {
      return 0.0;
    }
    return response.getData().stream()
        .filter(instance -> instance.getMeasurements() != null)
        .flatMapToDouble(
            instance -> instance.getMeasurements().stream().mapToDouble(Double::doubleValue))
        .sum();
  }
}
