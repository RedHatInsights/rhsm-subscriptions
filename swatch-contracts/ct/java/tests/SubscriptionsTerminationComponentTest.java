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
import static utils.DateUtils.assertDatesAreEqual;

import com.redhat.swatch.component.tests.api.TestPlanName;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class SubscriptionsTerminationComponentTest extends BaseContractComponentTest {

  @TestPlanName("subscriptions-termination-TC001")
  @Test
  void shouldTerminateSubscriptionWithTimestamp() {
    Subscription subscription = givenActiveSubscription();
    OffsetDateTime terminationDate =
        OffsetDateTime.now().plusDays(1).withHour(23).withMinute(59).withSecond(59).withNano(0);

    Response response = service.terminateSubscription(subscription, terminationDate);
    assertEquals(HttpStatus.SC_OK, response.statusCode());

    service
        .getSubscriptionsByOrgId(orgId)
        .forEach(s -> assertDatesAreEqual(terminationDate, s.getEndDate()));
  }

  private Subscription givenActiveSubscription() {
    var subscription = Subscription.buildRhelSubscription(orgId, Map.of(SOCKETS, 1.0));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(subscription);
    wiremock.forProductAPI().stubOfferingData(subscription.getOffering());
    service.syncUmbSubscription(subscription);
    return subscription;
  }
}
