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
import static org.junit.jupiter.api.Assertions.assertEquals;

import api.ContractsSwatchService;
import api.ContractsUnleashService;
import api.ContractsWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Unleash;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.ReportCategory;
import domain.BillingProvider;
import domain.Contract;
import domain.Product;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-contracts")
public class BaseContractComponentTest {
  static final MetricId CORES = MetricIdUtils.getCores();
  static final MetricId SOCKETS = MetricIdUtils.getSockets();
  static final MetricId INSTANCE_HOURS = MetricIdUtils.getInstanceHours();
  static final String SUCCESS_MESSAGE = "SUCCESS";
  static final String EXISTING_CONTRACTS_SYNCED_MESSAGE =
      "Existing contracts and subscriptions updated";

  @KafkaBridge static KafkaBridgeService kafkaBridge = new KafkaBridgeService();

  @Wiremock static ContractsWiremockService wiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-contracts")
  static ContractsSwatchService service = new ContractsSwatchService();

  @Unleash static ContractsUnleashService unleash = new ContractsUnleashService();

  protected static final ApplicationClock clock = new ApplicationClock();

  protected String orgId;
  private List<String> orgIds = new ArrayList<>();

  @BeforeEach
  void setUp() {
    orgId = givenOrgId();
  }

  @AfterEach
  void tearDown() {
    for (String orgId : orgIds) {
      service.deleteDataForOrg(orgId);
    }
  }

  String givenOrgId() {
    return givenOrgId(RandomUtils.generateRandom());
  }

  String givenOrgIdWithSuffix(String suffix) {
    return givenOrgId(RandomUtils.generateRandom() + suffix);
  }

  String givenOrgId(String orgId) {
    orgIds.add(orgId);
    return orgId;
  }

  protected Contract givenRosaContractIsCreated(double coresCapacity) {
    return givenRosaContractIsCreated(RandomUtils.generateRandom(), coresCapacity);
  }

  protected Contract givenRosaContractIsCreated(String sku, double coresCapacity) {
    Contract contract =
        Contract.buildRosaContract(orgId, BillingProvider.AWS, Map.of(CORES, coresCapacity), sku);
    givenContractIsCreated(contract);
    return contract;
  }

  void givenContractIsCreated(Contract contract) {
    givenOfferingIsSynced(contract);
    Response create = service.createContract(contract);
    assertThat("Creating contract should succeed", create.statusCode(), is(HttpStatus.SC_OK));
  }

  protected void givenOfferingIsSynced(Contract contract) {
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertEquals(HttpStatus.SC_OK, sync.statusCode(), "Sync offering should succeed");
  }

  protected int givenCapacityIsIncreased(Subscription subscription) {
    return AwaitilityUtils.until(
        () -> service.getSkuCapacityBySubscription(subscription).getMeta().getCount(),
        capacity -> capacity > 0);
  }

  protected Subscription givenPhysicalSubscriptionIsCreated(
      String physicalSku, double coresCapacity, double socketsCapacity) {
    return givenPhysicalSubscriptionIsCreated(
        physicalSku,
        coresCapacity,
        socketsCapacity,
        OffsetDateTime.now().minusDays(1),
        OffsetDateTime.now().plusDays(1));
  }

