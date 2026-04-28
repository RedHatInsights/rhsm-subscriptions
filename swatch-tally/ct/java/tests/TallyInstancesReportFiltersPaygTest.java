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
import org.apache.http.HttpStatus;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallyInstancesReportFiltersPaygTest extends BaseTallyComponentTest {

  private record PriorMonthInstance(String instanceId, String billingAccountId, String eventId) {}

  private record SlaFilterRows(
      String billingAccountId,
      String premiumInstanceId,
      String standardInstanceId,
      String premiumEventId,
      String standardEventId) {}

  private record UsageFilterRows(
      String billingAccountId,
      String productionInstanceId,
      String developmentInstanceId,
      String productionEventId,
      String developmentEventId) {}

  private record CloudProviderPair(
      String awsInstanceId,
      String azureInstanceId,
      String awsEventId,
      String azureEventId,
      String awsBillingAccountId,
      String azureBillingAccountId) {}

  private record BillingExclusion(
      String matchingBillingAccountId,
      String decoyBillingAccountId,
      String instanceId,
      String eventId) {}

  private record AllOptionalFilters(String billingAccountId, String instanceId, String eventId) {}

  private record PartialSlaUsagePair(
      String firstInstanceId,
      String secondInstanceId,
      String firstBillingAccountId,
      String secondBillingAccountId) {}

  private record NarrowedBillingPair(
      String matchInstanceId,
      String siblingInstanceId,
      String matchBillingAccountId,
      String siblingBillingAccountId) {}

  private record UnfilteredRowTriple(
      String aInstanceId,
      String bInstanceId,
      String cInstanceId,
      String aBilling,
      String bBilling,
      String cBilling) {}

  private record CrossMonthPair(
      String previousMonthInstanceId,
      String currentMonthInstanceId,
      String previousMonthBilling,
      String currentMonthBilling) {}

  private static String testOrgId;
  private static OffsetDateTime start;
  private static OffsetDateTime firstOfCurrentMonth;
  private static OffsetDateTime firstOfPreviousMonth;
  private static OffsetDateTime eventHour;
  private static String metricId;

  private static PriorMonthInstance priorMonth;
  private static SlaFilterRows sla;
  private static UsageFilterRows usage;
  private static CloudProviderPair cloudProviders;
  private static BillingExclusion billingExclusion;
  private static AllOptionalFilters allOptional;
  private static PartialSlaUsagePair partialSlaUsage;
  private static NarrowedBillingPair narrowedBilling;
  private static UnfilteredRowTriple unfilteredTriple;
  private static CrossMonthPair crossMonth;

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

    priorMonth =
        new PriorMonthInstance(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    sla =
        new SlaFilterRows(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    usage =
        new UsageFilterRows(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    cloudProviders =
        new CloudProviderPair(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    billingExclusion =
        new BillingExclusion(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    allOptional =
        new AllOptionalFilters(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    partialSlaUsage =
        new PartialSlaUsagePair(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    narrowedBilling =
        new NarrowedBillingPair(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    unfilteredTriple =
        new UnfilteredRowTriple(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    crossMonth =
        new CrossMonthPair(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    OffsetDateTime priorMonthEventTime = firstOfPreviousMonth.plusHours(1);
    OffsetDateTime billingExclusionEventTime = start.minusHours(1);
    OffsetDateTime allOptionalEventTime = start.minusHours(1);
    OffsetDateTime previousMonthForCross = firstOfPreviousMonth.plusHours(3);
    OffsetDateTime currentMonthForCross = start.minusHours(1);

    List<Event> events = new ArrayList<>();
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            priorMonth.instanceId(),
            priorMonthEventTime.toString(),
            priorMonth.eventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            priorMonth.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            sla.premiumInstanceId(),
            eventHour.toString(),
            sla.premiumEventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            sla.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            sla.standardInstanceId(),
            eventHour.toString(),
            sla.standardEventId(),
            metricId,
            1.0f,
            Event.Sla.STANDARD,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            sla.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            usage.productionInstanceId(),
            eventHour.toString(),
            usage.productionEventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            usage.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            usage.developmentInstanceId(),
            eventHour.toString(),
            usage.developmentEventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.DEVELOPMENT_TEST,
            Event.BillingProvider.AWS,
            usage.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            cloudProviders.awsInstanceId(),
            eventHour.toString(),
            cloudProviders.awsEventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            cloudProviders.awsBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    Event eventAzure =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            cloudProviders.azureInstanceId(),
            eventHour.toString(),
            cloudProviders.azureEventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AZURE,
            cloudProviders.azureBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventAzure.setCloudProvider(Event.CloudProvider.AZURE);
    events.add(eventAzure);

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            billingExclusion.instanceId(),
            billingExclusionEventTime.toString(),
            billingExclusion.eventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingExclusion.matchingBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            allOptional.instanceId(),
            allOptionalEventTime.toString(),
            allOptional.eventId(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            allOptional.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            partialSlaUsage.firstInstanceId(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            partialSlaUsage.firstBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            partialSlaUsage.secondInstanceId(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            partialSlaUsage.secondBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            narrowedBilling.matchInstanceId(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            narrowedBilling.matchBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            narrowedBilling.siblingInstanceId(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            narrowedBilling.siblingBillingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));

    for (String[] pair :
        List.of(
            new String[] {unfilteredTriple.aInstanceId(), unfilteredTriple.aBilling()},
            new String[] {unfilteredTriple.bInstanceId(), unfilteredTriple.bBilling()},
            new String[] {unfilteredTriple.cInstanceId(), unfilteredTriple.cBilling()})) {
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
              RHEL_FOR_X86_ELS_PAYG.productTag()));
    }

    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            crossMonth.previousMonthInstanceId(),
            previousMonthForCross.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            crossMonth.previousMonthBilling(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag()));
    events.add(
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            crossMonth.currentMonthInstanceId(),
            currentMonthForCross.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            crossMonth.currentMonthBilling(),
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
    Map<String, Object> queryParams = Map.of("billing_account_id", priorMonth.billingAccountId());
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
    standardParams.put("billing_account_id", sla.billingAccountId());
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
        sla.standardInstanceId(),
        standardOnly.getData().get(0).getInstanceId(),
        "Row should be the STANDARD instance");
    assertEquals(1.0, sumMeteredValues(standardOnly), 0.001);
    assertNotNull(standardOnly.getMeta());
    assertEquals(ServiceLevelType.STANDARD, standardOnly.getMeta().getServiceLevel());

    Map<String, Object> premiumParams = new HashMap<>();
    premiumParams.put("sla", ServiceLevelType.PREMIUM);
    premiumParams.put("billing_account_id", sla.billingAccountId());
    InstanceResponse premiumOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            premiumParams);
    assertNotNull(premiumOnly.getData());
    assertEquals(1, premiumOnly.getData().size());
    assertEquals(sla.premiumInstanceId(), premiumOnly.getData().get(0).getInstanceId());
    assertEquals(ServiceLevelType.PREMIUM, premiumOnly.getMeta().getServiceLevel());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC003")
  public void shouldFilterInstancesReportByUsage() {
    Map<String, Object> productionParams = new HashMap<>();
    productionParams.put("usage", UsageType.PRODUCTION);
    productionParams.put("billing_account_id", usage.billingAccountId());
    InstanceResponse productionOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            productionParams);
    assertNotNull(productionOnly.getData());
    assertEquals(1, productionOnly.getData().size());
    assertEquals(usage.productionInstanceId(), productionOnly.getData().get(0).getInstanceId());
    assertEquals(UsageType.PRODUCTION, productionOnly.getMeta().getUsage());

    Map<String, Object> developmentParams = new HashMap<>();
    developmentParams.put("usage", UsageType.DEVELOPMENT_TEST);
    developmentParams.put("billing_account_id", usage.billingAccountId());
    InstanceResponse developmentOnly =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfCurrentMonth,
            start,
            developmentParams);
    assertNotNull(developmentOnly.getData());
    assertEquals(1, developmentOnly.getData().size());
    assertEquals(usage.developmentInstanceId(), developmentOnly.getData().get(0).getInstanceId());
    assertEquals(UsageType.DEVELOPMENT_TEST, developmentOnly.getMeta().getUsage());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC004")
  public void shouldFilterInstancesReportByBillingProvider() {
    Map<String, Object> azureParams = new HashMap<>();
    azureParams.put("billing_provider", BillingProviderType.AZURE);
    azureParams.put("billing_account_id", cloudProviders.azureBillingAccountId());
    InstanceResponse azureOnly =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, azureParams);
    assertNotNull(azureOnly.getData());
    assertEquals(1, azureOnly.getData().size());
    assertEquals(cloudProviders.azureInstanceId(), azureOnly.getData().get(0).getInstanceId());
    assertEquals(BillingProviderType.AZURE, azureOnly.getMeta().getBillingProvider());

    Map<String, Object> awsParams = new HashMap<>();
    awsParams.put("billing_provider", BillingProviderType.AWS);
    awsParams.put("billing_account_id", cloudProviders.awsBillingAccountId());
    InstanceResponse awsOnly =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, awsParams);
    assertNotNull(awsOnly.getData());
    assertEquals(1, awsOnly.getData().size());
    assertEquals(cloudProviders.awsInstanceId(), awsOnly.getData().get(0).getInstanceId());
    assertEquals(BillingProviderType.AWS, awsOnly.getMeta().getBillingProvider());
  }

  @Test
  @TestPlanName("tally-instances-payg-TC005")
  public void shouldExcludeInstancesWithNonMatchingBillingAccountId() {
    Map<String, Object> wrongAccount =
        Map.of("billing_account_id", billingExclusion.decoyBillingAccountId());
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

    Map<String, Object> correctAccount =
        Map.of("billing_account_id", billingExclusion.matchingBillingAccountId());
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
    allFilters.put("billing_account_id", allOptional.billingAccountId());

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, allFilters);
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size());
    InstanceData row = response.getData().get(0);
    assertEquals(allOptional.instanceId(), row.getInstanceId());
    assertTrue(sumMeteredValues(response) > 0.0);

    assertNotNull(response.getMeta());
    assertEquals(ServiceLevelType.PREMIUM, response.getMeta().getServiceLevel());
    assertEquals(UsageType.PRODUCTION, response.getMeta().getUsage());
    assertEquals(BillingProviderType.AWS, response.getMeta().getBillingProvider());
    assertEquals(allOptional.billingAccountId(), response.getMeta().getBillingAccountId());
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
        ids.contains(partialSlaUsage.firstInstanceId())
            && ids.contains(partialSlaUsage.secondInstanceId()),
        "sla+usage filter should include both fixture instances with different billing accounts");
    long fixtureRows =
        response.getData().stream()
            .filter(
                d ->
                    partialSlaUsage.firstInstanceId().equals(d.getInstanceId())
                        || partialSlaUsage.secondInstanceId().equals(d.getInstanceId()))
            .count();
    assertEquals(2, fixtureRows, "Each fixture instance should appear once under partial filters");
  }

  @Test
  @TestPlanName("tally-instances-payg-TC008")
  public void shouldReturnInstancesReportWithPartialFiltersAndSameBillingAccountId() {
    Map<String, Object> narrowed = new HashMap<>();
    narrowed.put("sla", ServiceLevelType.PREMIUM);
    narrowed.put("usage", UsageType.PRODUCTION);
    narrowed.put("billing_account_id", narrowedBilling.matchBillingAccountId());

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, start, narrowed);
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size());
    assertEquals(narrowedBilling.matchInstanceId(), response.getData().get(0).getInstanceId());
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
    for (String id :
        List.of(
            unfilteredTriple.aInstanceId(),
            unfilteredTriple.bInstanceId(),
            unfilteredTriple.cInstanceId())) {
      assertTrue(returned.contains(id), "Expected instance id " + id);
    }
    assertTrue(
        response.getData().size() >= 3,
        "Unfiltered report should include at least the three instances used in the unfiltered-triple "
            + "fixture");
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
        ids.contains(crossMonth.currentMonthInstanceId()),
        "Current-month instance should appear for current-month window");
    assertFalse(
        ids.contains(crossMonth.previousMonthInstanceId()),
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

    Map<String, Object> filters = Map.of("billing_account_id", sla.billingAccountId());

    InstanceResponse response =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), monthStart, monthEnd, filters);

    assertNotNull(response.getData());
    assertFalse(response.getData().isEmpty(), "Full-month window should return instance rows");
    assertTrue(
        sumMeteredValues(response) > 0.0,
        "Metered total should be positive for the SLA filter fixture rows in the current month");
  }

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
