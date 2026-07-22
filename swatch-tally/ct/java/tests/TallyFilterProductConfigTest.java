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

import static com.redhat.swatch.component.tests.utils.Topics.SWATCH_SERVICE_INSTANCE_INGRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static utils.TallyTestProducts.RHEL_FOR_X86;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG_ADDON;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_UNCONVERTED;
import static utils.TallyTestProducts.ROSA;

import com.redhat.swatch.component.tests.api.TestPlanName;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TallyFilterProductConfigTest extends BaseTallyComponentTest {
  private TestSetup setup;

  @BeforeEach
  public void setUp() {
    super.setUp();
    setup = setupTest();
  }

  @Test
  @TestPlanName("tally-product-filter-TC001")
  public void testHourlyTallyFiltersNonPaygoProductTags() {
    // Given: An event with mixed PAYG and TRADITIONAL product tags with realistic metrics
    // rhel-for-x86-els-payg-addon is PAYG (supports vCPUs metric)
    // rhel-for-x86-els-unconverted is TRADITIONAL (supports Sockets metric)
    // Event includes BOTH metrics (vCPUs for PAYG, Sockets for TRADITIONAL)
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    float vcpuValue = 4.0f;
    float socketsValue = 2.0f;

    createMixedTagsEvent(setup.start, vcpuValue, socketsValue);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Only the PAYG product should have tally data
    // PAYG product should have the vCPUs metric tallied
    awaitHourlyTallySum(
        setup.orgId, paygoProductTag, "vCPUs", setup.start, setup.start.plusHours(1), vcpuValue);

    // Note: We cannot query hourly tally reports for TRADITIONAL products as they don't support
    // hourly granularity. The filtering is verified by checking instance reports instead.

    // And: Verify instance was created for PAYGO product with correct measurements
    // Verifies: product tag, metric ID (vCPUs not Sockets), and value
    assertInstanceMeasurements(
        setup.orgId,
        setup.instanceId,
        paygoProductTag,
        setup.start,
        setup.start.plusHours(1),
        Map.of("vCPUs", (double) vcpuValue));

    // And: TRADITIONAL product should have NO instances in hourly report
    long traditionalInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId,
            traditionalProductTag,
            setup.start,
            setup.start.plusHours(1),
            setup.instanceId);
    assertEquals(
        0,
        traditionalInstanceCount,
        "TRADITIONAL product should have no instances in the hourly instances report");
  }

  @Test
  @TestPlanName("tally-product-filter-TC002")
  public void testEventWithOnlyTraditionalTagsNotTalliedHourly() {
    // Given: An event with ONLY TRADITIONAL product tag (no PAYG tags)
    // For TRADITIONAL products, we don't set billing provider/account
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    String metricId = "Sockets";
    float value = 2.0f;

    Event event =
        helpers.createPaygEventWithTimestamp(
            setup.orgId,
            setup.instanceId,
            setup.start.toString(),
            UUID.randomUUID().toString(),
            metricId,
            value,
            Event.Sla.PREMIUM,
            Event.HardwareType.PHYSICAL,
            RHEL_FOR_X86_ELS_UNCONVERTED.productId(),
            traditionalProductTag);

    event.setDisplayName(Optional.of(setup.instanceId));
    // TRADITIONAL products don't have cloud billing - set to null or __EMPTY__
    event.setBillingProvider(Event.BillingProvider.__EMPTY__);
    event.setBillingAccountId(Optional.empty());

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Event should NOT be tallied hourly (product_tag should be cleared to null)
    // TRADITIONAL products don't support hourly tally reports, so we verify by checking
    // that the instance does not appear in the hourly instances report
    long traditionalInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId,
            traditionalProductTag,
            setup.start,
            setup.start.plusHours(1),
            setup.instanceId);
    assertEquals(
        0,
        traditionalInstanceCount,
        "Event with only TRADITIONAL tags should not appear in hourly instances report");
  }

  @Test
  @TestPlanName("tally-product-filter-TC003")
  public void testEventWithOnlyPaygoTagsProcessedNormally() {
    // Given: An event with ONLY PAYG product tags (no TRADITIONAL tags)
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String metricId = RHEL_FOR_X86_ELS_PAYG_ADDON.metricIds().getFirst(); // vCPUs
    float value = 4.0f;

    createMixedTagsEvent(setup.start, Set.of(paygoProductTag), metricId, value);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Event should be tallied normally (no filtering)
    awaitHourlyTallySum(
        setup.orgId,
        paygoProductTag,
        metricId,
        setup.start,
        setup.start.plusHours(1),
        (double) value);

    // And: Instance should exist with correct measurement value
    assertInstanceMeasurements(
        setup.orgId,
        setup.instanceId,
        paygoProductTag,
        setup.start,
        setup.start.plusHours(1),
        Map.of("vCPUs", (double) value));
  }

  @Test
  @TestPlanName("tally-product-filter-TC004")
  public void testMultipleEventsWithMixedTagsFilteredCorrectly() {
    // Given: Multiple events with different tag combinations at the same hour-truncated timestamp
    // (matching real-world behavior where events are sent with timestamps truncated to the hour)
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    String metricId = RHEL_FOR_X86_ELS_PAYG_ADDON.metricIds().get(0); // vCPUs

    // All events use same timestamp (hour-truncated) to match real-world usage
    // Event 1: Mixed tags - value 4.0
    createMixedTagsEvent(
        setup.start, Set.of(paygoProductTag, traditionalProductTag), metricId, 4.0f);

    // Event 2: Mixed tags - value 2.0 (conflicts with Event 1)
    createMixedTagsEvent(
        setup.start, Set.of(paygoProductTag, traditionalProductTag), metricId, 2.0f);

    // Event 3: PAYG only - value 6.0 (conflicts with Events 1 and 2)
    createMixedTagsEvent(setup.start, Set.of(paygoProductTag), metricId, 6.0f);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: PAYG product should have the max value (6.0) from the conflicting events
    // Events with same timestamp are conflicts; conflict resolution takes the max value
    awaitHourlyTallySum(
        setup.orgId, paygoProductTag, metricId, setup.start, setup.start.plusHours(1), 6.0);

    // And: Instance should have the max measurement value (6.0) after conflict resolution
    assertInstanceMeasurements(
        setup.orgId,
        setup.instanceId,
        paygoProductTag,
        setup.start,
        setup.start.plusHours(1),
        Map.of("vCPUs", 6.0));

    // And: TRADITIONAL product should have NO instances in hourly report (filtered out from all
    // events)
    long traditionalInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId,
            traditionalProductTag,
            setup.start,
            setup.start.plusHours(1),
            setup.instanceId);
    assertEquals(
        0,
        traditionalInstanceCount,
        "TRADITIONAL product should not appear in any of the hourly events");
  }

  @Test
  @TestPlanName("tally-product-filter-TC005")
  public void testConflictResolutionWithMixedTags() {
    // Given: First event with mixed PAYG and TRADITIONAL tags with realistic metrics
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    float firstVcpuValue = 4.0f;
    float firstSocketsValue = 2.0f;
    float secondVcpuValue = 8.0f;
    float secondSocketsValue = 4.0f;

    createMixedTagsEvent(setup.start, firstVcpuValue, firstSocketsValue);

    // When: Performing first hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: PAYGO product should have first tally with initial vCPU value
    double firstTally =
        awaitHourlyTallySum(
            setup.orgId,
            paygoProductTag,
            "vCPUs",
            setup.start,
            setup.start.plusHours(1),
            (double) firstVcpuValue);
    assertEquals(firstVcpuValue, firstTally, "First tally should reflect initial vCPU value");

    // Given: Second conflicting event with mixed tags for SAME instance and SAME timestamp (higher
    // values)
    createMixedTagsEvent(
        setup.start, // Same exact timestamp to trigger conflict resolution
        secondVcpuValue,
        secondSocketsValue);

    // When: Performing second hourly tally (triggers conflict resolution)
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: PAYGO product should be updated to the higher vCPU value (conflict resolved)
    double secondTally =
        awaitHourlyTallySum(
            setup.orgId,
            paygoProductTag,
            "vCPUs",
            setup.start,
            setup.start.plusHours(1),
            secondVcpuValue);
    assertEquals(
        secondVcpuValue,
        secondTally,
        "Conflict resolution should update to higher vCPU value from second event");

    // And: Verify instance has updated vCPU measurement after conflict resolution
    assertInstanceMeasurements(
        setup.orgId,
        setup.instanceId,
        paygoProductTag,
        setup.start,
        setup.start.plusHours(1),
        Map.of("vCPUs", (double) secondVcpuValue));

    // And: TRADITIONAL product should have NO instances in either tally run
    long traditionalInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId,
            traditionalProductTag,
            setup.start,
            setup.start.plusHours(1),
            setup.instanceId);
    assertEquals(
        0,
        traditionalInstanceCount,
        "TRADITIONAL product should not appear in any hourly tally, even with conflict resolution");
  }

  @Test
  @TestPlanName("tally-product-filter-TC006")
  public void testMixedTagsWithSingleMetricEdgeCase() {
    // Given: An unrealistic edge-case event with mixed PAYG and TRADITIONAL tags
    // but only includes one product's metric (vCPUs for PAYG, not Sockets for TRADITIONAL)
    // This tests that filtering works even with malformed/incomplete metric data
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    float vcpuValue = 4.0f;

    createMixedTagsEvent(
        setup.start, Set.of(paygoProductTag, traditionalProductTag), "vCPUs", vcpuValue);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: PAYG product should be tallied with the vCPUs metric
    awaitHourlyTallySum(
        setup.orgId, paygoProductTag, "vCPUs", setup.start, setup.start.plusHours(1), vcpuValue);

    // And: PAYG instance should exist with correct measurement
    assertInstanceMeasurements(
        setup.orgId,
        setup.instanceId,
        paygoProductTag,
        setup.start,
        setup.start.plusHours(1),
        Map.of("vCPUs", (double) vcpuValue));

    // And: TRADITIONAL product should have NO instances (filtered out)
    long traditionalInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId,
            traditionalProductTag,
            setup.start,
            setup.start.plusHours(1),
            setup.instanceId);
    assertEquals(
        0,
        traditionalInstanceCount,
        "TRADITIONAL product should be filtered out even with incomplete metrics");
  }

  @Test
  @TestPlanName("tally-product-filter-TC007")
  public void testRoleBasedProductTagLookupFiltersNonPaygoMetrics() {
    // Given: An event with NO product tag but WITH a role and mixed metrics
    // - Role "moa" maps to PAYG product "rosa" (supports Cores, Instance-hours)
    // - Event contains BOTH a PAYG metric (Cores) and a TRADITIONAL metric (Sockets)
    // - The role-based lookup should derive the PAYG product tag from the role
    // - The TRADITIONAL metric (Sockets) should be filtered out during normalization

    String expectedPaygoProductTag = ROSA.productTag(); // rosa
    String paygoMetricId = "Cores"; // Supported by rosa
    String traditionalMetricId = "Sockets"; // NOT supported by rosa
    float coresValue = 8.0f;
    float socketsValue = 2.0f;

    Event event = new Event();
    event.setEventId(UUID.randomUUID());
    event.setOrgId(setup.orgId);
    event.setInstanceId(setup.instanceId);
    event.setDisplayName(Optional.of(setup.instanceId));
    event.setTimestamp(setup.start);
    event.setRecordDate(setup.start);
    event.setExpiration(Optional.of(setup.start.plusHours(1)));
    event.setEventSource("test-role-lookup");
    event.setEventType("snapshot");

    // Critical: NO product tag set - this triggers role-based lookup
    event.setProductTag(Set.of());

    // Critical: Set role to "moa" which should map to rosa (PAYG product)
    event.setRole(Event.Role.MOA);

    // Add productIds as empty to force role-based lookup (not engId-based)
    event.setProductIds(List.of());

    event.setSla(Event.Sla.PREMIUM);
    event.setUsage(Event.Usage.PRODUCTION);
    event.setServiceType("rosa Instance");
    event.setBillingProvider(Event.BillingProvider.AWS);
    event.setBillingAccountId(Optional.of("aws-account-123"));
    event.setCloudProvider(Event.CloudProvider.AWS);

    // Add BOTH a PAYG-supported metric (Cores) and an unsupported metric (Sockets)
    List<Measurement> measurements = new ArrayList<>();

    Measurement cores = new Measurement();
    cores.setMetricId(paygoMetricId);
    cores.setValue((double) coresValue);
    measurements.add(cores);

    Measurement sockets = new Measurement();
    sockets.setMetricId(traditionalMetricId);
    sockets.setValue((double) socketsValue);
    measurements.add(sockets);

    event.setMeasurements(measurements);

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: The PAYG product tag (rosa) should be derived from the role (moa)
    // And: Only the supported metric (Cores) should be tallied, Sockets should be filtered out
    awaitHourlyTallySum(
        setup.orgId,
        expectedPaygoProductTag,
        paygoMetricId,
        setup.start,
        setup.start.plusHours(1),
        coresValue);

    assertInstanceMeasurements(
        setup.orgId,
        setup.instanceId,
        expectedPaygoProductTag,
        setup.start,
        setup.start.plusHours(1),
        Map.of("Cores", (double) coresValue, "Instance-hours", 0.0));

    // And: Verify NO instances exist for any RHEL product tag (which supports Sockets)
    // This ensures Sockets metric didn't cause a RHEL product to be incorrectly tallied
    long rhelInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId,
            RHEL_FOR_X86.productTag(),
            setup.start,
            setup.start.plusHours(1),
            setup.instanceId);
    assertEquals(
        0,
        rhelInstanceCount,
        "RHEL instance should NOT be created - role=moa should only map to rosa, not RHEL");
  }

  // --- Given helper methods ---

  private TestSetup setupTest() {
    service.createOptInConfig(orgId);

    // Use a fixed hour bucket so events collide (same instance_id + same hour)
    OffsetDateTime start =
        OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).truncatedTo(ChronoUnit.HOURS);
    String instanceId = UUID.randomUUID().toString();

    return new TestSetup(orgId, start, instanceId);
  }

  private void createMixedTagsEvent(
      OffsetDateTime timestamp, Set<String> productTags, String metricId, float value) {
    // Use the PAYG product configuration as the base
    Event event =
        helpers.createPaygEventWithTimestamp(
            setup.orgId,
            setup.instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            metricId,
            value,
            RHEL_FOR_X86_ELS_PAYG_ADDON.productId(),
            RHEL_FOR_X86_ELS_PAYG_ADDON.productTag());

    // Override product_tag to include both PAYG and TRADITIONAL tags
    event.setProductTag(productTags);
    event.setDisplayName(Optional.of(setup.instanceId));

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private void createMixedTagsEvent(OffsetDateTime timestamp, float vcpuValue, float socketsValue) {
    Event event =
        helpers.createPaygEventWithTimestamp(
            setup.orgId,
            setup.instanceId,
            timestamp.toString(),
            UUID.randomUUID().toString(),
            "vCPUs",
            vcpuValue,
            RHEL_FOR_X86_ELS_PAYG_ADDON.productId(),
            RHEL_FOR_X86_ELS_PAYG_ADDON.productTag());

    // Add both PAYG and TRADITIONAL tags
    event.setProductTag(
        Set.of(
            RHEL_FOR_X86_ELS_PAYG_ADDON.productTag(), RHEL_FOR_X86_ELS_UNCONVERTED.productTag()));

    // Add measurements for both products' metrics
    List<Measurement> measurements = new ArrayList<>();

    Measurement vcpus = new Measurement();
    vcpus.setMetricId("vCPUs");
    vcpus.setValue((double) vcpuValue);
    measurements.add(vcpus);

    Measurement sockets = new Measurement();
    sockets.setMetricId("Sockets");
    sockets.setValue((double) socketsValue);
    measurements.add(sockets);

    event.setMeasurements(measurements);
    event.setDisplayName(Optional.of(setup.instanceId));

    kafkaBridge.produceKafkaMessage(SWATCH_SERVICE_INSTANCE_INGRESS, event);
  }

  private record TestSetup(String orgId, OffsetDateTime start, String instanceId) {}
}
