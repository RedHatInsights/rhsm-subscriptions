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
import static domain.Contract.buildRosaContract;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.ContractsArtemisService;
import api.PartnerContractKafkaSender;
import com.redhat.swatch.component.tests.api.Artemis;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PartnerGatewayUmbComponentTest extends BaseContractComponentTest {

  private static final double DEFAULT_CAPACITY = 10.0;

  @Artemis static ContractsArtemisService artemis = new ContractsArtemisService();

  @BeforeAll
  static void enablePartnerGatewayContractsFeatureFlag() {
    unleash.enablePartnerGatewayContracts();
  }

  @AfterAll
  static void disablePartnerGatewayContractsFeatureFlag() {
    unleash.disablePartnerGatewayContracts();
  }

  @AfterEach
  void restorePartnerGatewayContractsFlag() {
    unleash.enablePartnerGatewayContracts();
    unleash.clearPartnerGatewayContractsVariants();
  }

  @TestPlanName("partner-gateway-umb-TC001")
  @Test
  void shouldIgnoreUmbMessageWhenFeatureFlagDisabled() {
    unleash.disablePartnerGatewayContracts();

    Contract contract = givenContractSetup(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    artemis.forContracts().sendAsText(contract);

    service
        .logs()
        .assertContains("IT Partner UMB consumer for contracts is disabled by feature flag.");
    thenNoContractCreated();
  }

  @TestPlanName("partner-gateway-umb-TC002")
  @Test
  void shouldIgnoreUmbMessageWhenUmbConsumerDisabledViaVariant() {
    unleash.enablePartnerGatewayContractsKafkaOnly();

    Contract contract = givenContractSetup(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    artemis.forContracts().sendAsText(contract);

    service
        .logs()
        .assertContains("IT Partner UMB consumer for contracts is disabled by feature flag.");
    thenNoContractCreated();
  }

  @TestPlanName("partner-gateway-umb-TC003")
  @Test
  void shouldProcessUmbMessageWhenKafkaConsumerDisabledViaVariant() {
    unleash.enablePartnerGatewayContractsUmbOnly();

    Contract contract = givenContractSetup(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    artemis.forContracts().sendAsText(contract);

    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));
    var actual = service.getContracts(contract).get(0);
    assertNotNull(actual.getUuid());
    assertEquals(orgId, actual.getOrgId());
    assertEquals("aws", actual.getBillingProvider());
    assertEquals(contract.getOffering().getSku(), actual.getSku());
  }

  @TestPlanName("partner-gateway-umb-TC004")
  @Test
  void shouldProcessCrossConsumerDuplicateAsIdempotent() {
    unleash.enablePartnerGatewayContractsBothConsumers();

    Contract contract = givenContractSetup(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));

    new PartnerContractKafkaSender(kafkaBridge).send(contract);
    AwaitilityUtils.until(() -> service.getContractsByOrgId(orgId).size(), is(1));

    artemis.forContracts().sendAsText(contract);

    service
        .logs()
        .assertContains(
            "IT Partner message consumed: source=umb",
            "redHatSubscriptionNumber=" + contract.getSubscriptionNumber());

    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                1,
                service.getContractsByOrgId(orgId).size(),
                "Cross-consumer duplicate should not create a second contract"));
  }

  private Contract givenContractSetup(BillingProvider provider, Map<MetricId, Double> metrics) {
    Contract contract = buildRosaContract(orgId, provider, metrics);
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    return contract;
  }

  private void thenNoContractCreated() {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0, service.getContractsByOrgId(orgId).size(), "No contract should be created"));
  }
}
