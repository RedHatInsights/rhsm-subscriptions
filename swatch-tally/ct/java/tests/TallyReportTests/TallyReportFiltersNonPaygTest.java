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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.tally.test.model.GranularityType;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.ServiceLevelType;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import com.redhat.swatch.tally.test.model.UsageType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TallyReportFiltersNonPaygTest extends BaseTallyComponentTest {

  private static final String PRODUCT = RHEL_FOR_X86.productTag();
  private static final String SOCKETS = "Sockets";
  private static final String CORES = "Cores";
  private static final String PHYSICAL = "physical";
  private static final String VIRTUAL = "virtual";
  private static final String CLOUD = "cloud";

  private HostStateManager hostManager;
  private OffsetDateTime beginning;
  private OffsetDateTime ending;

  @BeforeEach
  void setUpHostManager() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
    beginning = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    ending = beginning.plusDays(1).minusNanos(1);
  }

  @AfterEach
  void cleanUpHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC001")
  void shouldFilterPhysicalSocketsBySla(boolean primaryRowSearches) {
    // Given: Three physical hosts have distinct SLA and usage values
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Each SLA reports the sockets contributed by matching hosts
    assertEquals(
        6, thenDailyValue(SOCKETS, PHYSICAL, Map.of("sla", "Premium")));
    assertEquals(
        6, thenDailyValue(SOCKETS, PHYSICAL, Map.of("sla", "Standard")));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC002")
  void shouldFilterPhysicalSocketsByUsage(boolean primaryRowSearches) {
    // Given: Three physical hosts have distinct SLA and usage values
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Each usage reports the sockets contributed by matching hosts
    assertEquals(
        4,
        thenDailyValue(
            SOCKETS, PHYSICAL, Map.of("usage", "Production")));
    assertEquals(
        8,
        thenDailyValue(
            SOCKETS,
            PHYSICAL,
            Map.of("usage", "Development/Test")));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC003")
  void shouldCombineSlaAndUsageFilters(boolean primaryRowSearches) {
    // Given: Three physical hosts have distinct SLA and usage values
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Combined filters isolate each host and echo report metadata
    Map<String, String> premiumProduction =
        Map.of("sla", "Premium", "usage", "Production");
    TallyReportData report =
        thenDailyReport(SOCKETS, PHYSICAL, premiumProduction);
    assertEquals(
        4,
        report.getData() != null
            ? report.getData().stream().mapToInt(TallyReportDataPoint::getValue).sum()
            : 0);
    assertEquals(
        6,
        thenDailyValue(
            SOCKETS,
            PHYSICAL,
            Map.of(
                "sla", "Standard", "usage", "Development/Test")));
    assertEquals(
        2,
        thenDailyValue(
            SOCKETS,
            PHYSICAL,
            Map.of(
                "sla", "Premium", "usage", "Development/Test")));
    assertEquals(
        0,
        thenDailyValue(
            SOCKETS,
            PHYSICAL,
            Map.of("sla", "Standard", "usage", "Production")));
    assertEquals(
        ServiceLevelType.PREMIUM,
        report.getMeta() != null ? report.getMeta().getServiceLevel() : null);
    assertEquals(UsageType.PRODUCTION, report.getMeta().getUsage());
    assertEquals(GranularityType.DAILY, report.getMeta().getGranularity());
    assertNull(report.getMeta().getBillingProvider());
    assertNull(report.getMeta().getBillingAcountId());
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC004")
  void shouldPartitionUnfilteredTotalBySla(boolean primaryRowSearches) {
    // Given: Three physical hosts total twelve sockets
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: SLA slices partition the unfiltered physical total
    int all = thenDailyValue(SOCKETS, PHYSICAL, Map.of());
    int premium =
        thenDailyValue(SOCKETS, PHYSICAL, Map.of("sla", "Premium"));
    int standard =
        thenDailyValue(SOCKETS, PHYSICAL, Map.of("sla", "Standard"));
    assertEquals(12, all);
    assertEquals(6, premium);
    assertEquals(6, standard);
    assertEquals(all, premium + standard);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC005")
  void shouldExcludeAwsBillingProvider(boolean primaryRowSearches) {
    // Given: Traditional physical hosts have no billing provider
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: AWS filtering excludes traditional nightly snapshots
    assertEquals(12, thenDailyValue(SOCKETS, PHYSICAL, Map.of()));
    assertEquals(
        0,
        thenDailyValue(
            SOCKETS,
            PHYSICAL,
            Map.of("billing_provider", "aws")));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC006")
  void shouldExcludeUnknownBillingAccount(boolean primaryRowSearches) {
    // Given: Traditional physical hosts have no billing account
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: A non-matching billing account returns no sockets
    assertEquals(12, thenDailyValue(SOCKETS, PHYSICAL, Map.of()));
    assertEquals(
        0,
        thenDailyValue(
            SOCKETS,
            PHYSICAL,
            Map.of("billing_account_id", "test123")));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC007")
  void shouldAddVirtualSocketsAndCores(boolean primaryRowSearches) {
    // Given: Baseline reports are empty and one matching virtual host exists
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    int baselineSockets =
        thenDailyValue(
            SOCKETS,
            VIRTUAL,
            Map.of("sla", "Premium", "usage", "Production"));
    int baselineCores =
        thenDailyValue(
            CORES,
            VIRTUAL,
            Map.of("sla", "Premium", "usage", "Production"));
    givenVirtualHost(4, "Premium");

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: The matching virtual slice gains normalized sockets and cores
    Map<String, String> filters =
        Map.of("sla", "Premium", "usage", "Production");
    assertEquals(
        baselineSockets + 1, thenDailyValue(SOCKETS, VIRTUAL, filters));
    assertEquals(
        baselineCores + 2, thenDailyValue(CORES, VIRTUAL, filters));
    assertEquals(
        0,
        thenDailyValue(
            SOCKETS,
            VIRTUAL,
            Map.of("sla", "Standard", "usage", "Production")));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC008")
  void shouldPartitionVirtualSocketsBySla(boolean primaryRowSearches) {
    // Given: Virtual hosts cover every defined SLA
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenVirtualHost(2, "Premium");
    givenVirtualHost(2, "Standard");
    givenVirtualHost(2, "Self-Support");

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Defined and empty SLA slices partition the total
    int all = thenDailyValue(SOCKETS, VIRTUAL, Map.of());
    int premium =
        thenDailyValue(SOCKETS, VIRTUAL, Map.of("sla", "Premium"));
    int standard =
        thenDailyValue(SOCKETS, VIRTUAL, Map.of("sla", "Standard"));
    int selfSupport =
        thenDailyValue(
            SOCKETS, VIRTUAL, Map.of("sla", "Self-Support"));
    int empty = thenDailyValue(SOCKETS, VIRTUAL, Map.of("sla", ""));
    assertTrue(premium > 0);
    assertTrue(standard > 0);
    assertTrue(selfSupport > 0);
    assertEquals(all - premium - standard - selfSupport, empty);
    assertEquals(all, premium + standard + selfSupport + empty);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC009")
  void shouldIsolateVirtualFromPhysical(boolean primaryRowSearches) {
    // Given: One physical and one virtual host contribute on the same day
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalHost(4, "Premium", "Production");
    givenVirtualHost(2, "Premium");

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Category reports isolate and partition both contributions
    int physical = thenDailyValue(SOCKETS, PHYSICAL, Map.of());
    int virtual = thenDailyValue(SOCKETS, VIRTUAL, Map.of());
    int all = thenDailyValue(SOCKETS, null, Map.of());
    assertEquals(4, physical);
    assertEquals(1, virtual);
    assertEquals(physical + virtual, all);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC010")
  void shouldAddNonMarketplaceCloudTotals(boolean primaryRowSearches) {
    // Given: Baseline cloud reports are empty and one AWS host exists
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    int baselineSockets = thenDailyValue(SOCKETS, CLOUD, Map.of());
    int baselineCores = thenDailyValue(CORES, CLOUD, Map.of());
    int baselineInstances = thenInstancesForCategory().getMeta().getCount();
    givenCloudHost();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Cloud sockets, cores, and instance count increase
    assertEquals(
        baselineSockets + 1, thenDailyValue(SOCKETS, CLOUD, Map.of()));
    assertEquals(
        baselineCores + 2, thenDailyValue(CORES, CLOUD, Map.of()));
    assertEquals(
        baselineInstances + 1,
        Objects.requireNonNull(thenInstancesForCategory().getMeta())
            .getCount());
  }

  private void givenOrgIsOptedIn() {
    service.createOptInConfig(orgId);
  }

  private void givenPhysicalFixture() {
    givenPhysicalHost(4, "Premium", "Production");
    givenPhysicalHost(6, "Standard", "Development/Test");
    givenPhysicalHost(2, "Premium", "Development/Test");
  }

  private void givenPhysicalHost(int sockets, String sla, String usage) {
    hostManager
        .createHost(orgId)
        .displayName("physical-" + UUID.randomUUID())
        .apply(HostTemplates.conduitReportedPhysicalRhel(sockets, sockets * 2))
        .rhsmFacts(RhsmFacts.builder().defaultFacts().sla(sla).usage(usage).build())
        .insert();
  }

  private void givenVirtualHost(int cores, String sla) {
    hostManager
        .createHost(orgId)
        .displayName("virtual-" + UUID.randomUUID())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().isVirtual(true).sla(sla).usage("Production").build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("virtual")
                .arch("x86_64")
                .numberOfSockets(2)
                .numberOfCpus(cores)
                .threadsPerCore(2)
                .build())
        .insert();
  }

  private void givenCloudHost() {
    hostManager
        .createHost(orgId)
        .displayName("cloud-" + UUID.randomUUID())
        .subscriptionManagerId(UUID.randomUUID().toString())
        .providerId("i-test-" + UUID.randomUUID())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().isVirtual(true).build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("virtual")
                .cloudProvider("aws")
                .arch("x86_64")
                .numberOfSockets(2)
                .numberOfCpus(4)
                .threadsPerCore(2)
                .isMarketplace(false)
                .build())
        .insert();
  }

  private void whenNightlyTallyRuns() {
    service.tallyOrg(orgId);
  }

  private TallyReportData thenDailyReport(String metric, String category, Map<String, ?> filters) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("granularity", "Daily");
    parameters.put("beginning", beginning.toString());
    parameters.put("ending", ending.toString());
    if (category != null) {
      parameters.put("category", category);
    }
    parameters.putAll(filters);
    return service.getTallyReportData(orgId, PRODUCT, metric, parameters);
  }

  private int thenDailyValue(String metric, String category, Map<String, ?> filters) {
    return Objects.requireNonNull(thenDailyReport(metric, category, filters).getData()).stream()
        .mapToInt(TallyReportDataPoint::getValue)
        .sum();
  }

  private InstanceResponse thenInstancesForCategory() {
    return service.getInstancesByProduct(
        orgId,
        PRODUCT,
        beginning,
        ending,
        Map.of("category", TallyReportFiltersNonPaygTest.CLOUD));
  }
}
