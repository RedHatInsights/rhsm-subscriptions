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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class ContractsComponentTest extends BaseContractComponentTest {

  /** Verify prepaid contract is created when all required data is valid. */
  @Test
  @Tag("contract")
  void shouldCreatePrepaidRosaContract_whenAllDataIsValid() {
    // The metric Cores is valid for the rosa product
    Contract contractData = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    Response getContractsResponse = service.getContracts(contractData);
    assertThat(
        "Contract retrieval call should succeed", getContractsResponse.statusCode(), is(200));

    getContractsResponse
        .then()
        .body("size()", equalTo(1))
        .body("[0].org_id", equalTo(orgId))
        .body("[0].subscription_number", equalTo(contractData.getSubscriptionNumber()))
        .body("[0].billing_account_id", equalTo(contractData.getBillingAccountId()))
        .body("[0].sku", equalTo(contractData.getOffering().getSku()))
        .body("[0].metrics", notNullValue())
        .body("[0].metrics.size()", greaterThan(0));
  }

  /** Verify pure pay-as-you-go ROSA contract is created when all dimensions are incorrect. */
  @Test
  @Tag("contract")
  void shouldCreatePurePaygRosaContract_whenAllDimensionsAreIncorrect() {
    // The metric Instance-hours is NOT valid for the rosa product, so it should be ignored
    Contract contractData =
        buildRosaContract(
            orgId, BillingProvider.AWS, Map.of(MetricIdUtils.getInstanceHours(), 10.0));
    givenContractIsCreated(contractData);

    // Retrieve and verify contract
    Response getContractsResponse = service.getContracts(contractData);
    assertThat(
        "Contract retrieval call should succeed", getContractsResponse.statusCode(), is(200));

    // Having metrics size as zero is what is indicating that this is pure paygo because there are
    // no valid prepaid metric amounts
    getContractsResponse
        .then()
        .body("size()", equalTo(1))
        .body("[0].org_id", equalTo(orgId))
        .body("[0].subscription_number", equalTo(contractData.getSubscriptionNumber()))
        .body("[0].billing_account_id", equalTo(contractData.getBillingAccountId()))
        .body("[0].sku", equalTo(contractData.getOffering().getSku()))
        .body("[0].metrics.size()", equalTo(0));
  }
}
