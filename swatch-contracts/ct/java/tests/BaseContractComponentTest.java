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

// Intentionally avoid assertions here; keep assertions in test classes.

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
import domain.Contract;
import domain.Offering;
import domain.Subscription;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

@ComponentTest
@Tag("component")
@Tag("contracts")
public class BaseContractComponentTest {
  static final MetricId CORES = MetricIdUtils.getCores();
  static final MetricId SOCKETS = MetricIdUtils.getSockets();

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
    assertThat("Sync offering should succeed", sync.statusCode(), is(200));

    Response create = service.createContract(contract);
    assertThat("Creating contract should succeed", create.statusCode(), is(200));
  }

  /** Common helper to stub upstream offering with capacity and sync it into the service. */
  protected void stubOfferingAndSync(String sku, double cores, double sockets) {
    wiremock.forProductAPI().stubOfferingData(Offering.buildRhelOffering(sku, cores, sockets));
    Response r = service.syncOffering(sku);

    assertThat("Sync offering should succeed", r.statusCode(), is(200));
  }

  /** Common helper to persist a PAYG subscription for the given org/sku with reconcile on. */
  protected String saveSubscriptionForOrgAndSku(String orgId, String sku) {
    Subscription sub = Subscription.buildRhelSubscriptionUsingSku(orgId, Map.of(), sku);
    Response r = service.saveSubscriptions(true, sub);

    assertThat("Building a Rhel subscription should succeed", r.statusCode(), is(200));

    return sub.getSubscriptionId();
  }

  /** Common helper to fetch the capacity count for a product/org, asserting 200. */
  protected int getCapacityCount(String productId, String orgId) {
    Response r = service.getSkuCapacityByProductIdForOrg(productId, orgId);
    assertThat("Sync offering call should succeed", r.statusCode(), is(200));

    return r.jsonPath().getInt("meta.count");
  }

  /** Common helper to fetch the full capacity report for a product/org, asserting 200. */
  protected Response getCapacityReport(String productId, String orgId) {
    Response r = service.getSkuCapacityByProductIdForOrg(productId, orgId);

    assertThat("Sync offering call should succeed", r.statusCode(), is(200));

    return r;
  }
}
