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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.component.tests.utils.RandomUtils;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

  private static String testOrgId;
  private static String cloudOrgId;
  private static HostStateManager hostManager;
  private static OffsetDateTime beginning;
  private static OffsetDateTime ending;

  @BeforeAll
  static void setUpSharedHosts() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
    testOrgId = RandomUtils.generateRandom();
    cloudOrgId = RandomUtils.generateRandom();
    beginning = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    ending = beginning.plusDays(1).minusNanos(1);

    service.createOptInConfig(testOrgId);
    service.createOptInConfig(cloudOrgId);

    givenPhysicalFixture(testOrgId);
    givenVirtualHost(testOrgId, 4, "Premium", "Production");
    givenVirtualHost(testOrgId, 2, "Standard", "Development/Test");
    givenVirtualHost(testOrgId, 2, "Self-Support", "Development/Test");
    givenCloudHost(cloudOrgId);

    service.tallyOrg(testOrgId);
    service.tallyOrg(cloudOrgId);
  }

  @BeforeEach
  void setUpRbacForSharedOrgs() {
    stubRbacAccessForOrg(testOrgId);
    stubRbacAccessForOrg(cloudOrgId);
  }

  @AfterAll
  static void cleanUpHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC001")
  void shouldFilterPhysicalSocketsBySla(boolean primaryRowSearches) {
    // Given: Shared physical hosts have distinct SLA and usage values
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Daily physical reports are filtered by SLA
    int premium = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Premium"));
    int standard = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Standard"));

    // Then: Each SLA reports the sockets contributed by matching hosts
    assertEquals(6, premium);
    assertEquals(6, standard);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC002")
  void shouldFilterPhysicalSocketsByUsage(boolean primaryRowSearches) {
    // Given: Shared physical hosts have distinct SLA and usage values
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Daily physical reports are filtered by usage
    int production = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("usage", "Production"));
    int development =
        thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("usage", "Development/Test"));

    // Then: Each usage reports the sockets contributed by matching hosts
    assertEquals(4, production);
    assertEquals(8, development);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC003")
  void shouldCombineSlaAndUsageFilters(boolean primaryRowSearches) {
    // Given: Shared physical hosts have distinct SLA and usage values
    givenFeatureFlagIsConfigured(primaryRowSearches);
    Map<String, String> premiumProduction = Map.of("sla", "Premium", "usage", "Production");

    // When: Daily physical reports combine SLA and usage filters
    TallyReportData report = getDailyReport(testOrgId, SOCKETS, PHYSICAL, premiumProduction);
    int standardDevelopment =
        thenDailyValue(
            testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Standard", "usage", "Development/Test"));
    int premiumDevelopment =
        thenDailyValue(
            testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Premium", "usage", "Development/Test"));
    int standardProduction =
        thenDailyValue(
            testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Standard", "usage", "Production"));

    // Then: Combined filters isolate each host and echo report metadata
    var data = report.getData();
    assertNotNull(data);
    assertEquals(4, data.stream().mapToInt(TallyReportDataPoint::getValue).sum());
    assertEquals(6, standardDevelopment);
    assertEquals(2, premiumDevelopment);
    assertEquals(0, standardProduction);
    var meta = report.getMeta();
    assertNotNull(meta);
    assertEquals(ServiceLevelType.PREMIUM, meta.getServiceLevel());
    assertEquals(UsageType.PRODUCTION, meta.getUsage());
    assertEquals(GranularityType.DAILY, meta.getGranularity());
    assertNull(meta.getBillingProvider());
    assertNull(meta.getBillingAcountId());
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC004")
  void shouldPartitionUnfilteredTotalBySla(boolean primaryRowSearches) {
    // Given: Shared physical hosts total twelve sockets
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: The unfiltered and SLA-filtered reports are queried
    int all = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of());
    int premium = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Premium"));
    int standard = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("sla", "Standard"));

    // Then: SLA slices partition the unfiltered physical total
    assertEquals(12, all);
    assertEquals(6, premium);
    assertEquals(6, standard);
    assertEquals(all, premium + standard);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC005")
  void shouldExcludeAwsBillingProvider(boolean primaryRowSearches) {
    // Given: Shared traditional physical hosts have no billing provider
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Reports are queried with and without an AWS filter
    int all = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of());
    int aws = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("billing_provider", "aws"));

    // Then: AWS filtering excludes traditional nightly snapshots
    assertEquals(12, all);
    assertEquals(0, aws);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC006")
  void shouldExcludeUnknownBillingAccount(boolean primaryRowSearches) {
    // Given: Shared traditional physical hosts have no billing account
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Reports are queried with and without a billing account filter
    int all = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of());
    int unknownAccount =
        thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of("billing_account_id", "test123"));

    // Then: A non-matching billing account returns no sockets
    assertEquals(12, all);
    assertEquals(0, unknownAccount);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC007")
  void shouldAddVirtualSocketsAndCores(boolean primaryRowSearches) {
    // Given: One shared virtual host matches Premium and Production
    givenFeatureFlagIsConfigured(primaryRowSearches);
    Map<String, String> filters = Map.of("sla", "Premium", "usage", "Production");

    // When: Matching and non-matching virtual reports are queried
    int sockets = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, filters);
    int cores = thenDailyValue(testOrgId, CORES, VIRTUAL, filters);
    int nonMatching =
        thenDailyValue(
            testOrgId, SOCKETS, VIRTUAL, Map.of("sla", "Standard", "usage", "Production"));

    // Then: The matching virtual slice contains normalized sockets and cores
    assertEquals(1, sockets);
    assertEquals(2, cores);
    assertEquals(0, nonMatching);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC008")
  void shouldPartitionVirtualSocketsBySla(boolean primaryRowSearches) {
    // Given: Shared virtual hosts cover every defined SLA
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: The unfiltered and SLA-filtered virtual reports are queried
    int all = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, Map.of());
    int premium = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, Map.of("sla", "Premium"));
    int standard = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, Map.of("sla", "Standard"));
    int selfSupport = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, Map.of("sla", "Self-Support"));
    int empty = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, Map.of("sla", ""));

    // Then: Defined and empty SLA slices partition the total
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
    // Given: Shared physical and virtual hosts contribute on the same day
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Physical, virtual, and unfiltered reports are queried
    int physical = thenDailyValue(testOrgId, SOCKETS, PHYSICAL, Map.of());
    int virtual = thenDailyValue(testOrgId, SOCKETS, VIRTUAL, Map.of());
    int all = thenDailyValue(testOrgId, SOCKETS, null, Map.of());

    // Then: Category reports isolate and partition both contributions
    assertEquals(12, physical);
    assertEquals(3, virtual);
    assertEquals(physical + virtual, all);
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-filters-nonpayg-TC010")
  void shouldAddNonMarketplaceCloudTotals(boolean primaryRowSearches) {
    // Given: One shared non-marketplace AWS cloud host exists
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Cloud tally and instance reports are queried
    int sockets = thenDailyValue(cloudOrgId, SOCKETS, CLOUD, Map.of());
    int cores = thenDailyValue(cloudOrgId, CORES, CLOUD, Map.of());
    int instances =
        Objects.requireNonNull(thenInstancesForCategory(cloudOrgId).getMeta()).getCount();

    // Then: Cloud sockets, cores, and instance count match the host
    assertEquals(1, sockets);
    assertEquals(2, cores);
    assertEquals(1, instances);
  }

  private static void givenPhysicalFixture(String fixtureOrgId) {
    givenPhysicalHost(fixtureOrgId, 4, "Premium", "Production");
    givenPhysicalHost(fixtureOrgId, 6, "Standard", "Development/Test");
    givenPhysicalHost(fixtureOrgId, 2, "Premium", "Development/Test");
  }

  private static void givenPhysicalHost(
      String fixtureOrgId, int sockets, String sla, String usage) {
    hostManager
        .createHost(fixtureOrgId)
        .displayName("physical-" + UUID.randomUUID())
        .apply(HostTemplates.conduitReportedPhysicalRhel(sockets, sockets * 2))
        .rhsmFacts(RhsmFacts.builder().defaultFacts().sla(sla).usage(usage).build())
        .insert();
  }

  private static void givenVirtualHost(String fixtureOrgId, int cores, String sla, String usage) {
    hostManager
        .createHost(fixtureOrgId)
        .displayName("virtual-" + UUID.randomUUID())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().isVirtual(true).sla(sla).usage(usage).build())
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

  private static void givenCloudHost(String fixtureOrgId) {
    hostManager
        .createHost(fixtureOrgId)
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

  private TallyReportData getDailyReport(
      String fixtureOrgId, String metric, String category, Map<String, ?> filters) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("granularity", "Daily");
    parameters.put("beginning", beginning.toString());
    parameters.put("ending", ending.toString());
    if (category != null) {
      parameters.put("category", category);
    }
    parameters.putAll(filters);
    return service.getTallyReportData(fixtureOrgId, PRODUCT, metric, parameters);
  }

  private int thenDailyValue(
      String fixtureOrgId, String metric, String category, Map<String, ?> filters) {
    return Objects.requireNonNull(
            getDailyReport(fixtureOrgId, metric, category, filters).getData())
        .stream()
        .mapToInt(TallyReportDataPoint::getValue)
        .sum();
  }

  private InstanceResponse thenInstancesForCategory(String fixtureOrgId) {
    return service.getInstancesByProduct(
        fixtureOrgId,
        PRODUCT,
        beginning,
        ending,
        Map.of("category", TallyReportFiltersNonPaygTest.CLOUD));
  }
}
