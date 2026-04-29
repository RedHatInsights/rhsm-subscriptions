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
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import utils.TallyDbHostSeeder;

public class TallyInstancesBillingAccountIdsTest extends BaseTallyComponentTest {

  /** Two distinct rows after tally (distinguish-by-billing). */
  private record DistinctBillings(String first, String second) {}

  /** Three products of interest for the three-ID assertion. */
  private record ThreeBillings(String a, String b, String c) {}

  /** One billing_id shared by two different instance events. */
  private record SharedByTwoEvents(String id) {}

  private record MixedProviderBillings(String aws, String azure) {}

  private static String testOrgId;

  private static DistinctBillings distinct;
  private static ThreeBillings three;
  private static SharedByTwoEvents shared;
  private static MixedProviderBillings providers;

  @BeforeAll
  static void setupEvents() {
    testOrgId = RandomUtils.generateRandom();
    final String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
    final OffsetDateTime eventTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
    final OffsetDateTime eventHour =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);

    distinct = new DistinctBillings(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    three =
        new ThreeBillings(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    shared = new SharedByTwoEvents(UUID.randomUUID().toString());
    providers =
        new MixedProviderBillings(UUID.randomUUID().toString(), UUID.randomUUID().toString());

    Event eventAzure =
        helpers.createPaygEventWithTimestamp(
            testOrgId,
            UUID.randomUUID().toString(),
            eventHour.toString(),
            UUID.randomUUID().toString(),
            metricId,
            1.0f,
            Event.Sla.PREMIUM,
            Event.Usage.PRODUCTION,
            Event.BillingProvider.AZURE,
            providers.azure(),
            Event.HardwareType.CLOUD,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    eventAzure.setCloudProvider(Event.CloudProvider.AZURE);

    Event[] events =
        new Event[] {
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              distinct.first(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              distinct.second(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              three.a(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              three.b(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              three.c(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              shared.id(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventTime.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              shared.id(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          helpers.createPaygEventWithTimestamp(
              testOrgId,
              UUID.randomUUID().toString(),
              eventHour.toString(),
              UUID.randomUUID().toString(),
              metricId,
              1.0f,
              Event.Sla.PREMIUM,
              Event.Usage.PRODUCTION,
              Event.BillingProvider.AWS,
              providers.aws(),
              Event.HardwareType.CLOUD,
              RHEL_FOR_X86_ELS_PAYG.productId(),
              RHEL_FOR_X86_ELS_PAYG.productTag()),
          eventAzure,
        };

    helpers.ingestPaygEventsAndSyncOnceForOrg(
        service,
        kafkaBridge,
        testOrgId,
        () -> {
          Response response = service.getBillingAccountIds(testOrgId, new HashMap<>());
          List<Map<String, String>> ids = response.jsonPath().getList("ids");
          return ids != null && ids.size() >= 6;
        },
        events);
  }

  @Test
  @TestPlanName("tally-instances-billing-account-TC001")
  public void shouldExcludeBillingAccountsWithLastSeenBeforeCurrentMonth() {
    final String isolatedOrg = RandomUtils.generateRandom();
    final String testInventoryId = UUID.randomUUID().toString();
    final String billingAccountId = UUID.randomUUID().toString();
    final OffsetDateTime lastMonthDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(35);

    service.createOptInConfig(isolatedOrg);

    TallyDbHostSeeder.insertHostWithBillingAccountAndDate(
        isolatedOrg,
        testInventoryId,
        RHEL_FOR_X86_ELS_PAYG.productTag(),
        "AWS",
        billingAccountId,
        lastMonthDate);

    Map<String, Object> queryParams = new HashMap<>();
    Response response = service.getBillingAccountIds(isolatedOrg, queryParams);

    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(ids, "Response ids should not be null");

    boolean containsOldAccount =
        ids.stream()
            .anyMatch(
                entry ->
                    isolatedOrg.equals(entry.get("org_id"))
                        && billingAccountId.equals(entry.get("billing_account_id"))
                        && RHEL_FOR_X86_ELS_PAYG.productTag().equals(entry.get("product_tag"))
                        && "aws".equals(entry.get("billing_provider")));

    assertFalse(
        containsOldAccount,
        "Response should not contain billing account from last month: " + billingAccountId);
  }

  @Test
  @TestPlanName("tally-instances-billing-account-TC002")
  public void shouldReturnDistinctBillingAccountIdsAfterHourlyTally() {
    Map<String, Object> queryParams = new HashMap<>();
    Response response = service.getBillingAccountIds(testOrgId, queryParams);
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(
        ids, "Response ids should not be null. Response body: " + response.getBody().asString());

    List<String> billingAccountIds =
        ids.stream().map(item -> item.get("billing_account_id")).collect(Collectors.toList());
    assertTrue(
        billingAccountIds.contains(distinct.first()),
        "Response should contain billing account ID: " + distinct.first());
    assertTrue(
        billingAccountIds.contains(distinct.second()),
        "Response should contain billing account ID: " + distinct.second());

    for (Map<String, String> entry : ids) {
      if (distinct.first().equals(entry.get("billing_account_id"))
          || distinct.second().equals(entry.get("billing_account_id"))) {
        assertEquals(testOrgId, entry.get("org_id"), "Entry should have correct org_id");
        assertEquals(RHEL_FOR_X86_ELS_PAYG.productTag(), entry.get("product_tag"));
        assertEquals("aws", entry.get("billing_provider"));
      }
    }
  }

  @Test
  @TestPlanName("tally-instances-billing-account-TC003")
  public void shouldReturnInstancesWithThreeDistinctBillingAccountIds() {
    Response response = service.getBillingAccountIds(testOrgId, new HashMap<>());
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(ids);
    List<String> accounts =
        ids.stream().map(m -> m.get("billing_account_id")).collect(Collectors.toList());
    assertTrue(accounts.contains(three.a()));
    assertTrue(accounts.contains(three.b()));
    assertTrue(accounts.contains(three.c()));
  }

  @Test
  @TestPlanName("tally-instances-billing-account-TC004")
  public void shouldReturnInstancesWithTwoInstancesSharingSameBillingAccountId() {
    Response response = service.getBillingAccountIds(testOrgId, new HashMap<>());
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(ids);
    long rowsForShared =
        ids.stream().filter(m -> shared.id().equals(m.get("billing_account_id"))).count();
    assertEquals(
        1,
        rowsForShared,
        "Same billing account from two instances should dedupe to one billing_account_ids row");
  }

  @Test
  @TestPlanName("tally-instances-billing-account-TC005")
  public void shouldReturnInstancesWithMixedBillingProviders() {
    Response response = service.getBillingAccountIds(testOrgId, new HashMap<>());
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(ids);
    List<String> prov =
        ids.stream().map(m -> m.get("billing_provider")).sorted().collect(Collectors.toList());
    assertTrue(prov.contains("aws"));
    assertTrue(prov.contains("azure"));
  }
}
