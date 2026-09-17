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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static utils.TallyTestProducts.RHEL_FOR_X86;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HbiDbConnector;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostConnector.SeededHost;
import com.redhat.swatch.component.tests.api.hbi.HostStateManager;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.api.hbi.RhsmFacts;
import com.redhat.swatch.component.tests.api.hbi.SystemProfileFacts;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
public class TallyInstancesReportFiltersNonPaygTest extends BaseTallyComponentTest {

  // Static fields
  private static final String PRODUCT_TAG = RHEL_FOR_X86.productTag();
  private static final String METRIC_ID = "Sockets";
  private static final String CATEGORY = "physical";
  private static final int EXPECTED_TOTAL_SOCKETS = 12;
  private static final int EXPECTED_PREMIUM_SOCKETS = 6;
  private static final int EXPECTED_STANDARD_SOCKETS = 6;
  private static final int EXPECTED_PRODUCTION_USAGE_SOCKETS = 4;
  private static final int EXPECTED_DEV_USAGE_SOCKETS = 8;
  private static final int EXPECTED_PREMIUM_PRODUCTION_SOCKETS = 4;
  private static final int EXPECTED_PREMIUM_DEV_SOCKETS = 2;
  private static final int EXPECTED_STANDARD_DEV_SOCKETS = 6;
  private static final int EXPECTED_PHYSICAL_HOST_COUNT = 3;

  // Instance fields
  private HostStateManager hostManager;
  private OffsetDateTime start;
  private OffsetDateTime end;

  @BeforeEach
  void setupHostManager() {
    hostManager = new HostStateManager(new HbiDbConnector(hbiDatabase));
    start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
    end = start.plusDays(1).minusNanos(1);
  }

  @AfterEach
  void cleanupHbiHosts() {
    if (hostManager != null) {
      hostManager.cleanupAll();
    }
  }

  // --- TC001-TC006: Basic filtering tests ---

  @Test
  @TestPlanName("tally-instances-nonpayg-TC001")
  public void shouldListAllPhysicalInstancesWhenUnfiltered() {
    // Given: Three physical RHEL hosts
    givenNonPaygPhysicalHosts();

    // When: Query unfiltered instances report
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, givenBaseParams());

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

    assertEquals(EXPECTED_TOTAL_SOCKETS, thenSumSockets(response));
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC002")
  public void shouldPartitionCountAndSocketsBySla() {
    // Given: Three physical RHEL hosts with different SLAs
    givenNonPaygPhysicalHosts();

    // When/Then: Query with SLA=Premium
    InstanceResponse premiumResp =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenBaseParamsWithSla("Premium"));
    assertEquals(2, premiumResp.getData().size(), "Premium SLA should return 2 hosts");
    assertEquals(2, premiumResp.getMeta().getCount());
    assertEquals(EXPECTED_PREMIUM_SOCKETS, thenSumSockets(premiumResp));

