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

import static com.redhat.swatch.component.tests.utils.Topics.BILLABLE_USAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import api.BillableUsageSwatchService;
import api.ContractsWiremockService;
import com.redhat.swatch.billable.usage.openapi.model.TallyRemittance;
import com.redhat.swatch.component.tests.api.ComponentTest;
import com.redhat.swatch.component.tests.api.KafkaBridge;
import com.redhat.swatch.component.tests.api.KafkaBridgeService;
import com.redhat.swatch.component.tests.api.Quarkus;
import com.redhat.swatch.component.tests.api.Wiremock;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import domain.Product;
import domain.RemittanceStatus;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;

@ComponentTest(name = "swatch-billable-usage")
public class BaseBillableUsageComponentTest {
  static final MetricId CORES = MetricIdUtils.getCores();
  static final MetricId VCPUS = MetricIdUtils.getVCpus();
  static final Product ROSA = Product.ROSA;
  static final Product RHEL_PAYG_ADDON = Product.RHEL_PAYG_ADDON;

  @KafkaBridge
  static KafkaBridgeService kafkaBridge = new KafkaBridgeService().subscribeToTopic(BILLABLE_USAGE);

  @Wiremock static ContractsWiremockService contractsWiremock = new ContractsWiremockService();

  @Quarkus(service = "swatch-billable-usage")
  static BillableUsageSwatchService service = new BillableUsageSwatchService();

  protected String orgId;

  @BeforeEach
  void setUp() {
    orgId = RandomUtils.generateRandom();
  }

  /** Wait for remittance to reach expected status using API polling */
  protected void waitForRemittanceStatus(String tallyId, RemittanceStatus expectedStatus) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              List<TallyRemittance> remittances = service.getRemittancesByTallyId(tallyId);
              assertNotNull(remittances, "Remittances should exist for tallyId: " + tallyId);
              assertFalse(remittances.isEmpty(), "Should have at least one remittance");
              assertEquals(
                  expectedStatus,
                  RemittanceStatus.valueOf(remittances.get(0).getStatus()),
                  "Expected status "
                      + expectedStatus
                      + " but got "
                      + remittances.get(0).getStatus());
            });
  }
}
