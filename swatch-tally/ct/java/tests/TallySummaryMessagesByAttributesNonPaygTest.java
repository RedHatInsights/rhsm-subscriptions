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

import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.tally.test.model.TallySnapshot.Granularity;
import com.redhat.swatch.tally.test.model.TallySummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TallySummaryMessagesByAttributesNonPaygTest extends BaseTallyComponentTest {

  private static final String TEST_PRODUCT_TAG = RHEL_FOR_X86.productTag();
  private static final String TEST_METRIC_ID = RHEL_FOR_X86.metricIds().get(1);

  private static final List<String> slas = List.of("Premium", "Standard", "Self-Support");
  private static final List<String> usages =
      List.of("Production", "Development/Test", "Disaster Recovery");

  private HostStateManager hostManager;

  @BeforeEach
  void setupHostManager() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
  }

  @AfterEach
  void cleanupHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  // ---- SLA test ----

  @Test
  @TestPlanName("tally-summary-by-attributes-nonpayg-TC001")
  public void testTallyNightlySummarySeparatesMeasurementsBySla() {
    // Given: Org is opted in and a host exists for each SLA type
    service.createOptInConfig(orgId);
    for (String sla : slas) {
      createSlaHost(sla, 2);
    }

    // When: Nightly tally runs and the daily summary messages are collected
    List<TallySummary> tallySummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, slas.size(), service, kafkaBridge);

    // Then: Each SLA should have its own tally value and their sum should equal total
    double slaValues = 0;
    for (String sla : slas) {
      double value = slaValue(tallySummaries, sla);
      Assertions.assertEquals(2, value, "Expected 2 sockets for SLA " + sla);
      slaValues += value;
    }
    double allTallySummaries = slaValue(tallySummaries, null);

    Assertions.assertEquals(
        slaValues, allTallySummaries, "Sum of individual SLA values should equal total");
  }

  // ---- Usage test ----

  @Test
  @TestPlanName("tally-summary-by-attributes-nonpayg-TC002")
  public void testTallyNightlySummarySeparatesMeasurementsByUsage() {
    // Given: Org is opted in and a host exists for each usage type
    service.createOptInConfig(orgId);
    for (String usage : usages) {
      createUsageHost(usage, 4);
    }

    // When: Nightly tally runs and the daily summary messages are collected
    List<TallySummary> tallySummaries =
        helpers.pollForNightlyTallyAndMessages(
            orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, usages.size(), service, kafkaBridge);

    // Then: Each Usage should have its own tally value and their sum should equal total
    double usageValues = 0;
    for (String usage : usages) {
      double value = usageValue(tallySummaries, usage);
      Assertions.assertEquals(4, value, "Expected 4 sockets for usage " + usage);
      usageValues += value;
    }
    double allTallySummaries = usageValue(tallySummaries, null);

    Assertions.assertEquals(
        usageValues, allTallySummaries, "Sum of individual Usage values should equal total");
  }

  // ---- SLA helpers ----

  private void createSlaHost(String sla, int sockets) {
    hostManager
        .createHost(orgId)
        .subscriptionManagerId(UUID.randomUUID().toString())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().sla(sla).build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("physical")
                .arch("x86_64")
                .numberOfSockets(sockets)
                .build())
        .insert();
  }

  private double slaValue(List<TallySummary> summaries, String sla) {
    return helpers.getTallySummaryValueWithSla(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.DAILY, sla);
  }

  // ---- Usage helpers ----

  private void createUsageHost(String usage, int sockets) {
    hostManager
        .createHost(orgId)
        .subscriptionManagerId(UUID.randomUUID().toString())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().usage(usage).build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("physical")
                .arch("x86_64")
                .numberOfSockets(sockets)
                .build())
        .insert();
  }

  private double usageValue(List<TallySummary> summaries, String usage) {
    return helpers.getTallySummaryValueWithUsage(
        summaries, orgId, TEST_PRODUCT_TAG, TEST_METRIC_ID, Granularity.DAILY, usage);
  }
}