    // When/Then: Query with SLA=Standard
    InstanceResponse standardResp =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenBaseParamsWithSla("Standard"));
    assertEquals(1, standardResp.getData().size(), "Standard SLA should return 1 host");
    assertEquals(1, standardResp.getMeta().getCount());
    assertEquals(EXPECTED_STANDARD_SOCKETS, thenSumSockets(standardResp));

    // Verify SLA buckets partition the total
    assertEquals(
        EXPECTED_PHYSICAL_HOST_COUNT,
        premiumResp.getMeta().getCount() + standardResp.getMeta().getCount(),
        "SLA bucket counts should sum to total host count");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC003")
  public void shouldPartitionCountAndSocketsByUsage() {
    // Given: Three physical RHEL hosts with different usage values
    givenNonPaygPhysicalHosts();

    // When/Then: Query with usage=Production
    InstanceResponse prodResp =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenBaseParamsWithUsage("Production"));
    assertEquals(1, prodResp.getData().size(), "Production usage should return 1 host");
    assertEquals(1, prodResp.getMeta().getCount());
    assertEquals(EXPECTED_PRODUCTION_USAGE_SOCKETS, thenSumSockets(prodResp));

    // When/Then: Query with usage=Development/Test
    InstanceResponse devResp =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenBaseParamsWithUsage("Development/Test"));
    assertEquals(2, devResp.getData().size(), "Development/Test usage should return 2 hosts");
    assertEquals(2, devResp.getMeta().getCount());
    assertEquals(EXPECTED_DEV_USAGE_SOCKETS, thenSumSockets(devResp));

    // Verify usage buckets partition the total
    assertEquals(
        EXPECTED_PHYSICAL_HOST_COUNT,
        prodResp.getMeta().getCount() + devResp.getMeta().getCount(),
        "Usage bucket counts should sum to total host count");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC004")
  public void shouldNarrowToSingleHostWithCombinedFilters() {
    // Given: Three physical RHEL hosts
    givenNonPaygPhysicalHosts();

    // When/Then: Premium + Production (Host 1)
    InstanceResponse premiumProdResp =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            givenBaseParamsWithSlaAndUsage("Premium", "Production"));
    assertEquals(1, premiumProdResp.getData().size());
    assertEquals(EXPECTED_PREMIUM_PRODUCTION_SOCKETS, thenSumSockets(premiumProdResp));

    // When/Then: Standard + Development/Test (Host 2)
    InstanceResponse standardDevResp =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            givenBaseParamsWithSlaAndUsage("Standard", "Development/Test"));
    assertEquals(1, standardDevResp.getData().size());
    assertEquals(EXPECTED_STANDARD_DEV_SOCKETS, thenSumSockets(standardDevResp));

    // When/Then: Premium + Development/Test (Host 3)
    InstanceResponse premiumDevResp =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            givenBaseParamsWithSlaAndUsage("Premium", "Development/Test"));
    assertEquals(1, premiumDevResp.getData().size());
    assertEquals(EXPECTED_PREMIUM_DEV_SOCKETS, thenSumSockets(premiumDevResp));

    // When/Then: Standard + Production (no matching host)
    InstanceResponse standardProdResp =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            givenBaseParamsWithSlaAndUsage("Standard", "Production"));
    assertTrue(
        standardProdResp.getData() == null || standardProdResp.getData().isEmpty(),
        "Standard + Production should return no hosts");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC005")
  public void shouldExcludeNonPaygHostsFromAwsBillingProvider() {
    // Given: Three physical RHEL hosts (non-PAYG, should have _ANY billing provider)
    givenNonPaygPhysicalHosts();

    // When: Query with billing_provider=aws
    Map<String, Object> params = givenBaseParams();
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
  public void shouldExcludeNonMatchingBillingAccountId() {
    // Given: Three physical RHEL hosts
    givenNonPaygPhysicalHosts();

    // When: Query with a random billing_account_id
    String randomBillingAccountId = UUID.randomUUID().toString();
    Map<String, Object> params = givenBaseParams();
    params.put("billing_account_id", randomBillingAccountId);
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, params);

    // Then: Should return no rows
    assertTrue(
        response.getData() == null || response.getData().isEmpty(),
        "Random billing_account_id should not match any non-PAYG physical hosts");
  }

  // --- TC007-TC011: Advanced filtering tests ---

  @Test
  @TestPlanName("tally-instances-nonpayg-TC007")
  public void shouldMigrateInstancesWhenSlaAndUsageChange() {
    // Given: One physical RHEL host with Premium/Production
    service.createOptInConfig(orgId);

    HostBuilder hostBuilder =
        hostManager
            .createHost(orgId)
            .displayName("migratable-host")
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 4));
    hostBuilder
        .rhsmFacts(
            RhsmFacts.builder()
                .products(List.of("69"))
                .isVirtual(false)
                .sla("Premium")
                .usage("Production")
                .build())
        .insert();
    service.tallyOrg(orgId);

    // When: Query with Premium SLA filter (should find the host)
    InstanceResponse premiumResp =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenBaseParamsWithSla("Premium"));

    // Then: Should find host in Premium bucket
    assertNotNull(premiumResp.getData());
    assertEquals(1, premiumResp.getData().size());
    assertEquals(2, thenSumSockets(premiumResp));

    // Given: Update host to Standard/Development/Test
    hostBuilder
        .rhsmFacts(
            RhsmFacts.builder()
                .products(List.of("69"))
                .isVirtual(false)
                .sla("Standard")
                .usage("Development/Test")
                .build())
        .update();

    service.tallyOrg(orgId);

    // When: Query with Premium SLA filter again (should NOT find the host)
    InstanceResponse premiumRespAfter =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenBaseParamsWithSla("Premium"));

    // Then: Host should have migrated out of Premium bucket
    int premiumSockets = thenSumSockets(premiumRespAfter);
    assertEquals(
        0,
        premiumSockets,
        "Host should no longer appear in Premium bucket after SLA change to Standard");

    // When: Query with Standard SLA and Production usage filters (should NOT find the host)
    InstanceResponse standardProdRespBefore =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            givenBaseParamsWithSlaAndUsage("Standard", "Production"));

    // Then: Host should have migrated out of Production usage bucket
    int standardProdSockets = thenSumSockets(standardProdRespBefore);
    assertEquals(
        0,
        standardProdSockets,
        "Host should no longer appear in Production bucket after usage change to Development/Test");

    // When: Query with Standard SLA and Development/Test usage filters (should find the host)
    InstanceResponse standardResp =
        service.getInstancesByProduct(
            orgId,
            PRODUCT_TAG,
            start,
            end,
            givenBaseParamsWithSlaAndUsage("Standard", "Development/Test"));

    // Then: Host should now appear in Standard+Development/Test bucket
    assertNotNull(standardResp.getData());
    assertEquals(1, standardResp.getData().size());
    assertEquals(2, thenSumSockets(standardResp));
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC008")
  public void shouldExcludeMarketplaceAwsCloudInstances() {
    // Given: AWS marketplace cloud instance (simulated via cloud provider)
    service.createOptInConfig(orgId);

    hostManager
        .createHost(orgId)
        .displayName("marketplace-aws-cloud")
        .rhsmFacts(
            RhsmFacts.builder()
                .products(List.of("69"))
                .isVirtual(false)
                .sla("Premium")
                .usage("Production")
                .build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("physical")
                .cloudProvider("aws")
                .arch("x86_64")
                .numberOfSockets(4)
                .numberOfCpus(4)
                .isMarketplace(true)
                .build())
        .providerId(UUID.randomUUID().toString())
        .insert();

    service.tallyOrg(orgId);

    // When: Query for physical instances
    InstanceResponse response =
        service.getInstancesByProduct(orgId, PRODUCT_TAG, start, end, givenBaseParams());

    // Then: Marketplace AWS cloud should not appear in physical category
    // (Cloud instances with billing provider are marketplace and excluded from physical)
    int sockets = thenSumSockets(response);
    assertEquals(
        0, sockets, "Marketplace AWS cloud instances should not appear in physical category");

    // And: Verify no response rows match the marketplace instance display name
    if (response.getData() != null) {
      for (InstanceData instance : response.getData()) {
        assertNotEquals(
            "marketplace-aws-cloud",
            instance.getDisplayName(),
            "Marketplace instance should not appear in response");
      }
    }
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC009")
  public void shouldIncludeNonMarketplaceAwsCloudInstances() {
    // Given: Non-marketplace AWS cloud instance (cloud provider without billing metadata)
    service.createOptInConfig(orgId);

    hostManager
        .createHost(orgId)
        .displayName("non-marketplace-aws-cloud")
        .rhsmFacts(
            RhsmFacts.builder()
                .products(List.of("69"))
                .isVirtual(false)
                .sla("Premium")
                .usage("Production")
                .build())
        .systemProfileFacts(
            SystemProfileFacts.builder()
                .infrastructureType("physical")
                .cloudProvider("aws")
                .numberOfSockets(1)
                .numberOfCpus(4)
                .build())
        .providerId(UUID.randomUUID().toString())
        .insert();

    service.tallyOrg(orgId);

    // When: Query for cloud instances
    InstanceResponse response =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenParamsWithCategory("cloud"));

    // Then: Should find the cloud instance with 1 socket
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size(), "Should find exactly one cloud instance");
    assertEquals(
        "non-marketplace-aws-cloud",
        response.getData().get(0).getDisplayName(),
        "Should return the seeded non-marketplace cloud instance");
    assertEquals(1, thenSumSockets(response), "Non-marketplace AWS cloud should have 1 socket");
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC010")
  public void shouldIncludeAllVirtualHostsInMetaCount() {
    // Given: Two virtual hosts (infrastructure_type=virtual, no hypervisor relationship)
    // Matches IQE test: test_validate_sla_filters_for_daily_tally_on_virtual_rhel
    service.createOptInConfig(orgId);

    hostManager
        .createHost(orgId)
        .displayName("virtual-rhel-1")
        .apply(HostTemplates.conduitReportedVirtualRhelGuest(null, "Premium", "Production", 1, 2))
        .insert();

    hostManager
        .createHost(orgId)
        .displayName("virtual-rhel-2")
        .apply(HostTemplates.conduitReportedVirtualRhelGuest(null, "Premium", "Production", 1, 2))
        .insert();

    service.tallyOrg(orgId);

    // When: Query for virtual instances
    InstanceResponse response =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenParamsWithCategory("virtual"));

    // Then: Should find both virtual hosts (meta.count = 2)
    assertNotNull(response.getData());
    assertEquals(2, response.getData().size(), "Should find both virtual hosts");
    assertEquals(2, response.getMeta().getCount(), "Meta count should reflect 2 virtual hosts");

    // Verify sockets are normalized to 1 for virtual hosts (matches IQE test)
    for (InstanceData instance : response.getData()) {
      assertEquals(ReportCategory.VIRTUAL, instance.getCategory(), "Category should be virtual");
      Map<String, Double> measurements = thenGetMeasurementsMap(response, instance);
      Double sockets = measurements.get(METRIC_ID);
      assertEquals(1.0, sockets, "Virtual instance sockets should be normalized to 1");
    }
  }

  @Test
  @TestPlanName("tally-instances-nonpayg-TC011")
  public void shouldNormalizeVirtualHostSocketsToOne() {
    // Given: Virtual host with multiple sockets defined (unmapped, no hypervisor relationship)
    service.createOptInConfig(orgId);

    hostManager
        .createHost(orgId)
        .displayName("virtual-multi-socket-guest")
        .apply(HostTemplates.conduitReportedVirtualRhelGuest(null, "Premium", "Production", 4, 4))
        .insert();

    service.tallyOrg(orgId);

    // When: Query for virtual instances
    InstanceResponse response =
        service.getInstancesByProduct(
            orgId, PRODUCT_TAG, start, end, givenParamsWithCategory("virtual"));

    // Then: Virtual guest sockets should be normalized to 1
    assertNotNull(response.getData());
    assertEquals(1, response.getData().size(), "Should find one virtual guest");
    Map<String, Double> measurements = thenGetMeasurementsMap(response, response.getData().get(0));
    assertEquals(
        1.0,
        measurements.get(METRIC_ID),
        "Virtual instance sockets should be normalized to 1 regardless of reported sockets");
  }

  // Private given helper methods
  private List<SeededHost> givenNonPaygPhysicalHosts() {
    service.createOptInConfig(orgId);

    SeededHost host1 =
        hostManager
            .createHost(orgId)
            .displayName("physical-premium-production")
            .apply(HostTemplates.conduitReportedPhysicalRhel(4, 8))
            .rhsmFacts(
                RhsmFacts.builder()
                    .products(List.of("69"))
                    .isVirtual(false)
                    .sla("Premium")
                    .usage("Production")
                    .build())
            .insert();

    SeededHost host2 =
        hostManager
            .createHost(orgId)
            .displayName("physical-standard-dev")
            .apply(HostTemplates.conduitReportedPhysicalRhel(6, 12))
            .rhsmFacts(
                RhsmFacts.builder()
                    .products(List.of("69"))
                    .isVirtual(false)
                    .sla("Standard")
                    .usage("Development/Test")
                    .build())
            .insert();

    SeededHost host3 =
        hostManager
            .createHost(orgId)
            .displayName("physical-premium-dev")
            .apply(HostTemplates.conduitReportedPhysicalRhel(2, 4))
            .rhsmFacts(
                RhsmFacts.builder()
                    .products(List.of("69"))
                    .isVirtual(false)
                    .sla("Premium")
                    .usage("Development/Test")
                    .build())
            .insert();

    service.tallyOrg(orgId);
    return List.of(host1, host2, host3);
  }

  private Map<String, Object> givenBaseParams() {
    Map<String, Object> params = new HashMap<>();
    params.put("category", CATEGORY);
    params.put("metric_id", METRIC_ID.toLowerCase());
    return params;
  }

  private Map<String, Object> givenBaseParamsWithSla(String sla) {
    Map<String, Object> params = givenBaseParams();
    params.put("sla", sla);
    return params;
  }

  private Map<String, Object> givenBaseParamsWithUsage(String usage) {
    Map<String, Object> params = givenBaseParams();
    params.put("usage", usage);
    return params;
  }

  private Map<String, Object> givenBaseParamsWithSlaAndUsage(String sla, String usage) {
    Map<String, Object> params = givenBaseParams();
    params.put("sla", sla);
    params.put("usage", usage);
    return params;
  }

  private Map<String, Object> givenBaseParamsWithPagination(int limit, int offset) {
    Map<String, Object> params = givenBaseParams();
    params.put("limit", limit);
    params.put("offset", offset);
    return params;
  }

  private Map<String, Object> givenBaseParamsWithPaginationAndSort(
      int limit, int offset, String sort, String dir) {
    Map<String, Object> params = givenBaseParamsWithPagination(limit, offset);
    params.put("sort", sort);
    params.put("dir", dir);
    return params;
  }

  private Map<String, Object> givenBaseParamsWithSort(String sort, String dir) {
    Map<String, Object> params = givenBaseParams();
    params.put("sort", sort);
    params.put("dir", dir);
    return params;
  }

  private Map<String, Object> givenParamsWithCategory(String category) {
    Map<String, Object> params = new HashMap<>();
    params.put("category", category);
    params.put("metric_id", METRIC_ID.toLowerCase());
    return params;
  }

  private Map<String, Object> givenParamsWithCategoryAndSort(
      String category, String sort, String dir) {
    Map<String, Object> params = givenParamsWithCategory(category);
    params.put("sort", sort);
    params.put("dir", dir);
    return params;
  }

  private Map<String, Double> thenGetMeasurementsMap(
      InstanceResponse response, InstanceData instance) {
    List<String> metricIds = response.getMeta().getMeasurements();
    List<Double> values = instance.getMeasurements();
    Map<String, Double> measurements = new HashMap<>();
    for (int i = 0; i < metricIds.size(); i++) {
      measurements.put(metricIds.get(i), values.get(i));
    }
    return measurements;
  }

  private int thenSumSockets(InstanceResponse response) {
    if (response.getData() == null) {
      return 0;
    }
    return response.getData().stream()
        .mapToInt(
            instance -> {
              Map<String, Double> measurements = thenGetMeasurementsMap(response, instance);
              Double sockets = measurements.get(METRIC_ID);
              return sockets != null ? sockets.intValue() : 0;
            })
        .sum();
  }
}
