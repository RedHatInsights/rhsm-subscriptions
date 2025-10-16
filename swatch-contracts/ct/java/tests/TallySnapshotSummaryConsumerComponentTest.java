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

import static api.ContractsTestHelper.givenTallySnapshot;
import static api.ContractsTestHelper.givenTallySummary;
import static com.redhat.swatch.component.tests.utils.Topics.TALLY;
import static com.redhat.swatch.contract.model.TallySnapshot.Sla.PREMIUM;
import static com.redhat.swatch.contract.model.TallySnapshot.Usage.PRODUCTION;

import com.redhat.swatch.contract.model.TallySnapshot;
import com.redhat.swatch.contract.model.TallySnapshot.Sla;
import com.redhat.swatch.contract.model.TallySummary;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TallySnapshotSummaryConsumerComponentTest extends BaseContractComponentTest {

  private static final String ORG_ID = "org123";
  private static final String PRODUCT_ID = "rosa";
  private static final String SKU = "MCT4121HR";
  private static final String SERVICE_LEVEL = PREMIUM.name();
  private static final String USAGE = PRODUCTION.name();
  private static final boolean LIMITED_USAGE = false;
  private static final boolean UNLIMITED_USAGE = true;

  @Test
  void whenTallySummaryWithMatchingSubscriptionThenCapacityIsEnriched() {
    // Given subscription with capacity in database
    givenSubscription(ORG_ID, PRODUCT_ID);

    // Given tally summary with matching criteria
    TallySummary tallySummary = givenTallySummary(ORG_ID, PRODUCT_ID);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySummary);

    // Then capacity should be enriched and subscription found
    thenUtilizationMessageIsProduced();
  }

  @Test
  void whenTallySummaryWithNoMatchingSubscriptionThenSubscriptionNotFound() {
    // Given tally summary with criteria that don't match any subscription
    TallySummary tallySummary = givenTallySummary(ORG_ID, "NonExistentProduct");

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySummary);

    // Then should process but subscription not found
    thenUtilizationMessageIsProduced();
  }

  @Test
  void whenTallySummaryWithMultipleMatchingSubscriptionsThenCapacityIsAggregated() {
    // Given multiple subscriptions with capacity for same criteria
    givenMultipleSubscriptions(ORG_ID, PRODUCT_ID);

    // Given tally summary with matching criteria
    TallySummary tallySummary = givenTallySummary(ORG_ID, PRODUCT_ID);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySummary);

    // Then capacity should be aggregated
    thenUtilizationMessageIsProduced();
  }

  @Test
  void whenTallySummaryWithUnlimitedSubscriptionThenUnlimitedIsSet() {
    // Given subscription with unlimited usage
    givenSubscriptionUnlimited(ORG_ID, PRODUCT_ID);

    // Given tally summary with matching criteria
    TallySummary tallySummary = givenTallySummary(ORG_ID, PRODUCT_ID);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySummary);

    // Then unlimited usage should be detected
    thenUtilizationMessageIsProduced();
  }

  @Test
  void whenTallySummaryWithDifferentServiceLevelThenNoMatch() {
    // Given subscription with Standard service level
    givenSubscription(ORG_ID, PRODUCT_ID, Sla.STANDARD);

    // Given tally summary with Premium service level (different)
    TallySummary tallySummary = givenTallySummary(ORG_ID, PRODUCT_ID, PREMIUM);

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySummary);

    // Then should not match due to service level difference
    thenUtilizationMessageIsProduced();
  }

  @Test
  void whenTallySummaryWithMultipleSnapshotsThenMultipleUtilizationSummaries() {
    // Given subscriptions for different products
    givenSubscription(ORG_ID, "RHEL");
    givenSubscription(ORG_ID, "rosa");

    // Given tally summary with multiple snapshots
    TallySummary tallySummary =
        new TallySummary()
            .withOrgId(ORG_ID)
            .withTallySnapshots(List.of(givenTallySnapshot("RHEL"), givenTallySnapshot("rosa")));

    // When tally summary is processed
    whenTallySummaryMessageIsSent(tallySummary);

    // Then should create utilization summary for each snapshot
    thenUtilizationMessageIsProduced();
    service.logs().assertContains("Created 2 utilization summaries from 1 tally messages");
  }

  private void givenSubscriptionUnlimited(String orgId, String productId) {
    wiremock.givenOfferingExists(SKU, productId, SERVICE_LEVEL, USAGE, UNLIMITED_USAGE);
    service.syncOffering(SKU);
    service.givenSubscription(orgId, SKU);
  }

  private void givenSubscription(String orgId, String productId) {
    wiremock.givenOfferingExists(SKU, productId, SERVICE_LEVEL, USAGE, LIMITED_USAGE);
    service.syncOffering(SKU);
    service.givenSubscription(orgId, SKU);
  }

  private void givenSubscription(String orgId, String productId, TallySnapshot.Sla sla) {
    wiremock.givenOfferingExists(SKU, productId, sla.toString(), USAGE, LIMITED_USAGE);
    service.syncOffering(SKU);
    service.givenSubscription(orgId, SKU);
  }

  private void givenMultipleSubscriptions(String orgId, String productId) {
    wiremock.givenOfferingExists(SKU, productId, SERVICE_LEVEL, USAGE, LIMITED_USAGE);
    service.syncOffering(SKU);

    // multiple subscriptions for same SKU
    service.givenSubscription(orgId, SKU);
    service.givenSubscription(orgId, SKU);
  }

  private void whenTallySummaryMessageIsSent(TallySummary message) {
    kafkaBridge.produceKafkaMessage(TALLY, message);
  }

  private void thenUtilizationMessageIsProduced() {
    // Wait for processing to complete and utilization message to be produced
    service.logs().assertContains("Created");
    service.logs().assertContains("utilization summaries from");
  }
}
