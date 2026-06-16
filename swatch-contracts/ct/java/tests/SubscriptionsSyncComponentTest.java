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

import static com.redhat.swatch.component.tests.utils.Topics.ENABLED_ORGS;
import static com.redhat.swatch.component.tests.utils.Topics.SUBSCRIPTION_SYNC_TASK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.DefaultMessageValidator;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilitySettings;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.EnabledOrgsRequest;
import com.redhat.swatch.contract.test.model.EnabledOrgsResponse;
import com.redhat.swatch.contract.test.model.GranularityType;
import domain.Offering;
import domain.Product;
import domain.Subscription;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SubscriptionsSyncComponentTest extends BaseContractComponentTest {

  private static final double SOCKETS_CAPACITY = 8.0;

  private static final OffsetDateTime REPORT_START = clock.startOfToday().minusDays(30);
  private static final OffsetDateTime REPORT_END = clock.endOfToday();
  private static final OffsetDateTime ACTIVE_SUBSCRIPTION_START =
      clock.startOfToday().minusDays(25);
  private static final OffsetDateTime EXPIRED_SEGMENT_START = REPORT_START.minusMonths(1);
  private static final OffsetDateTime EXPIRED_SEGMENT_END = clock.startOfToday().minusDays(15);
  private static final OffsetDateTime REPLACEMENT_START = clock.startOfToday().minusDays(14);

  @BeforeAll
  static void subscribeToBillableUsageTopic() {
    kafkaBridge.subscribeToTopic(ENABLED_ORGS);
  }

  @TestPlanName("subscriptions-sync-TC001")
  @Test
  void shouldSyncAllSubscriptionsForEnabledOrgs() {
    var response = service.syncAllSubscriptions();
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    kafkaBridge.waitForKafkaMessage(
        ENABLED_ORGS,
        new DefaultMessageValidator<>(
            request -> SUBSCRIPTION_SYNC_TASK.equals(request.getTargetTopic()),
            EnabledOrgsRequest.class));
  }

  @TestPlanName("subscriptions-sync-TC002")
  @Test
  void shouldSyncUmbSubscriptionMessage() {
    Subscription subscription = givenSubscription();
    var response = service.syncUmbSubscription(subscription);
    assertEquals("Success", response.getDetail());
  }

  @TestPlanName("subscriptions-sync-TC003")
  @Test
  void shouldDeleteAllSubscriptionsWhenSubscriptionSearchReturnsEmpty() {
    Subscription subscription = givenExistingSubscription(ACTIVE_SUBSCRIPTION_START, REPORT_END);

    wiremock.forSearchApi().stubSearchSubscriptionsByOrgId(orgId);
    whenSubscriptionSyncRunsForOrg();

    thenSubscriptionIsAbsent(subscription.getSubscriptionId());
  }

  @TestPlanName("subscriptions-sync-TC004")
  @ParameterizedTest
  @ValueSource(ints = {HttpStatus.SC_BAD_GATEWAY, HttpStatus.SC_INTERNAL_SERVER_ERROR})
  void shouldNotDeleteSubscriptionsWhenSubscriptionSearchFails(int httpStatus) {
    Subscription subscription = givenExistingSubscription(ACTIVE_SUBSCRIPTION_START, REPORT_END);

    wiremock.forSearchApi().stubSearchSubscriptionsByOrgIdFailure(orgId, httpStatus);
    whenSubscriptionSyncRunsForOrg();

    thenSubscriptionIsPresent(subscription.getSubscriptionId());
  }

  @TestPlanName("subscriptions-sync-TC005")
  @Test
  void shouldDeleteOmittedSubscriptionAndDropPriorMonthCapacity() {
    Subscription expiredSubscription =
        givenExistingSubscription(EXPIRED_SEGMENT_START, EXPIRED_SEGMENT_END);
    Subscription replacementSubscription =
        givenReplacementSubscription(REPLACEMENT_START, REPORT_END);

    whenSubscriptionSyncRunsWithUpstream(replacementSubscription);

    thenSubscriptionIsAbsent(expiredSubscription.getSubscriptionId());
    thenSubscriptionIsPresent(replacementSubscription.getSubscriptionId());
    thenDailyCapacityShowsSocketsOnDaysInRange(REPORT_START, REPLACEMENT_START.minusDays(1), 0.0);
    thenDailyCapacityShowsSocketsOnDaysInRange(REPLACEMENT_START, REPORT_END, SOCKETS_CAPACITY);
  }

  @TestPlanName("subscriptions-sync-TC006")
  @Test
  void shouldRetainPriorMonthCapacityWhenExpiredAndRenewalInIt() {
    Subscription expiredSubscription =
        givenExistingSubscription(EXPIRED_SEGMENT_START, EXPIRED_SEGMENT_END);
    Subscription replacementSubscription =
        givenReplacementSubscription(REPLACEMENT_START, REPORT_END);

    whenSubscriptionSyncRunsWithUpstream(expiredSubscription, replacementSubscription);

    thenSubscriptionIsPresent(expiredSubscription.getSubscriptionId());
    thenSubscriptionIsPresent(replacementSubscription.getSubscriptionId());
    thenDailyCapacityShowsSocketsOnDaysInRange(REPORT_START, EXPIRED_SEGMENT_END, SOCKETS_CAPACITY);
    thenDailyCapacityShowsSocketsOnDaysInRange(REPLACEMENT_START, REPORT_END, SOCKETS_CAPACITY);
  }

  @TestPlanName("subscriptions-sync-TC007")
  @Test
  void shouldDeleteSubscriptionWhenUpstreamStartDateIsNull() {
    Subscription subscription = givenExistingSubscription(ACTIVE_SUBSCRIPTION_START, REPORT_END);
    Subscription upstreamWithNullStart = subscription.toBuilder().startDate(null).build();

    whenSubscriptionSyncRunsWithUpstream(upstreamWithNullStart);

    thenSubscriptionIsAbsent(subscription.getSubscriptionId());
  }

  @TestPlanName("subscriptions-sync-TC008")
  @Test
  void shouldDeleteSubscriptionWhenUpstreamStartTooFarInFuture() {
    Subscription subscription = givenExistingSubscription(ACTIVE_SUBSCRIPTION_START, REPORT_END);
    // 61 days exceeds SUBSCRIPTION_IGNORE_STARTING_LATER_THAN (60d) threshold
    OffsetDateTime farFutureStart = clock.now().plusDays(61);
    Subscription upstreamFarFutureStart =
        subscription.toBuilder()
            .startDate(farFutureStart)
            .endDate(farFutureStart.plusYears(1))
            .build();

    whenSubscriptionSyncRunsWithUpstream(upstreamFarFutureStart);

    thenSubscriptionIsAbsent(subscription.getSubscriptionId());
  }

  private Subscription givenSubscription() {
    var subscription = Subscription.buildRhelSubscription(orgId, Map.of(SOCKETS, 1.0));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(subscription);
    wiremock.forProductAPI().stubOfferingData(subscription.getOffering());
    return subscription;
  }

  private Subscription givenExistingSubscription(OffsetDateTime startDate, OffsetDateTime endDate) {
    String seed = RandomUtils.generateRandom();
    String sku = RandomUtils.generateRandom();
    wiremock
        .forProductAPI()
        .stubOfferingData(Offering.buildRhelOffering(sku, 4.0, SOCKETS_CAPACITY));
    assertEquals(
        HttpStatus.SC_OK, service.syncOffering(sku).statusCode(), "Sync offering should succeed");

    Subscription subscription =
        Subscription.buildRhelSubscriptionUsingSku(orgId, Map.of(SOCKETS, SOCKETS_CAPACITY), sku)
            .toBuilder()
            .subscriptionId(seed)
            .subscriptionNumber(seed)
            .startDate(startDate)
            .endDate(endDate)
            .build();
    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, subscription).statusCode(),
        "Creating subscription should succeed");
    return subscription;
  }

  private Subscription givenReplacementSubscription(
      OffsetDateTime replacementStartDate, OffsetDateTime replacementEnd) {
    String seed = RandomUtils.generateRandom();
    String replacementSku = RandomUtils.generateRandom();
    wiremock
        .forProductAPI()
        .stubOfferingData(Offering.buildRhelOffering(replacementSku, 4.0, SOCKETS_CAPACITY));
    assertEquals(
        HttpStatus.SC_OK,
        service.syncOffering(replacementSku).statusCode(),
        "Sync replacement offering should succeed");

    return Subscription.buildRhelSubscriptionUsingSku(
            orgId, Map.of(SOCKETS, SOCKETS_CAPACITY), replacementSku)
        .toBuilder()
        .subscriptionId(seed)
        .subscriptionNumber(seed)
        .startDate(replacementStartDate)
        .endDate(replacementEnd)
        .build();
  }

  private void whenSubscriptionSyncRunsForOrg() {
    kafkaBridge.produceKafkaMessage(
        SUBSCRIPTION_SYNC_TASK, new EnabledOrgsResponse().withOrgId(orgId));
  }

  private void whenSubscriptionSyncRunsWithUpstream(Subscription... upstreamSubscriptions) {
    wiremock.forSearchApi().stubSearchSubscriptionsByOrgId(orgId, upstreamSubscriptions);
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(upstreamSubscriptions);
    whenSubscriptionSyncRunsForOrg();
  }

  private CapacityReportByMetricId whenDailyCapacityReport() {
    return service.getCapacityReportByMetricId(
        Product.RHEL,
        orgId,
        SOCKETS.toString(),
        REPORT_START,
        REPORT_END,
        GranularityType.DAILY,
        null);
  }

  private void thenSubscriptionIsPresent(String subscriptionId) {
    AwaitilityUtils.untilIsTrue(
        () ->
            service.getSubscriptionsByOrgId(orgId).stream()
                .anyMatch(sub -> subscriptionId.equals(sub.getSubscriptionId())),
        AwaitilitySettings.defaults()
            .withService(service)
            .timeoutMessage("Subscription " + subscriptionId + " should remain in DB"));
  }

  private void thenSubscriptionIsAbsent(String subscriptionId) {
    AwaitilityUtils.untilIsTrue(
        () ->
            service.getSubscriptionsByOrgId(orgId).stream()
                .noneMatch(sub -> subscriptionId.equals(sub.getSubscriptionId())),
        AwaitilitySettings.defaults()
            .withService(service)
            .timeoutMessage("Subscription " + subscriptionId + " should be removed from DB"));
  }

  private void thenDailyCapacityShowsSocketsOnDaysInRange(
      OffsetDateTime inclusiveFrom, OffsetDateTime inclusiveTo, double expectedSockets) {

    AwaitilityUtils.untilIsTrue(
        () -> {
          CapacityReportByMetricId report = whenDailyCapacityReport();
          return allDaysInRangeMatchCapacity(report, inclusiveFrom, inclusiveTo, expectedSockets);
        },
        AwaitilitySettings.defaults()
            .withService(service)
            .timeoutMessage(
                "Capacity API daily report should show "
                    + expectedSockets
                    + " sockets from "
                    + inclusiveFrom
                    + " through "
                    + inclusiveTo));
    assertDailyCapacitySocketsInDateRange(
        whenDailyCapacityReport(), inclusiveFrom, inclusiveTo, expectedSockets);
  }

  private boolean allDaysInRangeMatchCapacity(
      CapacityReportByMetricId report,
      OffsetDateTime inclusiveFrom,
      OffsetDateTime inclusiveTo,
      double expectedSockets) {
    for (OffsetDateTime day = inclusiveFrom.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        !toLocalDate(day).isAfter(toLocalDate(inclusiveTo));
        day = day.plusDays(1)) {
      if (Math.abs(socketsOnDay(report, day) - expectedSockets) >= 0.01) {
        return false;
      }
    }
    return true;
  }

  private void assertDailyCapacitySocketsInDateRange(
      CapacityReportByMetricId report,
      OffsetDateTime inclusiveFrom,
      OffsetDateTime inclusiveTo,
      double expectedSockets) {
    assertNotNull(report.getData(), "Capacity report data must not be null");
    for (OffsetDateTime day = inclusiveFrom.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        !toLocalDate(day).isAfter(toLocalDate(inclusiveTo));
        day = day.plusDays(1)) {
      double actual = socketsOnDay(report, day);
      if (expectedSockets < 0.01) {
        assertTrue(
            actual < 0.01,
            "Capacity on "
                + day
                + " should have zero sockets in "
                + inclusiveFrom
                + "–"
                + inclusiveTo);
      } else {
        assertEquals(
            expectedSockets,
            actual,
            0.01,
            "Capacity on " + day + " should report expected socket capacity");
      }
    }
  }

  private double socketsOnDay(CapacityReportByMetricId report, OffsetDateTime day) {
    LocalDate targetDay = toLocalDate(day);
    return report.getData().stream()
        .filter(snapshot -> isSnapshotDateOnDay(snapshot.getDate(), targetDay))
        .map(this::snapshotSocketsValue)
        .findFirst()
        .orElse(0.0);
  }

  private LocalDate toLocalDate(OffsetDateTime dateTime) {
    return dateTime.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
  }

  private boolean isSnapshotDateOnDay(OffsetDateTime snapshotDate, LocalDate day) {
    return snapshotDate.atZoneSameInstant(ZoneOffset.UTC).toLocalDate().equals(day);
  }

  private double snapshotSocketsValue(CapacitySnapshotByMetricId snapshot) {
    if (!snapshot.getHasData()) {
      return 0.0;
    }
    return snapshot.getValue().doubleValue();
  }
}