  protected Subscription givenPhysicalSubscriptionIsCreated(
      String physicalSku,
      double coresCapacity,
      double socketsCapacity,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {
    wiremock
        .forProductAPI()
        .stubOfferingData(
            domain.Offering.buildRhelOffering(physicalSku, coresCapacity, socketsCapacity));
    assertEquals(
        HttpStatus.SC_OK,
        service.syncOffering(physicalSku).statusCode(),
        "Sync physical offering should succeed");

    Subscription physicalSubscription =
        Subscription.buildRhelSubscriptionUsingSku(
                orgId, java.util.Map.of(SOCKETS, socketsCapacity), physicalSku)
            .toBuilder()
            .startDate(startDate)
            .endDate(endDate)
            .build();
    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, physicalSubscription).statusCode(),
        "Creating physical subscription should succeed");
    return physicalSubscription;
  }

  protected Subscription givenHypervisorSubscriptionIsCreated(
      String hypervisorSku, double coresCapacity, double socketsCapacity) {
    wiremock
        .forProductAPI()
        .stubOfferingData(
            domain.Offering.buildRhelHypervisorOffering(
                hypervisorSku, coresCapacity, socketsCapacity));
    assertEquals(
        HttpStatus.SC_OK,
        service.syncOffering(hypervisorSku).statusCode(),
        "Sync hypervisor offering should succeed");

    Subscription hypervisorSubscription =
        Subscription.buildRhelSubscriptionUsingSku(
            orgId, java.util.Map.of(SOCKETS, socketsCapacity), hypervisorSku);
    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, hypervisorSubscription).statusCode(),
        "Creating hypervisor subscription should succeed");
    return hypervisorSubscription;
  }

  protected Subscription givenOpenshiftSubscriptionIsCreated(
      String openshiftSku, double coresCapacity, double socketsCapacity) {
    return givenOpenshiftSubscriptionIsCreated(
        openshiftSku,
        coresCapacity,
        socketsCapacity,
        OffsetDateTime.now().minusDays(1),
        OffsetDateTime.now().plusDays(1));
  }

  protected Subscription givenOpenshiftSubscriptionIsCreated(
      String openshiftSku,
      double coresCapacity,
      double socketsCapacity,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {
    wiremock
        .forProductAPI()
        .stubOfferingData(
            domain.Offering.buildOpenShiftOffering(openshiftSku, coresCapacity, socketsCapacity));
    assertEquals(
        HttpStatus.SC_OK,
        service.syncOffering(openshiftSku).statusCode(),
        "Sync OpenShift offering should succeed");

    Map<MetricId, Double> measurements = new HashMap<>();
    if (coresCapacity > 0) {
      measurements.put(CORES, coresCapacity);
    }
    if (socketsCapacity > 0) {
      measurements.put(SOCKETS, socketsCapacity);
    }

    Subscription openshiftSubscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(orgId, measurements, openshiftSku)
            .toBuilder()
            .startDate(startDate)
            .endDate(endDate)
            .build();
    assertEquals(
        HttpStatus.SC_OK,
        service.saveSubscriptions(true, openshiftSubscription).statusCode(),
        "Creating OpenShift subscription should succeed");
    return openshiftSubscription;
  }

  protected Response whenContractIsDeleted(String contractUuid) {
    Response response = service.deleteContract(contractUuid);
    assertEquals(HttpStatus.SC_NO_CONTENT, response.statusCode(), "Delete should succeed");
    return response;
  }

  protected void whenContractIsCreatedViaApi(Contract contract) {
    Response response = service.createContract(contract);
    assertEquals(HttpStatus.SC_OK, response.statusCode());
    var contractResponse =
        response.then().extract().as(com.redhat.swatch.contract.test.model.ContractResponse.class);
    assertEquals(SUCCESS_MESSAGE, contractResponse.getStatus().getStatus());
  }

  protected void thenContractShouldNotExist(String orgId) {
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(0, contracts.size(), "Contract should no longer be retrievable");
  }

  protected int thenCapacityIsDecreased(Subscription subscription, int initialCapacity) {
    return AwaitilityUtils.until(
        () -> service.getSkuCapacityBySubscription(subscription).getMeta().getCount(),
        capacity -> capacity < initialCapacity);
  }

  protected double getCapacityValueFromReport(CapacityReportByMetricId report) {
    return report.getData().stream()
        .filter(CapacitySnapshotByMetricId::getHasData)
        .mapToDouble(data -> data.getValue().doubleValue())
        .max()
        .orElse(0.0);
  }

  protected String getContractUuid(String orgId) {
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(1, contracts.size(), "Should have exactly one contract");
    return contracts.get(0).getUuid();
  }

  protected double getDailyCapacityByCategoryAndMetric(
      Product product,
      String orgId,
      OffsetDateTime beginning,
      OffsetDateTime ending,
      ReportCategory category,
      MetricId metric) {
    CapacityReportByMetricId report =
        service.getCapacityReportByMetricId(
            product, orgId, metric.toString(), beginning, ending, GranularityType.DAILY, category);
    return getCapacityValueFromReport(report);
  }
}
