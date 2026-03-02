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
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import api.ContractsSwatchService;
import api.ContractsWiremockService;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.contract.test.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.test.model.GranularityType;
import com.redhat.swatch.contract.test.model.ReportCategory;
import domain.Contract;
import domain.Product;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

  @KafkaBridge static KafkaBridgeService kafkaBridge = new KafkaBridgeService();

  @Wiremock static ContractsWiremockService wiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-contracts")
  static ContractsSwatchService service = new ContractsSwatchService();

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

  void givenContractIsCreated(Contract contract) {
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubPartnerSubscriptions(forContract(contract));
    // Sync offering needed for contract to persist with the SKU
    Response sync = service.syncOffering(contract.getOffering().getSku());
    assertThat("Sync offering should succeed", sync.statusCode(), is(HttpStatus.SC_OK));

    Response create = service.createContract(contract);
    assertThat("Creating contract should succeed", create.statusCode(), is(HttpStatus.SC_OK));
  }

  /** Helper method to await and get initial capacity after contract/subscription creation. */
  int givenCapacityIsIncreased(Subscription subscription) {
    return await("Capacity should increase")
        .atMost(1, MINUTES)
        .pollInterval(1, SECONDS)
        .until(
            () ->
                Objects.requireNonNull(service.getSkuCapacityBySubscription(subscription).getMeta())
                    .getCount(),
            capacity -> capacity > 0);
  }

  /** Helper method to await capacity decrease and return the new capacity. */
  int thenCapacityIsDecreased(Subscription subscription, int initialCapacity) {
    return await("Capacity should decrease")
        .atMost(1, MINUTES)
        .pollInterval(1, SECONDS)
        .until(
            () ->
                Objects.requireNonNull(service.getSkuCapacityBySubscription(subscription).getMeta())
                    .getCount(),
            capacity -> capacity < initialCapacity);
  }

  protected double getHypervisorSocketCapacity(
      Product product, String orgId, OffsetDateTime beginning, OffsetDateTime ending) {
    CapacityReportByMetricId report =
        service.getCapacityReportByMetricId(
            product,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.HYPERVISOR);
    return getCapacityValueFromReport(report);
  }

  protected double getPhysicalSocketCapacity(
      Product product, String orgId, OffsetDateTime beginning, OffsetDateTime ending) {
    CapacityReportByMetricId report =
        service.getCapacityReportByMetricId(
            product,
            orgId,
            SOCKETS.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.PHYSICAL);
    return getCapacityValueFromReport(report);
  }

  protected double getPhysicalCoreCapacity(
      Product product, String orgId, OffsetDateTime beginning, OffsetDateTime ending) {
    CapacityReportByMetricId report =
        service.getCapacityReportByMetricId(
            product,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.PHYSICAL);
    return getCapacityValueFromReport(report);
  }

  protected double getHypervisorCoreCapacity(
      Product product, String orgId, OffsetDateTime beginning, OffsetDateTime ending) {
    CapacityReportByMetricId report =
        service.getCapacityReportByMetricId(
            product,
            orgId,
            CORES.toString(),
            beginning,
            ending,
            GranularityType.DAILY,
            ReportCategory.HYPERVISOR);
    return getCapacityValueFromReport(report);
  }

  /** Helper method to extract capacity value from the capacity report. */
  protected double getCapacityValueFromReport(CapacityReportByMetricId report) {
    return report.getData().stream()
        .filter(data -> Boolean.TRUE.equals(data.getHasData()))
        .mapToDouble(data -> data.getValue().doubleValue())
        .max()
        .orElse(0.0);
  }

  /** Helper method to delete a contract by UUID and verify success. */
  protected void whenContractIsDeleted(String contractUuid) {
    Response deleteResponse = service.deleteContract(contractUuid);
    assertThat("Delete should succeed", deleteResponse.statusCode(), is(HttpStatus.SC_NO_CONTENT));
  }

  /** Helper method to verify that a contract no longer exists for the organization. */
  protected void thenContractShouldNotExist(String orgId) {
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(0, contracts.size(), "Contract should no longer be retrievable");
  }

  /** Helper method to get the UUID of the first contract for an organization. */
  protected String getContractUuid(String orgId) {
    var contracts = service.getContractsByOrgId(orgId);
    assertEquals(1, contracts.size(), "Should have exactly one contract");
    return contracts.get(0).getUuid();
  }

  /**
   * Helper method to delete a contract by UUID, handling graceful non-existent contract scenarios.
   */
  protected Response whenContractIsDeletedGracefully(String contractUuid) {
    return service.deleteContract(contractUuid);
  }

  /** Helper method to verify that contract deletion handles non-existent contracts gracefully. */
  protected void thenDeleteShouldBeIdempotent(Response deleteResponse) {
    assertThat(
        "Delete non-existent contract should return 204 No Content (idempotent behavior)",
        deleteResponse.statusCode(),
        is(HttpStatus.SC_NO_CONTENT));
  }

  /** Helper method to create a physical subscription with specified SKU and capacity values. */
  protected Subscription givenPhysicalSubscriptionIsCreated(
      String physicalSku, double coresCapacity, double socketsCapacity) {
    return givenPhysicalSubscriptionIsCreated(
        physicalSku,
        coresCapacity,
        socketsCapacity,
        OffsetDateTime.now().minusDays(1),
        OffsetDateTime.now().plusDays(1));
  }

  /**
   * Helper method to create a physical subscription with specified SKU, capacity values, and custom
   * dates.
   */
  protected Subscription givenPhysicalSubscriptionIsCreated(
      String physicalSku,
      double coresCapacity,
      double socketsCapacity,
      OffsetDateTime startDate,
      OffsetDateTime endDate) {
    // Given: A physical offering with standard sockets capacity (no DERIVED_SKU)
    wiremock
        .forProductAPI()
        .stubOfferingData(
            domain.Offering.buildRhelOffering(physicalSku, coresCapacity, socketsCapacity));
    assertThat(
        "Sync physical offering should succeed",
        service.syncOffering(physicalSku).statusCode(),
        is(HttpStatus.SC_OK));

    // When: Create a physical subscription with sockets and custom dates
    Subscription physicalSubscription =
        Subscription.buildRhelSubscriptionUsingSku(
                orgId, java.util.Map.of(SOCKETS, socketsCapacity), physicalSku)
            .toBuilder()
            .startDate(startDate)
            .endDate(endDate)
            .build();
    assertThat(
        "Creating physical subscription should succeed",
        service.saveSubscriptions(true, physicalSubscription).statusCode(),
        is(HttpStatus.SC_OK));
    return physicalSubscription;
  }

  /** Helper method to create a hypervisor subscription with specified SKU and capacity values. */
  protected Subscription givenHypervisorSubscriptionIsCreated(
      String hypervisorSku, double coresCapacity, double socketsCapacity) {
    // Given: A hypervisor offering with hypervisor sockets capacity
    wiremock
        .forProductAPI()
        .stubOfferingData(
            domain.Offering.buildRhelHypervisorOffering(
                hypervisorSku, coresCapacity, socketsCapacity));
    assertThat(
        "Sync hypervisor offering should succeed",
        service.syncOffering(hypervisorSku).statusCode(),
        is(HttpStatus.SC_OK));

    // When: Create a hypervisor subscription with hypervisor sockets
    Subscription hypervisorSubscription =
        Subscription.buildRhelSubscriptionUsingSku(
            orgId, java.util.Map.of(SOCKETS, socketsCapacity), hypervisorSku);
    assertThat(
        "Creating hypervisor subscription should succeed",
        service.saveSubscriptions(true, hypervisorSubscription).statusCode(),
        is(HttpStatus.SC_OK));
    return hypervisorSubscription;
  }

  /** Helper method to create an OpenShift subscription with specified SKU and cores capacity. */
  protected Subscription givenSubscriptionWithCoresCapacity(String sku, double coresCapacity) {
    // Create offering with cores capacity
    wiremock
        .forProductAPI()
        .stubOfferingData(domain.Offering.buildOpenShiftOffering(sku, coresCapacity, null));
    assertThat(
        "Sync offering should succeed",
        service.syncOffering(sku).statusCode(),
        is(HttpStatus.SC_OK));

    // Create subscription with cores capacity
    Subscription subscription =
        Subscription.buildOpenShiftSubscriptionUsingSku(
            orgId, java.util.Map.of(CORES, coresCapacity), sku);
    assertThat(
        "Creating subscription should succeed",
        service.saveSubscriptions(true, subscription).statusCode(),
        is(HttpStatus.SC_OK));
    return subscription;
  }

  /**
   * Helper method to create a ROSA contract with specified SKU and cores capacity. ROSA requires a
   * contract (not just a subscription) because it's a marketplace product with billing provider
   * information.
   */
  protected Contract givenRosaContractIsCreated(String sku, double coresCapacity) {
    Contract contract =
        Contract.buildRosaContract(
            orgId, domain.BillingProvider.AWS, java.util.Map.of(CORES, coresCapacity), sku);
    givenContractIsCreated(contract);
    return contract;
  }
}
