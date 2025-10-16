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
package api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.SwatchService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class ContractsSwatchService extends SwatchService {
  private static final Duration ONE_DAY = Duration.ofDays(1);
  private static final Duration ONE_YEAR = Duration.ofDays(365);

  public void givenSubscription(String orgId, String sku) {
    String subscriptionJson = createSubscriptionJson(orgId, sku);
    saveSubscriptions(subscriptionJson);
  }

  public void syncOffering(String sku) {
    given()
        .when()
        .put("/api/swatch-contracts/internal/rpc/offerings/sync/" + sku)
        .then()
        .statusCode(200);
  }

  public void saveSubscriptions(String subscriptionJson) {
    var response =
        given()
            .header("Content-Type", "application/json")
            .body(subscriptionJson)
            .when()
            .post("/api/swatch-contracts/internal/subscriptions?reconcileCapacity=true")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // Verify subscription was created successfully
    assertTrue(response.getBody().asString().contains("Success"));
  }

  private String createSubscriptionJson(String orgId, String sku) {
    Instant now = Instant.now();
    long startDate = now.minus(ONE_DAY).toEpochMilli();
    long endDate = now.plus(ONE_YEAR).toEpochMilli();

    return String.format(
        """
        [{
          "id": %d,
          "subscriptionNumber": "SUB-%s",
          "subscriptionProducts": [{"sku": "%s"}],
          "webCustomerId": %d,
          "quantity": 1,
          "effectiveStartDate": %d,
          "effectiveEndDate": %d
        }]
        """,
        Math.abs(UUID.randomUUID().hashCode()),
        UUID.randomUUID().toString().substring(0, 8),
        sku,
        Math.abs(orgId.hashCode()),
        startDate,
        endDate);
  }
}
