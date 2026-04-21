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

import static api.MessageValidators.matchesOrgId;
import static api.MessageValidators.matchesOverageNotification;
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import com.redhat.swatch.utilization.test.model.UtilizationSummary.Granularity;
import domain.Product;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class UtilizationNotificationsFeatureFlagsComponentTest
    extends BaseUtilizationComponentTest {

  private static final String OVERUSAGE_EVENT_TYPE = "exceeded-utilization-threshold";
  private static final double BASELINE_CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0;

  @AfterEach
  void resetNotificationFeatureFlags() {
    unleash.clearSendNotificationsVariants();
    unleash.disableSendNotificationsFlagForOrgs();
    unleash.disableSendNotificationsFlag();
  }

  @Test
  @TestPlanName("utilization-notifications-featureflags-TC001")
  void shouldSendNotification_whenOrgAllowlistedAndGlobalFlagDisabled() {
    givenSendNotificationsGloballyDisabled();
    givenOrgOnNotificationsAllowlist(orgId);

    whenOverUsageRhelExceedsThreshold();

    thenRhelNotificationShouldBeSentForOrg();
  }

  @Test
  @TestPlanName("utilization-notifications-featureflags-TC002")
  void shouldNotSendNotification_whenOrgNotAllowlistedAndGlobalDisabled() {
    givenSendNotificationsGloballyDisabled();
    givenOrgOnNotificationsAllowlist("some-other-org-id");

    whenOverUsageRhelExceedsThreshold();

    thenNotificationIsNotSent();
  }

  @Test
  @TestPlanName("utilization-notifications-featureflags-TC003")
  void shouldNotSendNotification_whenAllowlistEmptyAndGlobalDisabled() {
    givenSendNotificationsGloballyDisabled();
    givenAllowlistFlagEnabledWithEmptyPayload();

    whenOverUsageRhelExceedsThreshold();

    thenNotificationIsNotSent();
  }

  @Test
  @TestPlanName("utilization-notifications-featureflags-TC004")
  void shouldSuppressNotification_whenOverusageEventTypeIsDenylisted() {
    givenSendNotificationsEnabledWithDenylistedEventTypes(OVERUSAGE_EVENT_TYPE);

    whenOverUsageRhelExceedsThreshold();

    thenNotificationIsNotSent();
  }

  @Test
  @TestPlanName("utilization-notifications-featureflags-TC005")
  void shouldSendNotification_whenDenylistOmitsOverusageEvent() {
    givenSendNotificationsEnabledWithDenylistedEventTypes("some-other-event-type");

    whenOverUsageRhelExceedsThreshold();

    thenRhelOverusageNotificationMatchesProductAndMetric();
  }

  @Test
  @TestPlanName("utilization-notifications-featureflags-TC006")
  void shouldDifferByAllowlist_whenDenylistedEventAndTwoOrgs() {
    var orgIdNotOnAllowlist = RandomUtils.generateRandom();

    givenSendNotificationsEnabledWithDenylistedEventTypes(OVERUSAGE_EVENT_TYPE);
    givenOrgOnNotificationsAllowlist(orgId);

    whenOverUsageRhelExceedsThresholdForOrg(orgId);
    thenRhelOverageNotificationForOrg(orgId);

    whenOverUsageRhelExceedsThresholdForOrg(orgIdNotOnAllowlist);
    thenNotificationIsNotSentForOrg(orgIdNotOnAllowlist);
  }

  private void givenAllowlistFlagEnabledWithEmptyPayload() {
    unleash.enableSendNotificationsFlagForOrgs(" ");
  }

  private void givenOrgOnNotificationsAllowlist(String allowlistedOrgId) {
    unleash.enableSendNotificationsFlagForOrgs(allowlistedOrgId);
  }

  private void givenSendNotificationsEnabledWithDenylistedEventTypes(String... deniedEventTypes) {
    unleash.enableSendNotificationsWithEventTypesDenylist(deniedEventTypes);
  }

  private void givenSendNotificationsGloballyDisabled() {
    unleash.disableSendNotificationsFlag();
  }

  private void whenOverUsageRhelExceedsThreshold() {
    whenOverUsageRhelExceedsThresholdForOrg(orgId);
  }

  private void whenOverUsageRhelExceedsThresholdForOrg(String orgId) {
    UtilizationSummary summary =
        new UtilizationSummary()
            .withOrgId(orgId)
            .withProductId(Product.RHEL.getName())
            .withGranularity(Granularity.DAILY)
            .withMeasurements(
                new ArrayList<>(
                    List.of(
                        new Measurement()
                            .withMetricId(Product.RHEL.getFirstMetric().getId())
                            .withValue(USAGE_EXCEEDING_THRESHOLD)
                            .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                            .withCapacity(BASELINE_CAPACITY)
                            .withUnlimited(false))));
    kafkaBridge.produceKafkaMessage(UTILIZATION, summary);
  }

  private void thenNotificationIsNotSent() {
    thenNotificationIsNotSentForOrg(orgId);
  }

  private void thenNotificationIsNotSentForOrg(String orgId) {
    service.logs().assertContains("Notification not sent for orgId=" + orgId);
  }

  private void thenRhelNotificationShouldBeSentForOrg() {
    Action notification = kafkaBridge.waitForKafkaMessage(NOTIFICATIONS, matchesOrgId(orgId));
    assertNotNull(notification, "Notification should be sent for allowlisted org");
    assertEquals(
        Product.RHEL.getName(),
        notification.getContext().getAdditionalProperties().get("product_id"),
        "Context should contain correct product_id");
  }

  private void thenRhelOverageNotificationForOrg(String expectedOrgId) {
    Action notification =
        kafkaBridge.waitForKafkaMessage(
            NOTIFICATIONS,
            matchesOverageNotification(
                expectedOrgId, Product.RHEL.getName(), Product.RHEL.getFirstMetric().getId()));
    assertNotNull(notification, "Notification should be sent");
    assertEquals(
        Product.RHEL.getName(),
        notification.getContext().getAdditionalProperties().get("product_id"),
        "Context should contain correct product_id");
  }

  private void thenRhelOverusageNotificationMatchesProductAndMetric() {
    thenRhelOverageNotificationForOrg(orgId);
  }
}
