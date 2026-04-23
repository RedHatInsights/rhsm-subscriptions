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

  private static String testOrgId;

  private static OffsetDateTime anchor;
  private static OffsetDateTime firstOfMonth;
  private static String metricId;

  private static String billingPage;
  private static List<String> instanceIdsPage;

  private static String billingLastSeen;
  private static String instanceEarlier;
  private static String instanceLater;
  private static OffsetDateTime tEarly;
  private static OffsetDateTime tLate;

  private static String billingDisplay;
  private static String instanceZ;
  private static String instanceA;

  private static String billingMetric;
  private static String instanceSmall;
  private static String instanceLarge;

  private static String billingCategory;
  private static String instanceCloud;
  private static String instancePhysical;

  @BeforeAll
  static void setupEvents() {
    testOrgId = RandomUtils.generateRandom();
    anchor = OffsetDateTime.now(ZoneOffset.UTC);
    firstOfMonth = anchor.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
    OffsetDateTime eventHour = anchor.minusHours(2).truncatedTo(ChronoUnit.HOURS);

    billingPage = UUID.randomUUID().toString();
    instanceIdsPage =
        List.of(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());

    billingLastSeen = UUID.randomUUID().toString();
    instanceEarlier = UUID.randomUUID().toString();
    instanceLater = UUID.randomUUID().toString();
    tEarly = firstOfMonth.plusHours(5);
    tLate = firstOfMonth.plusHours(10);

    billingDisplay = UUID.randomUUID().toString();
    instanceZ = UUID.randomUUID().toString();
    instanceA = UUID.randomUUID().toString();

    billingMetric = UUID.randomUUID().toString();
    instanceSmall = UUID.randomUUID().toString();
    instanceLarge = UUID.randomUUID().toString();

    billingCategory = UUID.randomUUID().toString();
    instanceCloud = UUID.randomUUID().toString();
    instancePhysical = UUID.randomUUID().toString();

    Event eventZ =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceZ,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingDisplay,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventZ.setDisplayName(Optional.of("zzz-sort-name"));
    Event eventA =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceA,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingDisplay,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventA.setDisplayName(Optional.of("aaa-sort-name"));

    Event physical =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instancePhysical,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.HardwareType.PHYSICAL,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    physical.setUsage(Event.Usage.PRODUCTION);
    physical.setBillingAccountId(Optional.of(billingCategory));
    physical.setBillingProvider(Event.BillingProvider.AWS);

    Event cloud =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            instanceCloud,
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AWS,
            billingCategory,
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());

    List<Event> all =
        List.of(
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceIdsPage.get(0),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingPage,
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceIdsPage.get(1),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingPage,
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceIdsPage.get(2),
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingPage,
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceEarlier,
                tEarly.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingLastSeen,
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceLater,
                tLate.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingLastSeen,
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            eventZ,
            eventA,
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceSmall,
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                1.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingMetric,
                Event.HardwareType.CLOUD,
                RHEL_FOR_X86_ELS_PAYG.productId(),
                RHEL_FOR_X86_ELS_PAYG.productTag()),
            helpers.createPaygEventWithTimestamp(
                testOrgId,
                instanceLarge,
                eventHour.toString(),
                UUID.randomUUID().toString(),
                metricId,
                99.0f,
                Event.Sla.PREMIUM,
                Event.Usage.PRODUCTION,
                Event.BillingProvider.AWS,
                billingMetric,
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
          Map<String, Object> p = Map.of("billing_account_id", billingPage);
          InstanceResponse r =
              service.getInstancesByProduct(
                  testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, p);
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
    page0.put("billing_account_id", billingPage);
    InstanceResponse firstPage =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, page0);
    assertEquals(2, firstPage.getData().size());
    assertNotNull(firstPage.getMeta());
    assertNotNull(firstPage.getMeta().getCount());
    assertEquals(3, firstPage.getMeta().getCount().intValue());

    Map<String, Object> page1 = new HashMap<>();
    page1.put("limit", 2);
    page1.put("offset", 2);
    page1.put("billing_account_id", billingPage);
    InstanceResponse secondPage =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, page1);
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
    for (String id : instanceIdsPage) {
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
            anchor,
            Map.of("billing_account_id", billingPage));
    assertNull(withoutPagination.getLinks());

    Map<String, Object> withLimit = new HashMap<>();
    withLimit.put("limit", 2);
    withLimit.put("offset", 0);
    withLimit.put("billing_account_id", billingPage);
    InstanceResponse withPagination =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, withLimit);
    assertNotNull(withPagination.getLinks());
    assertFalse(withPagination.getLinks().getFirst().isBlank());
    assertNotNull(withPagination.getMeta().getCount());
    assertEquals(3, withPagination.getMeta().getCount().intValue());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC003")
  public void shouldReturnInstancesReportWithSortByLastSeen() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", billingLastSeen);
    asc.put("sort", "last_seen");
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, asc);
    assertEquals(2, ascResp.getData().size());
    assertEquals(instanceEarlier, ascResp.getData().get(0).getInstanceId());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", billingLastSeen);
    desc.put("sort", "last_seen");
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, desc);
    assertEquals(instanceLater, descResp.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC004")
  public void shouldReturnInstancesReportWithSortByDisplayName() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", billingDisplay);
    asc.put("sort", "display_name");
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, asc);
    assertEquals(2, ascResp.getData().size());
    assertEquals(instanceA, ascResp.getData().get(0).getInstanceId());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", billingDisplay);
    desc.put("sort", "display_name");
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, desc);
    assertEquals(instanceZ, descResp.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC005")
  public void shouldReturnInstancesReportWithSortByMetricId() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", billingMetric);
    asc.put("sort", metricId);
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, asc);
    assertEquals(2, ascResp.getData().size());
    assertEquals(instanceSmall, ascResp.getData().get(0).getInstanceId());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", billingMetric);
    desc.put("sort", metricId);
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, desc);
    assertEquals(instanceLarge, descResp.getData().get(0).getInstanceId());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC006")
  public void shouldReturnInstancesReportWithSortByCategory() {
    Map<String, Object> asc = new HashMap<>();
    asc.put("billing_account_id", billingCategory);
    asc.put("sort", "category");
    asc.put("dir", SortDirection.ASC);
    InstanceResponse ascResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, asc);
    assertEquals(2, ascResp.getData().size());
    List<ReportCategory> ascCategories =
        ascResp.getData().stream().map(InstanceData::getCategory).collect(Collectors.toList());

    Map<String, Object> desc = new HashMap<>();
    desc.put("billing_account_id", billingCategory);
    desc.put("sort", "category");
    desc.put("dir", SortDirection.DESC);
    InstanceResponse descResp =
        service.getInstancesByProduct(
            testOrgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfMonth, anchor, desc);
    List<ReportCategory> descCategories =
        descResp.getData().stream().map(InstanceData::getCategory).collect(Collectors.toList());

    assertEquals(ascCategories.size(), descCategories.size());
    List<ReportCategory> ascReversed = new ArrayList<>(ascCategories);
    Collections.reverse(ascReversed);
    assertEquals(descCategories, ascReversed, "desc order should reverse asc category order");
  }
}
