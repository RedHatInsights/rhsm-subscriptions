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

import static domain.Contract.buildRosaContract;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Contract;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ContractCreationComponentTest extends BaseContractComponentTest {

  @Test
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    // The metric Cores is valid for the rosa product
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    var getContractsResponse = service.getContracts(contractData);

    assertEquals(1, getContractsResponse.size());
    var actualContract = getContractsResponse.get(0);
    assertEquals(orgId, actualContract.getOrgId());
    assertEquals(contractData.getSubscriptionNumber(), actualContract.getSubscriptionNumber());
    assertEquals(contractData.getBillingAccountId(), actualContract.getBillingAccountId());
    assertEquals(contractData.getOffering().getSku(), actualContract.getSku());
    assertNotNull(actualContract.getMetrics());
    assertEquals(1, actualContract.getMetrics().size());
  }

  /** Verify pure pay-as-you-go ROSA contract is created when all dimensions are incorrect. */
  @Test
  void shouldCreatePurePaygRosaContract_whenAllDimensionsAreIncorrect() {
    // The metric Instance-hours is NOT valid for the rosa product, so it should be ignored
    Contract contractData =
        buildRosaContract(
            orgId, BillingProvider.AWS, Map.of(MetricIdUtils.getInstanceHours(), 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    var getContractsResponse = service.getContracts(contractData);

    // Having metrics size as zero is what is indicating that this is pure paygo because there are
    // no valid prepaid metric amounts
    assertEquals(1, getContractsResponse.size());
    var actualContract = getContractsResponse.get(0);
    assertEquals(orgId, actualContract.getOrgId());
    assertEquals(contractData.getSubscriptionNumber(), actualContract.getSubscriptionNumber());
    assertEquals(contractData.getBillingAccountId(), actualContract.getBillingAccountId());
    assertEquals(contractData.getOffering().getSku(), actualContract.getSku());
    assertNotNull(actualContract.getMetrics());
    assertEquals(0, actualContract.getMetrics().size());
  }
}
