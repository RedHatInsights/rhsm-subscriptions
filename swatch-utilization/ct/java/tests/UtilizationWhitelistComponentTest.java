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
import static com.redhat.swatch.component.tests.utils.Topics.NOTIFICATIONS;
import static com.redhat.swatch.component.tests.utils.Topics.UTILIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.utilization.test.model.Measurement;
import com.redhat.swatch.utilization.test.model.UtilizationSummary;
import com.redhat.swatch.utilization.test.model.UtilizationSummary.Granularity;
import domain.Product;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UtilizationWhitelistComponentTest extends BaseUtilizationComponentTest {

  private static final double BASELINE_CAPACITY = 100.0;
  private static final double USAGE_EXCEEDING_THRESHOLD = 110.0;

  @BeforeAll
  static void disableGlobalNotificationsFlag() {
    unleash.disableSendNotificationsFlag();
  }

  @AfterAll
  static void clearNotificationsWhitelistFlag() {
    unleash.disableSendNotificationsFlagForOrgs();
  }

  @Test
  @TestPlanName("utilization-whitelist-TC001")
  void shouldSendNotification_whenOrgIsWhitelistedAndGlobalFlagDisabled() {
    givenOrgIsWhitelisted(orgId);

    // When
    whenOverUsageEventIsSent();

    // Then
    thenNotificationShouldBeSent();
  }

  @Test
  @TestPlanName("utilization-whitelist-TC002")
  void shouldNotSendNotification_whenOrgIsNotWhitelistedAndGlobalFlagDisabled() {
    // Given
    givenOrgIsWhitelisted("some-other-org-id");

    // When
    whenOverUsageEventIsSent();

    // Then
    thenNotificationIsNotSent();
  }

  @Test
  @TestPlanName("utilization-whitelist-TC003")
  void shouldNotSendNotification_whenWhitelistIsEmptyAndGlobalFlagDisabled() {
    // Given
    givenWhitelistFlagEnabledWithEmptyPayload();

    // When
    whenOverUsageEventIsSent();

    // Then
    thenNotificationIsNotSent();
  }

  private void givenOrgIsWhitelisted(String whitelistedOrgId) {
    unleash.enableSendNotificationsFlagForOrgs(whitelistedOrgId);
  }

  private void givenWhitelistFlagEnabledWithEmptyPayload() {
    unleash.enableSendNotificationsFlagForOrgs(" ");
  }

  private void whenOverUsageEventIsSent() {
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
                            .withCurrentTotal(USAGE_EXCEEDING_THRESHOLD)
                            .withCapacity(BASELINE_CAPACITY)
                            .withUnlimited(false))));
    kafkaBridge.produceKafkaMessage(UTILIZATION, summary);
  }

  private void thenNotificationShouldBeSent() {
    Action notification = kafkaBridge.waitForKafkaMessage(NOTIFICATIONS, matchesOrgId(orgId));
    assertThat("Notification should be sent for whitelisted org", notification, notNullValue());
    assertThat(
        "Context should contain correct product_id",
        notification.getContext().getAdditionalProperties().get("product_id"),
        equalTo(Product.RHEL.getName()));
  }

  private void thenNotificationIsNotSent() {
    service.logs().assertContains("Notification not sent for orgId=" + orgId);
  }
}
