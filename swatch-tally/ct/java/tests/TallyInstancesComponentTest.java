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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG;

import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Test;
import utils.TallyDbHostSeeder;

@Slf4j
public class TallyInstancesComponentTest extends BaseTallyComponentTest {

  @Test
  public void testGetBillingAccountIdsCurrentMonthBoundaryViaInstances() {
    // Given: A host with a last_seen date from last month (35 days ago)
    final String testInventoryId = UUID.randomUUID().toString();
    final String billingAccountId = UUID.randomUUID().toString();
    final OffsetDateTime lastMonthDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(35);

    service.createOptInConfig(orgId);

    TallyDbHostSeeder.insertHostWithBillingAccountAndDate(
        orgId,
        testInventoryId,
        RHEL_FOR_X86_ELS_PAYG.productTag(),
        "AWS",
        billingAccountId,
        lastMonthDate);

    // When: Calling get billing account ids
    Map<String, Object> queryParams = new HashMap<>();
    Response response = service.getBillingAccountIds(orgId, queryParams);

    // Then: Response should not contain the billing account from last month
    List<Map<String, String>> ids = response.jsonPath().getList("ids");
    assertNotNull(ids, "Response ids should not be null");

    boolean containsOldAccount =
        ids.stream()
            .anyMatch(
                entry ->
                    orgId.equals(entry.get("org_id"))
                        && billingAccountId.equals(entry.get("billing_account_id"))
                        && RHEL_FOR_X86_ELS_PAYG.productTag().equals(entry.get("product_tag"))
                        && "aws".equals(entry.get("billing_provider")));

    assertFalse(
        containsOldAccount,
        "Response should not contain billing account from last month: " + billingAccountId);
  }

  @Test
  public void testGetBillingAccountIdsViaInstances() {
    // Given: Two PAYG events with distinct billing account IDs
    final String instanceId1 = UUID.randomUUID().toString();
    final String instanceId2 = UUID.randomUUID().toString();
    final String eventId1 = UUID.randomUUID().toString();
    final String eventId2 = UUID.randomUUID().toString();
    final String billingAccountId1 = UUID.randomUUID().toString();
    final String billingAccountId2 = UUID.randomUUID().toString();
    final String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);
    final OffsetDateTime eventTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);

    givenOptInAndIngress(
        awsElsPaygEvent(instanceId1, eventId1, eventTime, metricId, billingAccountId1),
        awsElsPaygEvent(instanceId2, eventId2, eventTime, metricId, billingAccountId2));

    Map<String, Object> queryParams = new HashMap<>();

    // When / Then: Hourly tally materializes hosts/buckets; then billing_account_ids reflects them.
    untilHourlyTallyThen(
        () -> {
          Response response = service.getBillingAccountIds(orgId, queryParams);
          List<Map<String, String>> ids = response.jsonPath().getList("ids");
          assertNotNull(
              ids,
              "Response ids should not be null. Response body: " + response.getBody().asString());
          assertEquals(2, ids.size(), "Should return two billing account entries");

          List<String> billingAccountIds =
              ids.stream().map(item -> item.get("billing_account_id")).toList();

          assertTrue(
              billingAccountIds.contains(billingAccountId1),
              "Response should contain billing account ID: " + billingAccountId1);
          assertTrue(
              billingAccountIds.contains(billingAccountId2),
              "Response should contain billing account ID: " + billingAccountId2);

          for (Map<String, String> entry : ids) {
            assertEquals(orgId, entry.get("org_id"), "Entry should have correct org_id");
            assertEquals(
                RHEL_FOR_X86_ELS_PAYG.productTag(),
                entry.get("product_tag"),
                "Entry should have correct product_tag");
            assertEquals(
                "aws", entry.get("billing_provider"), "Entry should have correct billing_provider");
          }
        });
  }

  @Test
  public void testGetInstancesByProductPayg() {
    // Given: An event from the first day of the previous month
    final String billingAccountId = UUID.randomUUID().toString();
    final String instanceId = UUID.randomUUID().toString();
    final String eventId = UUID.randomUUID().toString();
    final String metricId = RHEL_FOR_X86_ELS_PAYG.metricIds().get(0);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime firstOfCurrentMonth =
        now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
    OffsetDateTime firstOfPreviousMonth = firstOfCurrentMonth.minusMonths(1);
    OffsetDateTime eventTimestamp = firstOfPreviousMonth.plusHours(1);

    givenOptInAndIngress(
        awsElsPaygEvent(instanceId, eventId, eventTimestamp, metricId, billingAccountId));

    Map<String, Object> queryParams = Map.of("billing_account_id", billingAccountId);
    untilHourlyTallyThen(
        () -> {
          InstanceResponse currentMonthResponse =
              service.getInstancesByProduct(
                  orgId, RHEL_FOR_X86_ELS_PAYG.productTag(), firstOfCurrentMonth, now, queryParams);

          assertEquals(
              0.0,
              sumMeteredValues(currentMonthResponse),
              0.001,
              "Current month should have no metered value for event from previous month");

          InstanceResponse previousMonthResponse =
              service.getInstancesByProduct(
                  orgId,
                  RHEL_FOR_X86_ELS_PAYG.productTag(),
                  firstOfPreviousMonth,
                  firstOfPreviousMonth.plusDays(1),
                  queryParams);

          double meteredValueLastMonth = sumMeteredValues(previousMonthResponse);
          assertTrue(
              meteredValueLastMonth > 0.0,
              "Previous month should have metered value greater than 0. Got: "
                  + meteredValueLastMonth);
        });
  }

  private void givenOptInAndIngress(Event... events) {
    service.createOptInConfig(orgId);
    for (Event event : events) {
      kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
    }
  }

  private Event awsElsPaygEvent(
      String instanceId,
      String eventId,
      OffsetDateTime timestamp,
      String metricId,
      String billingAccountId) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            orgId,
            instanceId,
            timestamp.toString(),
            eventId,
            metricId,
            1.0f,
            RHEL_FOR_X86_ELS_PAYG.productId(),
            RHEL_FOR_X86_ELS_PAYG.productTag());
    event.setBillingAccountId(Optional.of(billingAccountId));
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setCloudProvider(Event.CloudProvider.AWS);
    return event;
  }

  private void untilHourlyTallyThen(Runnable assertions) {
    AwaitilityUtils.untilAsserted(
        () -> {
          service.performHourlyTallyForOrg(orgId);
          assertions.run();
        });
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
