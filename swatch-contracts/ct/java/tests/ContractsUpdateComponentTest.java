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

import static api.PartnerApiStubs.PartnerSubscriptionsStubRequest.forContract;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import api.ContractsArtemisService;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.RunOnOpenShift;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@RunOnOpenShift
public class ContractsUpdateComponentTest extends BaseContractComponentTest {

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @TestPlanName("contracts-update-TC001")
  @Test
  void shouldUpdateExistingContractWhenReceivingAnUpdateEvent() {
    // given: An initial contract is created via UMB message
    Contract initialContract = givenContractCreatedViaMessageBroker();

    // update the end date for the contract
    Contract updatedContract =
        initialContract.toBuilder().endDate(OffsetDateTime.now().plusDays(30)).build();
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(updatedContract));
    artemis.forContracts().send(updatedContract);

    // then: The existing contract should be updated with the new end date
    service.logs().assertContains("Existing contracts and subscriptions updated");
    var contracts = service.getContracts(initialContract);
    Assertions.assertEquals(1, contracts.size());
    Assertions.assertNotNull(contracts.get(0).getEndDate());
    Assertions.assertTrue(contracts.get(0).getEndDate().isAfter(initialContract.getEndDate()));
  }

  @TestPlanName("contracts-update-TC002")
  @Test
  void shouldProcessRedundantContractMessage() {
    // given: An initial contract is created via UMB message
    Contract contract = givenContractCreatedViaMessageBroker();

    // update send the same message again
    artemis.forContracts().send(contract);

    // then message is ignored as redundant
    service.logs().assertContains("Redundant message ignored");
    var contracts = service.getContracts(contract);
    Assertions.assertEquals(1, contracts.size());
  }

  private Contract givenContractCreatedViaMessageBroker() {
    Contract contract = Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    // Send the contract via Message Broker (Artemis)
    artemis.forContracts().send(contract);

    // Wait for the contract to be processed
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));
    return contract;
  }
}
