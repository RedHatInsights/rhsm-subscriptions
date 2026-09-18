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
import org.junit.jupiter.api.AfterEach;
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
  @TestPlanName("tally-report-has-data-nonpayg-TC001")
  void shouldMarkOnlyPhysicalDataPresent(boolean primaryRowSearches) {
    // Given: Only three physical hosts contribute twelve sockets
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalFixture();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Physical reports data while empty categories do not
    for (String category : List.of(PHYSICAL, VIRTUAL, HYPERVISOR, CLOUD)) {
      TallyReportData report =
          thenDailyReportBetween(category, beginning.minusDays(11), ending, Map.of());
      assertFalse(
          report.getData() != null && report.getData().stream().anyMatch(this::claimsDataForZero),
          category + " must not claim data for a zero measurement");
    }

    TallyReportDataPoint physical =
        thenLatestPoint(PHYSICAL, Map.of());
    assertEquals(12, physical.getValue());
    assertEquals(Boolean.TRUE, physical.getHasData());

    TallyReportDataPoint virtual =
        thenLatestPoint(VIRTUAL, Map.of());
    assertEquals(0, virtual.getValue());
    assertNotEquals(Boolean.TRUE, virtual.getHasData());
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-nonpayg-TC002")
  void shouldMarkVirtualDataPresent(boolean primaryRowSearches) {
    // Given: One virtual RHEL host contributes normalized sockets
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenVirtualHost();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Virtual has_data matches its positive contribution
    TallyReportData report = thenDailyReport(VIRTUAL, Map.of());
    TallyReportDataPoint latest = latestPoint(report);
    assertTrue(latest.getValue() > 0);
    assertEquals(Boolean.TRUE, latest.getHasData());
    assertFalse(
        report.getData() != null && report.getData().stream().anyMatch(this::claimsDataForZero));
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-nonpayg-TC003")
  void shouldMarkMixedCategoriesPresent(boolean primaryRowSearches) {
    // Given: Physical and virtual hosts contribute on the same day
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenPhysicalHost(4, "Premium", "Production");
    givenVirtualHost();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Each category reports only its own positive contribution
    TallyReportDataPoint physical =
        thenLatestPoint(PHYSICAL, Map.of());
    TallyReportDataPoint virtual =
        thenLatestPoint(VIRTUAL, Map.of());
    assertEquals(4, physical.getValue());
    assertEquals(Boolean.TRUE, physical.getHasData());
    assertEquals(1, virtual.getValue());
    assertEquals(Boolean.TRUE, virtual.getHasData());
  }

  @ParameterizedTest(name = "primaryRowSearches={0}")
  @ValueSource(booleans = {true, false})
  @TestPlanName("tally-report-has-data-nonpayg-TC004")
  void shouldMarkOnlyCloudDataPresent(boolean primaryRowSearches) {
    // Given: Only one non-marketplace AWS host contributes
    givenFeatureFlagIsConfigured(primaryRowSearches);
    givenOrgIsOptedIn();
    givenCloudHost();

    // When: Nightly tally runs
    whenNightlyTallyRuns();

    // Then: Cloud reports data and all other categories remain empty
    TallyReportData cloud = thenDailyReport(CLOUD, Map.of());
    TallyReportDataPoint cloudLatest = latestPoint(cloud);
    assertTrue(cloudLatest.getValue() > 0);
    assertEquals(Boolean.TRUE, cloudLatest.getHasData());
    assertFalse(
        cloud.getData() != null && cloud.getData().stream().anyMatch(this::claimsDataForZero));

    for (String category : List.of(PHYSICAL, VIRTUAL, HYPERVISOR)) {
      TallyReportDataPoint empty = thenLatestPoint(category, Map.of());
      assertEquals(0, empty.getValue());
      assertNotEquals(Boolean.TRUE, empty.getHasData());
    }
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

  private void givenVirtualHost() {
    hostManager
        .createHost(orgId)
        .displayName("virtual-" + UUID.randomUUID())
        .rhsmFacts(RhsmFacts.builder().defaultFacts().isVirtual(true).sla("Premium").usage("Production").build())
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

  private TallyReportData thenDailyReport(String category, Map<String, ?> filters) {
    return thenDailyReportBetween(category, beginning, ending, filters);
  }

  private TallyReportData thenDailyReportBetween(
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
    return service.getTallyReportData(orgId, PRODUCT, TallyReportCategoryHasDataNonPaygTest.SOCKETS, parameters);
  }

  private TallyReportDataPoint thenLatestPoint(
      String category, Map<String, ?> filters) {
    return Objects.requireNonNull(thenDailyReport(category, filters).getData()).stream()
        .max(Comparator.comparing(TallyReportDataPoint::getDate))
        .orElseThrow(() -> new AssertionError("Daily report has no data points"));
  }

  private TallyReportDataPoint latestPoint(TallyReportData report) {
    List<TallyReportDataPoint> data = Objects.requireNonNull(report.getData());
    return data.getLast();
  }

  private boolean claimsDataForZero(TallyReportDataPoint point) {
    return point.getValue() == 0 && Boolean.TRUE.equals(point.getHasData());
  }
}
