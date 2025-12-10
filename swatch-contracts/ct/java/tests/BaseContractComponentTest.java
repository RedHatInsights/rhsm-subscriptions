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

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
import domain.Contract;
import domain.Product;
import domain.Subscription;
import io.restassured.response.Response;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-contracts")
public class BaseContractComponentTest {
  static final MetricId CORES = MetricIdUtils.getCores();
  static final MetricId SOCKETS = MetricIdUtils.getSockets();
  static final String RHEL_GRANULARITY_DAILY = "daily";
  static final String RHEL_CATEGORY_HYPERVISOR = "hypervisor";

  @KafkaBridge static KafkaBridgeService kafkaBridge = new KafkaBridgeService();

  @Wiremock static ContractsWiremockService wiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-contracts")
  static ContractsSwatchService service = new ContractsSwatchService();

  protected String orgId;

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }

  @AfterEach
  void tearDown() {
    service.deleteDataForOrg(orgId);
  }

  void givenContractIsCreated(Contract contract) {
    wiremock.forProductAPI().stubOfferingData(contract.getOffering());
    wiremock.forPartnerAPI().stubContract(contract);
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
            RHEL_GRANULARITY_DAILY,
            RHEL_CATEGORY_HYPERVISOR);
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
}
