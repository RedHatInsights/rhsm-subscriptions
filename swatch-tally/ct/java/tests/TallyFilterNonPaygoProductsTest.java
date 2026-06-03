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
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_PAYG_ADDON;
import static utils.TallyTestProducts.RHEL_FOR_X86_ELS_UNCONVERTED;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.tally.test.model.InstanceData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Component tests verifying that hourly tally correctly filters out non-PAYG product tags from
 * events.
 *
 * <p>These tests ensure that when events contain mixed PAYG and TRADITIONAL product tags, only the
 * PAYG tags are processed during hourly tally operations. This prevents TRADITIONAL products from
 * being incorrectly included in marketplace billing.
 */
public class TallyFilterNonPaygoProductsTest extends BaseTallyComponentTest {
  private TestSetup setup;

  @BeforeEach
  public void setUp() {
    super.setUp();
    setup = setupTest();
  }

  @Test
  @TestPlanName("tally-payg-filter-TC001")
  public void testHourlyTallyFiltersNonPaygoProductTags() {
    // Given: An event with mixed PAYG and TRADITIONAL product tags with realistic metrics
    // rhel-for-x86-els-payg-addon is PAYG (supports vCPUs metric)
    // rhel-for-x86-els-unconverted is TRADITIONAL (supports Sockets metric)
    // Event includes BOTH metrics (vCPUs for PAYG, Sockets for TRADITIONAL)
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    float vcpuValue = 4.0f;
    float socketsValue = 2.0f;

    createRealisticMixedTagsEvent(setup.start, vcpuValue, socketsValue);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: Only the PAYG product should have tally data
    // PAYG product should have the vCPUs metric tallied
    awaitHourlyTallySum(
        setup.orgId,
        paygoProductTag,
        "vCPUs",
        setup.start,
        setup.start.plusHours(1),
        (double) vcpuValue);

    // Note: We cannot query hourly tally reports for TRADITIONAL products as they don't support
    // hourly granularity. The filtering is verified by checking instances reports instead.

    // And: Verify instance was created for PAYGO product with correct measurements
    InstanceData paygoInstance =
        getInstanceByDisplayName(
            setup.orgId, paygoProductTag, setup.start, setup.start.plusHours(1), setup.instanceId);
    assertEquals(true, paygoInstance != null, "PAYGO instance should be created from the event");
    assertEquals(
        setup.instanceId,
        paygoInstance.getDisplayName(),
        "PAYGO instance should have correct display name");
    assertEquals(
        true,
        paygoInstance.getMeasurements() != null && !paygoInstance.getMeasurements().isEmpty(),
        "PAYGO instance should have measurements applied from the event");
    assertEquals(
        1,
        paygoInstance.getMeasurements().size(),
        "PAYGO instance should have only vCPUs measurement, not Sockets (filtered out)");
    assertEquals(
        (double) vcpuValue,
        paygoInstance.getMeasurements().get(0),
        0.0001,
        "PAYGO instance should have correct vCPUs measurement value from the original event");

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
  @TestPlanName("tally-payg-filter-TC002")
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
  @TestPlanName("tally-payg-filter-TC003")
  public void testEventWithOnlyPaygoTagsProcessedNormally() {
    // Given: An event with ONLY PAYG product tags (no TRADITIONAL tags)
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String metricId = RHEL_FOR_X86_ELS_PAYG_ADDON.metricIds().get(0); // vCPUs
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

    long paygoInstanceCount =
        getInstancesCountByDisplayName(
            setup.orgId, paygoProductTag, setup.start, setup.start.plusHours(1), setup.instanceId);
    assertEquals(
        1, paygoInstanceCount, "PAYG-only event should be processed normally in hourly tally");
  }

  @Test
  @TestPlanName("tally-payg-filter-TC004")
  public void testMultipleEventsWithMixedTagsFilteredCorrectly() {
    // Given: Multiple events with different tag combinations
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    String metricId = RHEL_FOR_X86_ELS_PAYG_ADDON.metricIds().get(0); // vCPUs

    // Event 1: Mixed tags - value 4.0
    createMixedTagsEvent(
        setup.start, Set.of(paygoProductTag, traditionalProductTag), metricId, 4.0f);

    // Event 2: Mixed tags at a different time in same hour - value 2.0
    createMixedTagsEvent(
        setup.start.plusMinutes(30),
        Set.of(paygoProductTag, traditionalProductTag),
        metricId,
        2.0f);

    // Event 3: PAYG only - value 6.0
    createMixedTagsEvent(setup.start.plusMinutes(45), Set.of(paygoProductTag), metricId, 6.0f);

    // When: Performing hourly tally
    service.performHourlyTallyForOrg(setup.orgId);

    // Then: PAYG product should have the max value (6.0) from the latest event
    // Tally uses max value per instance per hour for conflicting events
    awaitHourlyTallySum(
        setup.orgId, paygoProductTag, metricId, setup.start, setup.start.plusHours(1), 6.0);

    // TRADITIONAL product should have NO instances in hourly report (filtered out from all events)
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
  @TestPlanName("tally-payg-filter-TC005")
  public void testConflictResolutionWithMixedTags() {
    // Given: First event with mixed PAYG and TRADITIONAL tags with realistic metrics
    String paygoProductTag = RHEL_FOR_X86_ELS_PAYG_ADDON.productTag();
    String traditionalProductTag = RHEL_FOR_X86_ELS_UNCONVERTED.productTag();
    float firstVcpuValue = 4.0f;
    float firstSocketsValue = 2.0f;
    float secondVcpuValue = 8.0f;
    float secondSocketsValue = 4.0f;

    createRealisticMixedTagsEvent(setup.start, firstVcpuValue, firstSocketsValue);

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
    assertEquals(
        (double) firstVcpuValue,
        firstTally,
        0.0001,
        "First tally should reflect initial vCPU value");

    // Given: Second conflicting event with mixed tags for SAME instance and SAME timestamp (higher
    // values)
    createRealisticMixedTagsEvent(
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
            (double) secondVcpuValue);
    assertEquals(
        (double) secondVcpuValue,
        secondTally,
        0.0001,
        "Conflict resolution should update to higher vCPU value from second event");

    // And: Verify instance has updated vCPU measurement after conflict resolution
    InstanceData paygoInstance =
        getInstanceByDisplayName(
            setup.orgId, paygoProductTag, setup.start, setup.start.plusHours(1), setup.instanceId);
    assertEquals(
        true, paygoInstance != null, "PAYGO instance should exist after conflict resolution");
    assertEquals(
        1,
        paygoInstance.getMeasurements().size(),
        "PAYGO instance should have only vCPUs measurement after conflict resolution");
    assertEquals(
        (double) secondVcpuValue,
        paygoInstance.getMeasurements().get(0),
        0.0001,
        "Instance vCPU measurement should be updated to second (higher) value after conflict resolution");

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
  @TestPlanName("tally-payg-filter-TC006")
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
        setup.orgId,
        paygoProductTag,
        "vCPUs",
        setup.start,
        setup.start.plusHours(1),
        (double) vcpuValue);

    // And: PAYG instance should exist with correct measurement
    InstanceData paygoInstance =
        getInstanceByDisplayName(
            setup.orgId, paygoProductTag, setup.start, setup.start.plusHours(1), setup.instanceId);
    assertEquals(
        true,
        paygoInstance != null,
        "PAYGO instance should be created even with incomplete metrics");
    assertEquals(
        (double) vcpuValue,
        paygoInstance.getMeasurements().get(0),
        0.0001,
        "PAYGO instance should have vCPUs measurement");

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

  /**
   * Creates a realistic event with mixed PAYG and TRADITIONAL tags, including appropriate metrics
   * for each product type.
   *
   * @param timestamp event timestamp
   * @param vcpuValue vCPUs value (for PAYG product: rhel-for-x86-els-payg-addon)
   * @param socketsValue Sockets value (for TRADITIONAL product: rhel-for-x86-els-unconverted)
   */
  private void createRealisticMixedTagsEvent(
      OffsetDateTime timestamp, float vcpuValue, float socketsValue) {
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
