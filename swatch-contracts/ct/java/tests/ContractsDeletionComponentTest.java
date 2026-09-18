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

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.SubscriptionDeleteReason;
import domain.BillingProvider;
import domain.Contract;
import domain.Product;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;

public class ContractsDeletionComponentTest extends BaseContractComponentTest {

  private static final double ROSA_CORES_CAPACITY = 8.0;

  @TestPlanName("contracts-deletion-TC001")
  @Test
  void shouldDeleteContractByUUID() {
    // Given: A contract is created
    String sku = RandomUtils.generateRandom();
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0), sku);
    givenContractIsCreated(contract);
    String contractUuid = getContractUuid(orgId);

    // When: Contract is deleted by UUID
    whenContractIsDeleted(contractUuid);

    // Then: Contract should no longer exist
    thenContractShouldNotExist(orgId);
    thenSubscriptionAuditDeleteLog(SubscriptionDeleteReason.CONTRACT_DELETED);
  }

  @TestPlanName("contracts-deletion-TC002")
  @Test
  void shouldHandleDeleteNonExistentContract() {
    // Given: A random UUID that doesn't exist in the database
    String nonExistentUuid = UUID.randomUUID().toString();

    // When: Contract deletion is attempted with invalid UUID
    Response deleteResponse = whenContractIsDeleted(nonExistentUuid);

    // Then: Verify graceful idempotent handling
    thenDeleteShouldBeIdempotent(deleteResponse);
  }

  @TestPlanName("contracts-termination-TC003")
  @Test
  void shouldDoubleThenZeroCapacityWhenAddAndDeleteContracts() {
    // Given: An active ROSA contract exists with capacity matching its value
    String sku = RandomUtils.generateRandom();
    Contract firstContract =
        Contract.buildRosaContract(
            orgId, BillingProvider.AWS, Map.of(CORES, ROSA_CORES_CAPACITY), sku);
    givenContractIsCreated(firstContract);
    thenRosaCoresCapacityEquals(ROSA_CORES_CAPACITY);

    // When: A second identical contract is added (same SKU and capacity)
    Contract secondContract =
        Contract.buildRosaContract(
            orgId, BillingProvider.AWS, Map.of(CORES, ROSA_CORES_CAPACITY), sku);
    givenOfferingIsSynced(secondContract.getOffering());
    whenContractIsCreatedViaApi(secondContract);

    // Then: Capacity doubles
    thenRosaCoresCapacityEquals(ROSA_CORES_CAPACITY * 2);

    // When: the two contracts are deleted
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(2, contracts.size(), "Should have exactly two contracts");
    for (var contract : contracts) {
      whenContractIsDeleted(contract.getUuid());
    }

    // Then: Capacity returns to zero
    thenRosaCoresCapacityEquals(0.0);
  }

  private void thenDeleteShouldBeIdempotent(Response deleteResponse) {
    assertEquals(
        HttpStatus.SC_NO_CONTENT,
        deleteResponse.statusCode(),
        "Delete non-existent contract should return 204 No Content (idempotent behavior)");
  }

  private void thenRosaCoresCapacityEquals(double expectedCapacity) {
    OffsetDateTime beginning = clock.now().minusDays(1);
    OffsetDateTime ending = clock.now().plusDays(1);

    AwaitilityUtils.until(
        () ->
            getCapacityValueFromReport(
                service.getCapacityReportByMetricId(
                    Product.ROSA,
                    orgId,
                    CORES.toString(),
                    beginning,
                    ending,
                    GranularityType.DAILY,
                    null)),
        capacity -> Math.abs(capacity - expectedCapacity) < 0.01);
  }
}
