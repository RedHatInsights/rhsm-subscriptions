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
import static org.junit.jupiter.api.Assertions.assertTrue;

import api.PartnerContractKafkaSender;
import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.contract.test.model.PartnerEntitlementContract;
import com.redhat.swatch.contract.test.model.PartnerEntitlementContractCloudIdentifiers;
import domain.BillingProvider;
import domain.Contract;
import io.restassured.response.Response;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class PartnerGatewayKafkaComponentTest extends BaseContractComponentTest {

  private static final double DEFAULT_CAPACITY = 10.0;
  private static final double INSTANCE_HOURS_CAPACITY = 18.0;

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

  @TestPlanName("partner-gateway-kafka-TC001")
  @Test
  void shouldProcessValidAwsContractViaKafka() {
    Contract contract =
        givenContractCreatedViaKafka(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));

    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);
    assertEquals(
        contract.getBillingAccountId(),
        actual.getBillingAccountId(),
        "billing_account_id should match awsCustomerAccountId");
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("partner-gateway-kafka-TC002")
  @Test
  void shouldProcessValidAzureContractViaKafka() {
    Contract contract =
        givenContractCreatedViaKafka(BillingProvider.AZURE, Map.of(CORES, DEFAULT_CAPACITY));

    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAzureBillingProviderId(contract, actual);
    assertEquals(
        contract.getBillingAccountId(),
        actual.getBillingAccountId(),
        "billing_account_id should match azureTenantId");
    assertEquals(1, actual.getMetrics().size(), "Should have exactly 1 metric (Cores)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
  }

  @TestPlanName("partner-gateway-kafka-TC003")
  @Test
  void shouldProcessPurePaygAwsContractViaKafka() {
    Contract contract =
        givenContractCreatedViaKafka(BillingProvider.AWS, Map.of(SOCKETS, DEFAULT_CAPACITY));

    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);
    assertEquals(0, actual.getMetrics().size(), "Pure PAYG contract should have 0 metrics");
  }

  @TestPlanName("partner-gateway-kafka-TC004")
  @Test
  void shouldProcessContractWithMultipleValidMetricsViaKafka() {
    Contract contract =
        givenContractCreatedViaKafka(
            BillingProvider.AWS,
            Map.of(CORES, DEFAULT_CAPACITY, INSTANCE_HOURS, INSTANCE_HOURS_CAPACITY));

    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    verifyAwsBillingProviderId(contract, actual);
    assertEquals(2, actual.getMetrics().size(), "Should have 2 metrics (Cores and Instance-hours)");
    verifyMetric(actual, contract.getProduct().getMetric(CORES), DEFAULT_CAPACITY);
    verifyMetric(actual, contract.getProduct().getMetric(INSTANCE_HOURS), INSTANCE_HOURS_CAPACITY);
  }

  @TestPlanName("partner-gateway-kafka-TC005")
  @Test
  void shouldHandleMalformedJsonInKafkaMessage() {
    kafkaSender().sendRaw("not-a-json-object");

    service.logs().assertContains("Unable to read IT Partner Kafka message from JSON");
    assertTrue(service.isRunning(), "Service health checks should all be UP");

    thenKafkaConsumerAcceptsSubsequentMessages();
  }

  @TestPlanName("partner-gateway-kafka-TC006")
  @Test
  void shouldNotPersistContractWhenSearchApiReturnsNoSubscription() {
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubSearchApiNotFound(contract.getSubscriptionNumber());

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    kafkaSender().send(contract);

    thenNoContractCreated();
  }

  @TestPlanName("partner-gateway-kafka-TC007")
  @Test
  void shouldNotPersistContractWhenPartnerApiIsUnavailable() {
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptionsServerError(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    kafkaSender().send(contract);

    thenNoContractCreated();
    assertTrue(service.isRunning(), "Service health checks should all be UP");
  }

  @TestPlanName("partner-gateway-kafka-TC008")
  @Test
  void shouldHandleDuplicateKafkaMessages() {
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    kafkaSender().send(contract);
    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));

    kafkaSender().send(contract);

    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                1,
                service.getContractsByOrgId(orgId).size(),
                "Duplicate message should not create a second contract"));
  }

  @TestPlanName("partner-gateway-kafka-TC009")
  @Test
  void shouldIgnoreKafkaMessageWhenKafkaConsumerDisabledViaVariant() {
    unleash.disablePartnerGatewayContractsKafkaConsumer();

    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);
    service.syncOffering(contract.getOffering().getSku());

    kafkaSender().send(contract);

    service
        .logs()
        .assertContains("IT Partner Kafka consumer for contracts is disabled by feature flag.");
    thenNoContractCreated();
  }

  @TestPlanName("partner-gateway-kafka-TC010")
  @Test
  void shouldProcessKafkaMessageWhenUmbConsumerDisabledViaVariant() {
    unleash.enablePartnerGatewayContractsKafkaOnly();

    Contract contract =
        givenContractCreatedViaKafka(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));

    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    assertEquals("aws", actual.getBillingProvider());
  }

  @TestPlanName("partner-gateway-kafka-TC011")
  @Test
  void shouldProcessKafkaMessageWhenBothConsumersEnabledViaVariant() {
    unleash.enablePartnerGatewayContractsBothConsumers();

    Contract contract =
        givenContractCreatedViaKafka(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));

    var actual = service.getContracts(contract).get(0);
    verifyCommonContractFields(contract, actual);
    assertEquals("aws", actual.getBillingProvider());
  }

  @TestPlanName("partner-gateway-kafka-TC012")
  @Test
  void shouldRejectKafkaMessageWithMissingRequiredFields() {
    PartnerEntitlementContract emptyMessage = new PartnerEntitlementContract();
    emptyMessage.setAction("contract-updated");

    kafkaSender().sendRaw(emptyMessage);

    service.logs().assertContains("Can't process the partner contract");
    thenNoContractCreated();

    thenKafkaConsumerAcceptsSubsequentMessages();
  }

  @TestPlanName("partner-gateway-kafka-TC013")
  @Test
  void shouldProcessKafkaMessageWithNullOptionalFields() {
    Contract contract =
        buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY)).toBuilder()
            .sellerAccountId(null)
            .build();

    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    kafkaSender().send(contract);

    AwaitilityUtils.until(() -> service.getContractsByOrgId(orgId).size(), is(1));
    var actual = service.getContractsByOrgId(orgId).get(0);
    assertNotNull(actual.getUuid(), "Contract should be created");
    assertEquals(orgId, actual.getOrgId());
    verifyAwsBillingProviderId(contract, actual);
  }

  @TestPlanName("partner-gateway-kafka-TC014")
  @Test
  void shouldHandleUnknownSourceValueInKafkaMessage() {
    PartnerEntitlementContract message = new PartnerEntitlementContract();
    message.setAction("contract-updated");
    var cloudIdentifiers = new PartnerEntitlementContractCloudIdentifiers();
    cloudIdentifiers.setPartner("gcp");
    message.setCloudIdentifiers(cloudIdentifiers);

    kafkaSender().sendRaw(message);

    service.logs().assertContains("Can't process the partner contract");
    thenNoContractCreated();
    assertTrue(service.isRunning(), "Service health checks should all be UP");

    thenKafkaConsumerAcceptsSubsequentMessages();
  }

  private Contract givenContractCreatedViaKafka(
      BillingProvider provider, Map<MetricId, Double> metrics) {
    Contract contract = buildRosaContract(orgId, provider, metrics);
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    wiremock.forSearchApi().stubGetSubscriptionBySubscriptionNumber(contract);

    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    kafkaSender().send(contract);

    AwaitilityUtils.until(() -> service.getContracts(contract).size(), is(1));
    return contract;
  }

  private void thenNoContractCreated() {
    AwaitilityUtils.untilAsserted(
        () ->
            assertEquals(
                0, service.getContractsByOrgId(orgId).size(), "No contract should be created"));
  }

  private void thenKafkaConsumerAcceptsSubsequentMessages() {
    Contract contract =
        givenContractCreatedViaKafka(BillingProvider.AWS, Map.of(CORES, DEFAULT_CAPACITY));
    assertNotNull(
        service.getContracts(contract).get(0).getUuid(),
        "Consumer should accept subsequent valid messages");
  }

  private PartnerContractKafkaSender kafkaSender() {
    return new PartnerContractKafkaSender(kafkaBridge);
  }
}
