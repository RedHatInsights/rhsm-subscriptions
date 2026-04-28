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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.ReportCategory;
import com.redhat.swatch.tally.test.model.SortDirection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class TallyInstancesReportPaginationAndSortTest extends BaseTallyComponentTest {

  private record PaginationFixture(String billingAccountId, List<String> instanceIds) {}

  private record LastSeenSortFixture(
      String billingAccountId, String earlyInstanceId, String laterInstanceId) {}

  private record DisplayNameSortFixture(String billingAccountId, String nameZ, String nameA) {}

  private record MetricSortFixture(
      String billingAccountId, String smallMeterInstanceId, String largeMeterInstanceId) {}

  private record CategorySortFixture(
      String billingAccountId, String cloudInstanceId, String physicalInstanceId) {}

  private static String testOrgId;
  private static String metricId;

  private static OffsetDateTime start;
  private static OffsetDateTime firstOfMonth;

  private static PaginationFixture pagination;
  private static LastSeenSortFixture lastSeen;
  private static OffsetDateTime lastSeenTEarly;
  private static OffsetDateTime lastSeenTLate;
  private static DisplayNameSortFixture displayName;
  private static MetricSortFixture metric;
  private static CategorySortFixture category;

  @BeforeAll
  static void setupEvents() {
    testOrgId = RandomUtils.generateRandom();
    start = OffsetDateTime.now(ZoneOffset.UTC);
    firstOfMonth = start.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
    OffsetDateTime eventHour = start.minusHours(2).truncatedTo(ChronoUnit.HOURS);

    pagination =
        new PaginationFixture(
            UUID.randomUUID().toString(),
            List.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()));

    lastSeen =
        new LastSeenSortFixture(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    lastSeenTEarly = firstOfMonth.plusHours(5);
    lastSeenTLate = firstOfMonth.plusHours(10);

    displayName =
        new DisplayNameSortFixture(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    metric =
        new MetricSortFixture(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    category =
        new CategorySortFixture(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    Event eventZ =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            displayName.nameZ(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            displayName.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventZ.setDisplayName(Optional.of("zzz-sort-name"));
    Event eventA =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            displayName.nameA(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            displayName.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventA.setDisplayName(Optional.of("aaa-sort-name"));

    Event physical =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            category.physicalInstanceId(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.PHYSICAL,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    physical.setUsage(Event.Usage.PRODUCTION);
    physical.setBillingAccountId(Optional.of(category.billingAccountId()));
    physical.setBillingProvider(Event.BillingProvider.AWS);

    Event cloud =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            category.cloudInstanceId(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            category.billingAccountId(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());

    List<String> paged = pagination.instanceIds();
    List<Event> all =
        List.of(
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                paged.get(0),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                pagination.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                paged.get(1),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                pagination.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                paged.get(2),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                pagination.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                lastSeen.earlyInstanceId(),
                lastSeenTEarly.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                lastSeen.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                lastSeen.laterInstanceId(),
                lastSeenTLate.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                lastSeen.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            eventZ,
            eventA,
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                metric.smallMeterInstanceId(),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                metric.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                metric.largeMeterInstanceId(),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                99.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                metric.billingAccountId(),
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            cloud,
            physical);

    helpers.ingestPaygEventsAndSyncOnceForOrg(
        service,
        kafkaBridge,
        testOrgId,
        () -> {
          Map<String, Object> p = Map.of("billing_account_id", pagination.billingAccountId());
          InstanceResponse r =
              service.getInstancesByProduct(
                  testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, p);
          return r.getData() != null && r.getData().size() >= 3;
        },
        all.toArray(Event[]::new));
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC001")
  public void shouldReturnInstancesReportWithPaginationLimitAndOffset() {
    Map<String, Object> page0 = new HashMap<>();
    page0.put("limit", 2);
    page0.put("offset", 0);
    page0.put("billing_account_id", pagination.billingAccountId());
    InstanceResponse firstPage =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, page0);
    assertEquals(2, firstPage.getData().size());
    assertNotNull(firstPage.getMeta());
    assertNotNull(firstPage.getMeta().getCount());
    assertEquals(3, firstPage.getMeta().getCount().intValue());

    Map<String, Object> page1 = new HashMap<>();
    page1.put("limit", 2);
    page1.put("offset", 2);
    page1.put("billing_account_id", pagination.billingAccountId());
    InstanceResponse secondPage =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, page1);
    assertEquals(1, secondPage.getData().size());
    assertEquals(3, secondPage.getMeta().getCount().intValue());

    Set<String> union =
        firstPage.getData() == null
            ? Set.of()
            : firstPage.getData().stream()
                .map(InstanceData::getInstanceId)
                .collect(Collectors.toSet());
    union.addAll(
        secondPage.getData() == null
            ? Set.of()
            : secondPage.getData().stream()
                .map(InstanceData::getInstanceId)
                .collect(Collectors.toSet()));
    for (String id : pagination.instanceIds()) {
      assertTrue(union.contains(id));
    }
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC002")
  public void shouldReturnInstancesReportWithPaginationLinks() {
    InstanceResponse withoutPagination =
        service.getInstancesByProduct(
            testOrgId,
            RHEL_FOR_X86_ELS_PAYG.productTag(),
            firstOfMonth,
            start,
            Map.of("billing_account_id", pagination.billingAccountId()));
    assertNull(withoutPagination.getLinks());

    Map<String, Object> withLimit = new HashMap<>();
    withLimit.put("limit", 2);
    withLimit.put("offset", 0);
    withLimit.put("billing_account_id", pagination.billingAccountId());
    InstanceResponse withPagination =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, withLimit);
    assertNotNull(withPagination.getLinks());
    assertFalse(withPagination.getLinks().getFirst().isBlank());
    assertNotNull(withPagination.getMeta().getCount());
    assertEquals(3, withPagination.getMeta().getCount().intValue());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC003")
  public void shouldReturnInstancesReportWithSortByLastSeen() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", lastSeen.billingAccountId());
    asc.put("sort", "last_seen");
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, asc);
    assertEquals(2, ascResp.getData().size());
    assertEquals(lastSeen.earlyInstanceId(), ascResp.getData().get(0).getInstanceId());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", lastSeen.billingAccountId());
    desc.put("sort", "last_seen");
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, desc);
    assertEquals(lastSeen.laterInstanceId(), descResp.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC004")
  public void shouldReturnInstancesReportWithSortByDisplayName() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", displayName.billingAccountId());
    asc.put("sort", "display_name");
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, asc);
    assertEquals(2, ascResp.getData().size());
    assertEquals(displayName.nameA(), ascResp.getData().get(0).getInstanceId());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", displayName.billingAccountId());
    desc.put("sort", "display_name");
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, desc);
    assertEquals(displayName.nameZ(), descResp.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC005")
  public void shouldReturnInstancesReportWithSortByMetricId() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", metric.billingAccountId());
    asc.put("sort", metricId);
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, asc);
    assertEquals(2, ascResp.getData().size());
    assertEquals(metric.smallMeterInstanceId(), ascResp.getData().get(0).getInstanceId());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", metric.billingAccountId());
    desc.put("sort", metricId);
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, desc);
    assertEquals(metric.largeMeterInstanceId(), descResp.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC006")
  public void shouldReturnInstancesReportWithSortByCategory() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", category.billingAccountId());
    asc.put("sort", "category");
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, asc);
    assertEquals(2, ascResp.getData().size());
    List<ReportCategory> ascCategories =
        ascResp.getData().stream().map(InstanceData::getCategory).collect(Collectors.toList());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", category.billingAccountId());
    desc.put("sort", "category");
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, start, desc);
    List<ReportCategory> descCategories =
        descResp.getData().stream().map(InstanceData::getCategory).collect(Collectors.toList());

    assertEquals(ascCategories.size(), descCategories.size());
    List<ReportCategory> ascReversed = new ArrayList<>(ascCategories);
    Collections.reverse(ascReversed);
    assertEquals(descCategories, ascReversed, "desc order should reverse asc category order");
  }
}
