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

import static api.ContractsUnleashService.ENABLE_READ_ONLY;
import static api.PartnerApiStubs.PartnerSubscriptionsStubRequest.forContract;
import static domain.Contract.buildRosaContract;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.model.InfoFeatureFlag;
import com.redhat.swatch.component.tests.model.InfoFeatureFlags;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ReadOnlyModeComponentTest extends BaseContractComponentTest {

  private static final String CONTRACTS_CHANNEL = "contracts-from-gateway";
  private static final String PAUSING_CONSUMER_LOG =
      "Pausing consumer '%s'".formatted(CONTRACTS_CHANNEL);
  private static final String RESUMING_CONSUMER_LOG =
      "Resuming consumer '%s'".formatted(CONTRACTS_CHANNEL);

  @AfterEach
  void resetReadOnlyFlag() {
    unleash.disableReadOnly();
  }

  @TestPlanName("read-only-TC001")
  @Test
  void shouldPauseAndResumeConsumerWithFeatureFlag() {
    // By default, the channel is consuming, so we can create contracts
    Contract first = whenContractCreatedViaKafka();
    thenContractIsCreated(first);

    // Then, we enable flag and wait for the service to pause the consumer
    unleash.enableReadOnly();
    service.logs().assertContains(PAUSING_CONSUMER_LOG);

    // And we confirm that the channel is NOT consuming messages
    Contract second = whenContractCreatedViaKafka();
    thenContractIsNotCreated(second);

    // And we disable the flag again and wait for the service to resume the consumer
    unleash.disableReadOnly();
    service.logs().assertContains(RESUMING_CONSUMER_LOG);

    // Then the pending message is processed after resume
    thenContractIsCreated(second);
  }

  @TestPlanName("read-only-TC002")
  @Test
  void shouldExposeReadOnlyFlagOnInfoEndpoint() {
    // Given / When
    unleash.enableReadOnly();

    // Then
    InfoFeatureFlags featureFlags =
        service
            .getFeatureFlags()
            .orElseThrow(() -> new AssertionError("Management /info must expose feature-flags"));
    InfoFeatureFlag flag =
        featureFlags.getFlags().stream()
            .filter(entry -> ENABLE_READ_ONLY.equals(entry.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Read-only feature flag missing from /info"));

    assertEquals(ENABLE_READ_ONLY, flag.getName());
    assertTrue(flag.getEnabled());
  }

  private Contract whenContractCreatedViaKafka() {
    Contract contract = buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, 10.0));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertEquals(HttpStatus.SC_OK, sync.statusCode(), "Sync offering should succeed");
    kafkaBridge.asOfPartnerGateway().send(contract);

    return contract;
  }

  private void thenContractIsNotCreated(Contract contract) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0,
                countMatchingContracts(contract),
                "Contract should not be created while consumer is paused"));
  }

  private void thenContractIsCreated(Contract contract) {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                1,
                countMatchingContracts(contract),
                "Contract should be created while consumer is not paused"));
  }

  private long countMatchingContracts(Contract contract) {
    return service.getContractsByOrgId(orgId).stream()
        .filter(c -> c.getBillingAccountId().equals(contract.getBillingAccountId()))
        .count();
  }
}
