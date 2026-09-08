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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.InstanceData;
import com.redhat.swatch.tally.test.model.InstanceResponse;
import com.redhat.swatch.tally.test.model.ReportCategory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import utils.TallyHbiDbSeeder;

/**
 * Non-PAYG nightly instances report filtering, sorting, and pagination tests.
 *
 * <p>Tests instances report behavior for physical RHEL hosts from nightly tally. Covers:
 *
 * <ul>
 *   <li>Filtering by SLA, usage, billing provider, and billing account
 *   <li>Sorting by sockets and cores
 *   <li>Pagination with limit and offset
 * </ul>
 *
 * <p>Fixtures: Three physical RHEL hosts with varying SLA/usage/socket configurations.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TallyInstancesReportFiltersNonPaygTest extends BaseTallyComponentTest {

  // Test product and metric constants
  private static final String PRODUCT_TAG = RHEL_FOR_X86.productTag();
  private static final String METRIC_ID = "Sockets";
  private static final String CATEGORY = "physical";

  // Expected host configurations (matching IQE fixture constants)
  // Host 1: 4 sockets, Premium, Production
  // Host 2: 6 sockets, Standard, Development/Test
  // Host 3: 2 sockets, Premium, Development/Test
  private static final int EXPECTED_TOTAL_SOCKETS = 12; // 4 + 6 + 2
  private static final int EXPECTED_PREMIUM_SOCKETS = 6; // Host 1 (4) + Host 3 (2)
  private static final int EXPECTED_STANDARD_SOCKETS = 6; // Host 2 (6)
  private static final int EXPECTED_PRODUCTION_USAGE_SOCKETS = 4; // Host 1
  private static final int EXPECTED_DEV_USAGE_SOCKETS = 8; // Host 2 (6) + Host 3 (2)
  private static final int EXPECTED_PREMIUM_PRODUCTION_SOCKETS = 4; // Host 1
  private static final int EXPECTED_PREMIUM_DEV_SOCKETS = 2; // Host 3
  private static final int EXPECTED_STANDARD_DEV_SOCKETS = 6; // Host 2
  private static final int EXPECTED_PHYSICAL_HOST_COUNT = 3;

  private TallyHbiDbSeeder hbiSeeder;
  private OffsetDateTime start;
  private OffsetDateTime end;

  @BeforeEach
  void setupHbiSeeder() {
    hbiSeeder = new TallyHbiDbSeeder(hbiDatabase);
    start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    end = start.plusDays(1).minusNanos(1);
  }

  @AfterEach
  void cleanupHbiHosts() {
    if (hbiSeeder != null) {
      hbiSeeder.deleteAllInsertedHosts();
    }
  }

  @BeforeAll
  static void setupFixtureHosts() {
    // Actual seeding happens in each test's setup via setupNonPaygHosts()
  }

  /**
   * Seed three physical RHEL hosts matching the IQE fixture configuration.
   *
   * @return list of seeded hosts
   */
  private List<TallyHbiDbSeeder.SeededHost> setupNonPaygHosts() {
    // Create opt-in config for the test org
    service.createOptInConfig(orgId);

    // Host 1: 4 sockets, Premium, Production
    TallyHbiDbSeeder.SeededHost host1 =
        hbiSeeder
            .rhelHost(orgId)
            .displayName("physical-premium-production")
            .sla("Premium")
            .usage("Production")
            .cores(8)
            .sockets(4)
            .insert();

    // Host 2: 6 sockets, Standard, Development/Test
    TallyHbiDbSeeder.SeededHost host2 =
        hbiSeeder
            .rhelHost(orgId)
            .displayName("physical-standard-dev")
            .sla("Standard")
            .usage("Development/Test")
            .cores(12)
            .sockets(6)
            .insert();

    // Host 3: 2 sockets, Premium, Development/Test
    TallyHbiDbSeeder.SeededHost host3 =
        hbiSeeder
            .rhelHost(orgId)
            .displayName("physical-premium-dev")
            .sla("Premium")
            .usage("Development/Test")
            .cores(4)
            .sockets(2)
            .insert();

    service.tallyOrg(orgId);
    return List.of(host1, host2, host3);
  }

  /**
   * Build a map of metric ID -> value from the parallel lists in meta and instance.
   *
   * @param response the instance response containing meta with metric IDs
   * @param instance the instance data containing measurement values
   * @return map of metric ID to value
   */
  private Map<String, Double> getMeasurementsMap(InstanceResponse response, InstanceData instance) {
    List<String> metricIds = response.getMeta().getMeasurements();
    List<Double> values = instance.getMeasurements();

    Map<String, Double> measurements = new HashMap<>();
    for (int i = 0; i < metricIds.size(); i++) {
      measurements.put(metricIds.get(i), values.get(i));
    }
    return measurements;
  }

  /**
   * Sum socket values across all instances in the response.
   *
   * @param response the instance response
   * @return total sockets
   */
  private int sumSockets(InstanceResponse response) {
    if (response.getData() == null) {
      return 0;
    }
    return response.getData().stream()
        .mapToInt(
            instance -> {
              Map<String, Double> measurements = getMeasurementsMap(response, instance);
              Double sockets = measurements.get(METRIC_ID);
              return sockets != null ? sockets.intValue() : 0;
            })
        .sum();
  }

  // --- TC001-TC006: Basic filtering tests ---

  @Test
  @TestPlanName("tally-instances-nonpayg-TC001")
  public void unfilteredPhysicalInstancesReportListsAllFixtureHosts() {
    // Given: Three physical RHEL hosts
    setupNonPaygHosts();

    // When: Query unfiltered instances report
    InstanceResponse response =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            Map.of("category", CATEGORY, "metric_id", METRIC_ID.toLowerCase()));

    // Then: Should return all three hosts with expected total sockets
    assertNotNull(response.getData());
    assertEquals(EXPECTED_PHYSICAL_HOST_COUNT, response.getData().size());
    assertEquals(EXPECTED_PHYSICAL_HOST_COUNT, response.getMeta().getCount());

    // Verify expected display names are present
    Set<String> actualNames =
        response.getData().stream()
            .map(InstanceData::getDisplayName)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
    assertTrue(actualNames.contains("physical-premium-production"));
    assertTrue(actualNames.contains("physical-standard-dev"));
    assertTrue(actualNames.contains("physical-premium-dev"));

    assertEquals(EXPECTED_TOTAL_SOCKETS, sumSockets(response));
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC002")
  public void slaFilterPartitionsMetaCountAndSockets() {
    // Given: Three physical RHEL hosts with different SLAs
    setupNonPaygHosts();

    // When/Then: Query with SLA=Premium
    Map<String, Object> premiumParams = new HashMap<>();
    premiumParams.put("category", CATEGORY);
    premiumParams.put("metric_id", METRIC_ID.toLowerCase());
    premiumParams.put("sla", "Premium");
    InstanceResponse premiumResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, premiumParams);
    assertEquals(2, premiumResp.getData().size(), "Premium SLA should return 2 hosts");
    assertEquals(2, premiumResp.getMeta().getCount());
    assertEquals(EXPECTED_PREMIUM_SOCKETS, sumSockets(premiumResp));

    // When/Then: Query with SLA=Standard
    Map<String, Object> standardParams = new HashMap<>();
    standardParams.put("category", CATEGORY);
    standardParams.put("metric_id", METRIC_ID.toLowerCase());
    standardParams.put("sla", "Standard");
    InstanceResponse standardResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, standardParams);
    assertEquals(1, standardResp.getData().size(), "Standard SLA should return 1 host");
    assertEquals(1, standardResp.getMeta().getCount());
    assertEquals(EXPECTED_STANDARD_SOCKETS, sumSockets(standardResp));

    // Verify SLA buckets partition the total
    assertEquals(
        EXPECTED_PHYSICAL_HOST_COUNT,
        premiumResp.getMeta().getCount() + standardResp.getMeta().getCount(),
        "SLA bucket counts should sum to total host count");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC003")
  public void usageFilterPartitionsMetaCountAndSockets() {
    // Given: Three physical RHEL hosts with different usage values
    setupNonPaygHosts();

    // When/Then: Query with usage=Production
    Map<String, Object> prodParams = new HashMap<>();
    prodParams.put("category", CATEGORY);
    prodParams.put("metric_id", METRIC_ID.toLowerCase());
    prodParams.put("usage", "Production");
    InstanceResponse prodResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, prodParams);
    assertEquals(1, prodResp.getData().size(), "Production usage should return 1 host");
    assertEquals(1, prodResp.getMeta().getCount());
    assertEquals(EXPECTED_PRODUCTION_USAGE_SOCKETS, sumSockets(prodResp));

    // When/Then: Query with usage=Development/Test
    Map<String, Object> devParams = new HashMap<>();
    devParams.put("category", CATEGORY);
    devParams.put("metric_id", METRIC_ID.toLowerCase());
    devParams.put("usage", "Development/Test");
    InstanceResponse devResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, devParams);
    assertEquals(2, devResp.getData().size(), "Development/Test usage should return 2 hosts");
    assertEquals(2, devResp.getMeta().getCount());
    assertEquals(EXPECTED_DEV_USAGE_SOCKETS, sumSockets(devResp));

    // Verify usage buckets partition the total
    assertEquals(
        EXPECTED_PHYSICAL_HOST_COUNT,
        prodResp.getMeta().getCount() + devResp.getMeta().getCount(),
        "Usage bucket counts should sum to total host count");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC004")
  public void combinedSlaAndUsageFiltersNarrowToOneHost() {
    // Given: Three physical RHEL hosts
    setupNonPaygHosts();

    // When/Then: Premium + Production (Host 1)
    Map<String, Object> premiumProdParams = new HashMap<>();
    premiumProdParams.put("category", CATEGORY);
    premiumProdParams.put("metric_id", METRIC_ID.toLowerCase());
    premiumProdParams.put("sla", "Premium");
    premiumProdParams.put("usage", "Production");
    InstanceResponse premiumProdResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, premiumProdParams);
    assertEquals(1, premiumProdResp.getData().size());
    assertEquals(EXPECTED_PREMIUM_PRODUCTION_SOCKETS, sumSockets(premiumProdResp));

    // When/Then: Standard + Development/Test (Host 2)
    Map<String, Object> standardDevParams = new HashMap<>();
    standardDevParams.put("category", CATEGORY);
    standardDevParams.put("metric_id", METRIC_ID.toLowerCase());
    standardDevParams.put("sla", "Standard");
    standardDevParams.put("usage", "Development/Test");
    InstanceResponse standardDevResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, standardDevParams);
    assertEquals(1, standardDevResp.getData().size());
    assertEquals(EXPECTED_STANDARD_DEV_SOCKETS, sumSockets(standardDevResp));

    // When/Then: Premium + Development/Test (Host 3)
    Map<String, Object> premiumDevParams = new HashMap<>();
    premiumDevParams.put("category", CATEGORY);
    premiumDevParams.put("metric_id", METRIC_ID.toLowerCase());
    premiumDevParams.put("sla", "Premium");
    premiumDevParams.put("usage", "Development/Test");
    InstanceResponse premiumDevResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, premiumDevParams);
    assertEquals(1, premiumDevResp.getData().size());
    assertEquals(EXPECTED_PREMIUM_DEV_SOCKETS, sumSockets(premiumDevResp));

    // When/Then: Standard + Production (no matching host)
    Map<String, Object> standardProdParams = new HashMap<>();
    standardProdParams.put("category", CATEGORY);
    standardProdParams.put("metric_id", METRIC_ID.toLowerCase());
    standardProdParams.put("sla", "Standard");
    standardProdParams.put("usage", "Production");
    InstanceResponse standardProdResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, standardProdParams);
    assertTrue(
        standardProdResp.getData() == null || standardProdResp.getData().isEmpty(),
        "Standard + Production should return no hosts");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC005")
  public void excludesAwsBillingProvider() {
    // Given: Three physical RHEL hosts (non-PAYG, should have _ANY billing provider)
    setupNonPaygHosts();

    // When: Query with billing_provider=aws
    Map<String, Object> params = new HashMap<>();
    params.put("category", CATEGORY);
    params.put("metric_id", METRIC_ID.toLowerCase());
    params.put("billing_provider", "aws");
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Should return no rows (non-PAYG hosts use _ANY, not aws)
    assertTrue(
        response.getData() == null || response.getData().isEmpty(),
        "Non-PAYG physical hosts should not match billing_provider=aws filter");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC006")
  public void excludesNonMatchingBillingAccountId() {
    // Given: Three physical RHEL hosts
    setupNonPaygHosts();

    // When: Query with a random billing_account_id
    String randomBillingAccountId = UUID.randomUUID().toString();
    Map<String, Object> params = new HashMap<>();
    params.put("category", CATEGORY);
    params.put("metric_id", METRIC_ID.toLowerCase());
    params.put("billing_account_id", randomBillingAccountId);
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Should return no rows
    assertTrue(
        response.getData() == null || response.getData().isEmpty(),
        "Random billing_account_id should not match any non-PAYG physical hosts");
  }

  // --- TC007-TC008: Pagination tests ---

  @Test
  @TestPlanName("tally-instances-sorting-TC007")
  public void paginationLimitAndOffsetForNonPayg() {
    // Given: Three physical RHEL hosts
    setupNonPaygHosts();

    // When: Query first page with limit=1, offset=0
    Map<String, Object> page0Params = new HashMap<>();
    page0Params.put("category", CATEGORY);
    page0Params.put("metric_id", METRIC_ID.toLowerCase());
    page0Params.put("limit", 1);
    page0Params.put("offset", 0);
    InstanceResponse page0 =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, page0Params);

    // Then: Should return 1 row with meta.count = 3
    assertNotNull(page0.getData());
    assertTrue(page0.getData().size() <= 1, "First page should return at most 1 row");
    assertEquals(EXPECTED_PHYSICAL_HOST_COUNT, page0.getMeta().getCount());

    // When: Query second page with limit=1, offset=1
    Map<String, Object> page1Params = new HashMap<>();
    page1Params.put("category", CATEGORY);
    page1Params.put("metric_id", METRIC_ID.toLowerCase());
    page1Params.put("limit", 1);
    page1Params.put("offset", 1);
    InstanceResponse page1 =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, page1Params);

    // Then: Should also return at most 1 row with same meta.count
    assertTrue(page1.getData().size() <= 1, "Second page should return at most 1 row");
    assertEquals(EXPECTED_PHYSICAL_HOST_COUNT, page1.getMeta().getCount());
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC008")
  public void paginationLinksForNonPayg() {
    // Given: Three physical RHEL hosts
    setupNonPaygHosts();

    // When: Query without limit
    Map<String, Object> noLimitParams = new HashMap<>();
    noLimitParams.put("category", CATEGORY);
    noLimitParams.put("metric_id", METRIC_ID.toLowerCase());
    InstanceResponse withoutLimit =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, noLimitParams);

    // Then: Should return all 3 rows
    assertNotNull(withoutLimit.getData());
    assertEquals(3, withoutLimit.getData().size());

    // When: Query with limit=2
    Map<String, Object> withLimitParams = new HashMap<>();
    withLimitParams.put("category", CATEGORY);
    withLimitParams.put("metric_id", METRIC_ID.toLowerCase());
    withLimitParams.put("limit", 2);
    withLimitParams.put("offset", 0);
    InstanceResponse withLimit =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, withLimitParams);

    // Then: Should return at most 2 rows with links present and meta populated
    assertTrue(withLimit.getData().size() <= 2, "Limited query should return at most 2 rows");
    assertNotNull(withLimit.getMeta(), "Meta should be present for paginated query");
    if (withLimit.getLinks() != null) {
      // If links are present, verify at least one link is non-empty
      assertFalse(
          (withLimit.getLinks().getFirst() == null || withLimit.getLinks().getFirst().isBlank()),
          "Pagination links should be non-empty when present");
    }
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC010")
  public void sortBySocketsForNonPayg() {
    // Given: Three physical RHEL hosts with different socket counts (4, 6, 2)
    setupNonPaygHosts();

    // When: Sort by sockets ascending
    Map<String, Object> ascParams = new HashMap<>();
    ascParams.put("category", CATEGORY);
    ascParams.put("metric_id", METRIC_ID.toLowerCase());
    ascParams.put("sort", METRIC_ID.toLowerCase());
    ascParams.put("dir", "asc");
    InstanceResponse ascResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, ascParams);

    // Then: First should have 2 sockets, last should have 6 sockets
    assertNotNull(ascResp.getData());
    assertEquals(3, ascResp.getData().size());
    Map<String, Double> firstMeasurements = getMeasurementsMap(ascResp, ascResp.getData().get(0));
    Map<String, Double> lastMeasurements = getMeasurementsMap(ascResp, ascResp.getData().get(2));
    assertEquals(2.0, firstMeasurements.get(METRIC_ID), "First host should have 2 sockets");
    assertEquals(6.0, lastMeasurements.get(METRIC_ID), "Last host should have 6 sockets");

    // When: Sort by sockets descending
    Map<String, Object> descParams = new HashMap<>();
    descParams.put("category", CATEGORY);
    descParams.put("metric_id", METRIC_ID.toLowerCase());
    descParams.put("sort", METRIC_ID.toLowerCase());
    descParams.put("dir", "desc");
    InstanceResponse descResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, descParams);

    // Then: First should have 6 sockets, last should have 2 sockets
    assertNotNull(descResp.getData());
    assertEquals(3, descResp.getData().size());
    Map<String, Double> firstMeasurementsDesc =
        getMeasurementsMap(descResp, descResp.getData().get(0));
    Map<String, Double> lastMeasurementsDesc =
        getMeasurementsMap(descResp, descResp.getData().get(2));
    assertEquals(6.0, firstMeasurementsDesc.get(METRIC_ID), "First host should have 6 sockets");
    assertEquals(2.0, lastMeasurementsDesc.get(METRIC_ID), "Last host should have 2 sockets");
  }

  // --- TC007-TC011: Advanced filtering and sorting tests ---

  @Test
  @TestPlanName("tally-instances-nonpayg-TC007")
  public void slaAndUsageChangeMigratesFilterBuckets() {
    // Given: One physical RHEL host with Premium/Production
    service.createOptInConfig(orgId);

    TallyHbiDbSeeder.SeededHost host =
        hbiSeeder
            .rhelHost(orgId)
            .displayName("migratable-host")
            .sla("Premium")
            .usage("Production")
            .cores(4)
            .sockets(2)
            .insert();

    service.tallyOrg(orgId);

    // When: Query with Premium SLA filter (should find the host)
    Map<String, Object> premiumParams = new HashMap<>();
    premiumParams.put("category", CATEGORY);
    premiumParams.put("metric_id", METRIC_ID.toLowerCase());
    premiumParams.put("sla", "Premium");
    InstanceResponse premiumResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, premiumParams);

    // Then: Should find host in Premium bucket
    assertNotNull(premiumResp.getData());
    assertEquals(1, premiumResp.getData().size());
    assertEquals(2, sumSockets(premiumResp));

    // Given: Update host to Standard/Development/Test
    hbiSeeder.deleteAllInsertedHosts();
    hbiSeeder
        .rhelHost(orgId)
        .inventoryId(host.inventoryId())
        .subscriptionManagerId(host.subscriptionManagerId())
        .displayName("migratable-host")
        .sla("Standard")
        .usage("Development/Test")
        .cores(4)
        .sockets(2)
        .insert();

    service.tallyOrg(orgId);

    // When: Query with Premium SLA filter again (should NOT find the host)
    InstanceResponse premiumRespAfter =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, premiumParams);

    // Then: Host should have migrated out of Premium bucket
    int premiumSockets = sumSockets(premiumRespAfter);
    assertEquals(
        0,
        premiumSockets,
        "Host should no longer appear in Premium bucket after SLA change to Standard");

    // When: Query with Standard SLA filter (should find the host)
    Map<String, Object> standardParams = new HashMap<>();
    standardParams.put("category", CATEGORY);
    standardParams.put("metric_id", METRIC_ID.toLowerCase());
    standardParams.put("sla", "Standard");
    InstanceResponse standardResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, standardParams);

    // Then: Host should now appear in Standard bucket
    assertNotNull(standardResp.getData());
    assertEquals(1, standardResp.getData().size());
    assertEquals(2, sumSockets(standardResp));
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC008")
  public void marketplaceAwsCloudHasZeroSockets() {
    // Given: AWS marketplace cloud instance (simulated via cloud provider)
    service.createOptInConfig(orgId);

    hbiSeeder
        .rhelHost(orgId)
        .displayName("marketplace-aws-cloud")
        .cloudProvider("aws")
        .providerId(UUID.randomUUID().toString())
        .sla("Premium")
        .usage("Production")
        .cores(4)
        .sockets(4)
        .insert();

    service.tallyOrg(orgId);

    // When: Query for physical instances
    Map<String, Object> params = new HashMap<>();
    params.put("category", CATEGORY);
    params.put("metric_id", METRIC_ID.toLowerCase());
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Marketplace AWS cloud should not appear in physical category
    // (Cloud instances with billing provider are marketplace and excluded from physical)
    int sockets = sumSockets(response);
    assertEquals(
        0, sockets, "Marketplace AWS cloud instances should not appear in physical category");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC009")
  public void nonMarketplaceAwsCloudHasOneSockets() {
    // Given: Non-marketplace AWS cloud instance (cloud provider without billing metadata)
    service.createOptInConfig(orgId);

    hbiSeeder
        .rhelHost(orgId)
        .displayName("non-marketplace-aws-cloud")
        .cloudProvider("aws")
        .providerId(UUID.randomUUID().toString())
        .sla("Premium")
        .usage("Production")
        .cores(4)
        .sockets(1)
        .insert();

    service.tallyOrg(orgId);

    // When: Query for cloud instances
    Map<String, Object> params = new HashMap<>();
    params.put("category", "cloud");
    params.put("metric_id", METRIC_ID.toLowerCase());
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Should find the cloud instance with 1 socket
    assertNotNull(response.getData());
    assertTrue(response.getData().size() >= 1, "Should find at least one cloud instance");
    assertEquals(1, sumSockets(response), "Non-marketplace AWS cloud should have 1 socket");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC010")
  public void virtualHostIncreasesMetaCount() {
    // Given: Two virtual hosts (infrastructure_type=virtual, no hypervisor relationship)
    // Matches IQE test: test_validate_sla_filters_for_daily_tally_on_virtual_rhel
    service.createOptInConfig(orgId);

    hbiSeeder
        .rhelHost(orgId)
        .displayName("virtual-rhel-1")
        .isVirtual(true)
        .sla("Premium")
        .usage("Production")
        .cores(2)
        .sockets(1)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .displayName("virtual-rhel-2")
        .isVirtual(true)
        .sla("Premium")
        .usage("Production")
        .cores(2)
        .sockets(1)
        .insert();

    service.tallyOrg(orgId);

    // When: Query for virtual instances
    Map<String, Object> params = new HashMap<>();
    params.put("category", "virtual");
    params.put("metric_id", METRIC_ID.toLowerCase());
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Should find both virtual hosts (meta.count = 2)
    assertNotNull(response.getData());
    assertEquals(2, response.getData().size(), "Should find both virtual hosts");
    assertEquals(2, response.getMeta().getCount(), "Meta count should reflect 2 virtual hosts");

    // Verify sockets are normalized to 1 for virtual hosts (matches IQE test)
    for (InstanceData instance : response.getData()) {
      assertEquals(ReportCategory.VIRTUAL, instance.getCategory(), "Category should be virtual");
      Map<String, Double> measurements = getMeasurementsMap(response, instance);
      Double sockets = measurements.get(METRIC_ID);
      assertEquals(1.0, sockets, "Virtual instance sockets should be normalized to 1");
    }
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC011")
  public void virtualSocketsNormalizedToOne() {
    // Given: Virtual host with multiple sockets defined (unmapped, no hypervisor relationship)
    service.createOptInConfig(orgId);

    hbiSeeder
        .rhelHost(orgId)
        .displayName("virtual-multi-socket-guest")
        .isVirtual(true)
        .sla("Premium")
        .usage("Production")
        .cores(4)
        .sockets(4) // Guest reports 4 sockets
        .insert();

    service.tallyOrg(orgId);

    // When: Query for virtual instances
    Map<String, Object> params = new HashMap<>();
    params.put("category", "virtual");
    params.put("metric_id", METRIC_ID.toLowerCase());
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Virtual guest sockets should be normalized to 1
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size(), "Should find one virtual guest");
    Map<String, Double> measurements = getMeasurementsMap(response, response.getData().get(0));
    assertEquals(
        1.0,
        measurements.get(METRIC_ID),
        "Virtual instance sockets should be normalized to 1 regardless of reported sockets");
  }

  @Test
  @TestPlanName("tally-instances-sorting-TC009")
  public void sortByNumberOfGuestsForHypervisor() {
    // Given: Two hypervisors with different guest counts
    service.createOptInConfig(orgId);

    // Hypervisor 1 with 1 guest
    String hypervisor1Uuid = UUID.randomUUID().toString();
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypervisor1Uuid)
        .displayName("hypervisor-one-guest")
        .cores(8)
        .sockets(2)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .hypervisorUuid(hypervisor1Uuid)
        .displayName("guest-of-hypervisor-1")
        .cores(2)
        .sockets(1)
        .insert();

    // Hypervisor 2 with 3 guests
    String hypervisor2Uuid = UUID.randomUUID().toString();
    hbiSeeder
        .rhelHost(orgId)
        .subscriptionManagerId(hypervisor2Uuid)
        .displayName("hypervisor-three-guests")
        .cores(16)
        .sockets(4)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .hypervisorUuid(hypervisor2Uuid)
        .displayName("guest-1-of-hypervisor-2")
        .cores(2)
        .sockets(1)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .hypervisorUuid(hypervisor2Uuid)
        .displayName("guest-2-of-hypervisor-2")
        .cores(2)
        .sockets(1)
        .insert();

    hbiSeeder
        .rhelHost(orgId)
        .hypervisorUuid(hypervisor2Uuid)
        .displayName("guest-3-of-hypervisor-2")
        .cores(2)
        .sockets(1)
        .insert();

    service.tallyOrg(orgId);

    // When: Sort hypervisors by number_of_guests ascending
    Map<String, Object> ascParams = new HashMap<>();
    ascParams.put("category", "hypervisor");
    ascParams.put("metric_id", METRIC_ID.toLowerCase());
    ascParams.put("sort", "number_of_guests");
    ascParams.put("dir", "asc");
    InstanceResponse ascResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, ascParams);

    // Then: First hypervisor should have 1 guest, last should have 3 guests
    assertNotNull(ascResp.getData());
    assertEquals(2, ascResp.getData().size(), "Should find both hypervisors");
    assertEquals(
        1, ascResp.getData().get(0).getNumberOfGuests(), "First hypervisor should have 1 guest");
    assertEquals(
        3, ascResp.getData().get(1).getNumberOfGuests(), "Last hypervisor should have 3 guests");

    // When: Sort hypervisors by number_of_guests descending
    Map<String, Object> descParams = new HashMap<>();
    descParams.put("category", "hypervisor");
    descParams.put("metric_id", METRIC_ID.toLowerCase());
    descParams.put("sort", "number_of_guests");
    descParams.put("dir", "desc");
    InstanceResponse descResp =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, descParams);

    // Then: First hypervisor should have 3 guests, last should have 1 guest
    assertNotNull(descResp.getData());
    assertEquals(2, descResp.getData().size(), "Should find both hypervisors");
    assertEquals(
        3, descResp.getData().get(0).getNumberOfGuests(), "First hypervisor should have 3 guests");
    assertEquals(
        1, descResp.getData().get(1).getNumberOfGuests(), "Last hypervisor should have 1 guest");
  }
}
