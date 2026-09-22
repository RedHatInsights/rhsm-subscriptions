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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
import com.redhat.swatch.component.tests.utils.RandomUtils;
import com.redhat.swatch.tally.test.model.TallyReportData;
import com.redhat.swatch.tally.test.model.TallyReportDataPoint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TallyReportCategoryHasDataNonPaygTest extends BaseTallyComponentTest {

  private static final String PRODUCT = RHEL_FOR_X86.productTag();
  private static final String SOCKETS = "Sockets";
  private static final String PHYSICAL = "physical";
  private static final String VIRTUAL = "virtual";
  private static final String HYPERVISOR = "hypervisor";
  private static final String CLOUD = "cloud";

  private static String physicalOrgId;
  private static String mixedOrgId;
  private static String cloudOrgId;
  private static HostStateManager hostManager;
  private static OffsetDateTime beginning;
  private static OffsetDateTime ending;

  @BeforeAll
  static void setUpSharedHosts() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
    physicalOrgId = RandomUtils.generateRandom();
    mixedOrgId = RandomUtils.generateRandom();
    cloudOrgId = RandomUtils.generateRandom();
    beginning = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    ending = beginning.plusDays(1).minusNanos(1);

    service.createOptInConfig(physicalOrgId);
    service.createOptInConfig(mixedOrgId);
    service.createOptInConfig(cloudOrgId);

    givenPhysicalFixture(physicalOrgId);
    givenPhysicalHost(mixedOrgId, 4, "Premium", "Production");
    givenVirtualHost(mixedOrgId);
    givenCloudHost(cloudOrgId);

    service.tallyOrg(physicalOrgId);
    service.tallyOrg(mixedOrgId);
    service.tallyOrg(cloudOrgId);
  }

  @BeforeEach
  void setUpRbacForSharedOrgs() {
    stubRbacAccessForOrg(physicalOrgId);
    stubRbacAccessForOrg(mixedOrgId);
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
  @TestPlanName("tally-report-has-data-nonpayg-TC001")
  void shouldMarkOnlyPhysicalDataPresent(boolean primaryRowSearches) {
    // Given: The shared physical-only org contributes twelve sockets
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Daily reports are queried for each category
    Map<String, TallyReportData> reports = new HashMap<>();
    for (String category : List.of(PHYSICAL, VIRTUAL, HYPERVISOR, CLOUD)) {
      reports.put(
          category,
          getDailyReportBetween(
              physicalOrgId, category, beginning.minusDays(11), ending, Map.of()));
    }
    TallyReportDataPoint physical = latestPoint(getDailyReport(physicalOrgId, PHYSICAL, Map.of()));
    TallyReportDataPoint virtual = latestPoint(getDailyReport(physicalOrgId, VIRTUAL, Map.of()));

    // Then: Physical reports data while empty categories do not
    for (String category : List.of(PHYSICAL, VIRTUAL, HYPERVISOR, CLOUD)) {
      TallyReportData report = reports.get(category);
      assertFalse(
          report.getData() != null && report.getData().stream().anyMatch(this::claimsDataForZero),
          category + " must not claim data for a zero measurement");
    }

    assertEquals(12, physical.getValue());
    assertEquals(Boolean.TRUE, physical.getHasData());

    assertEquals(0, virtual.getValue());
    assertNotEquals(Boolean.TRUE, virtual.getHasData());
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-nonpayg-TC002")
  void shouldMarkVirtualDataPresent(boolean primaryRowSearches) {
    // Given: The shared mixed org contains a virtual RHEL host
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Its virtual daily report is queried
    TallyReportData report = getDailyReport(mixedOrgId, VIRTUAL, Map.of());
    TallyReportDataPoint latest = latestPoint(report);

    // Then: Virtual has_data matches its positive contribution
    assertTrue(latest.getValue() > 0);
    assertEquals(Boolean.TRUE, latest.getHasData());
    assertFalse(
        report.getData() != null && report.getData().stream().anyMatch(this::claimsDataForZero));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-nonpayg-TC003")
  void shouldMarkMixedCategoriesPresent(boolean primaryRowSearches) {
    // Given: The shared mixed org has physical and virtual contributions
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Physical and virtual daily reports are queried
    TallyReportDataPoint physical = latestPoint(getDailyReport(mixedOrgId, PHYSICAL, Map.of()));
    TallyReportDataPoint virtual = latestPoint(getDailyReport(mixedOrgId, VIRTUAL, Map.of()));

    // Then: Each category reports only its own positive contribution
    assertEquals(4, physical.getValue());
    assertEquals(Boolean.TRUE, physical.getHasData());
    assertEquals(1, virtual.getValue());
    assertEquals(Boolean.TRUE, virtual.getHasData());
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-nonpayg-TC004")
  void shouldMarkOnlyCloudDataPresent(boolean primaryRowSearches) {
    // Given: The shared cloud-only org has one non-marketplace AWS host
    givenFeatureFlagIsConfigured(primaryRowSearches);

    // When: Cloud and empty-category daily reports are queried
    TallyReportData cloud = getDailyReport(cloudOrgId, CLOUD, Map.of());
    TallyReportDataPoint cloudLatest = latestPoint(cloud);
    Map<String, TallyReportDataPoint> emptyCategories = new HashMap<>();
    for (String category : List.of(PHYSICAL, VIRTUAL, HYPERVISOR)) {
      emptyCategories.put(category, latestPoint(getDailyReport(cloudOrgId, category, Map.of())));
    }

    // Then: Cloud reports data and all other categories remain empty
    assertTrue(cloudLatest.getValue() > 0);
    assertEquals(Boolean.TRUE, cloudLatest.getHasData());
    assertFalse(
        cloud.getData() != null && cloud.getData().stream().anyMatch(this::claimsDataForZero));

    for (String category : List.of(PHYSICAL, VIRTUAL, HYPERVISOR)) {
      TallyReportDataPoint empty = emptyCategories.get(category);
      assertEquals(0, empty.getValue());
      assertNotEquals(Boolean.TRUE, empty.getHasData());
    }
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

  private static void givenVirtualHost(String fixtureOrgId) {
    hostManager
        .createHost(fixtureOrgId)
        .displayName("virtual-" + UUID.randomUUID())
        .rhsmFacts(
            RhsmFacts.builder()
                .defaultFacts()
                .isVirtual(true)
                .sla("Premium")
                .usage("Production")
                .build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("virtual")
                .arch("x86_64")
                .numberOfSockets(2)
                .numberOfCpus(2)
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
      String fixtureOrgId, String category, Map<String, ?> filters) {
    return getDailyReportBetween(fixtureOrgId, category, beginning, ending, filters);
  }

  private TallyReportData getDailyReportBetween(
      String fixtureOrgId,
      String category,
      OffsetDateTime rangeBeginning,
      OffsetDateTime rangeEnding,
      Map<String, ?> filters) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("granularity", "Daily");
    parameters.put("beginning", rangeBeginning.toString());
    parameters.put("ending", rangeEnding.toString());
    if (category != null) {
      parameters.put("category", category);
    }
    parameters.putAll(filters);
    return service.getTallyReportData(
        fixtureOrgId, PRODUCT, TallyReportCategoryHasDataNonPaygTest.SOCKETS, parameters);
  }

  private TallyReportDataPoint latestPoint(TallyReportData report) {
    return Objects.requireNonNull(report.getData()).stream()
        .max(Comparator.comparing(TallyReportDataPoint::getDate))
        .orElseThrow(() -> new AssertionError("Daily report has no data points"));
  }

  private boolean claimsDataForZero(TallyReportDataPoint point) {
    return point.getValue() == 0 && Boolean.TRUE.equals(point.getHasData());
  }
}
