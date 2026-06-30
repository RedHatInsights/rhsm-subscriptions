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
package org.candlepin.subscriptions.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.util.List;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventNormalizerTest {

  private static final MetricId SOCKETS = MetricIdUtils.getSockets();
  private static final MetricId VCPUS = MetricIdUtils.getVCpus();
  private static final MetricId CORES = MetricIdUtils.getCores();

  private EventNormalizer normalizer;

  @BeforeEach
  void setUp() {
    // ResolvedEventMapper is a MapStruct interface, so we need to mock it.
    ResolvedEventMapper mapper = mock(ResolvedEventMapper.class);
    normalizer = new EventNormalizer(mapper);
  }

  @Test
  void normalizeEventWithMixedPaygoAndTraditionalTagsFiltersTraditionalTags() {
    // Given: Event with both PAYG and TRADITIONAL product tags
    Event event = createEvent();
    event.setProductTag(
        Set.of("rhel-for-x86-els-payg-addon", "rhel-for-x86-els-unconverted")); // mixed
    event.setMeasurements(List.of(createMeasurement(VCPUS, 4.0), createMeasurement(SOCKETS, 2.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Only PAYG tags remain
    assertNotNull(result.getProductTag(), "Product tags should not be null");
    assertEquals(1, result.getProductTag().size(), "Should have only PAYG tag remaining");
    assertTrue(
        result.getProductTag().contains("rhel-for-x86-els-payg-addon"), "Should keep PAYG tag");

    // And: Only measurements supported by PAYG tags remain
    assertNotNull(result.getMeasurements(), "Measurements should not be null");
    assertEquals(
        1, result.getMeasurements().size(), "Should have only vCPUs measurement remaining");
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void normalizeEventWithOnlyTraditionalTagsClearsProductTag() {
    // Given: Event with only TRADITIONAL product tags
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-unconverted")); // TRADITIONAL only
    event.setMeasurements(List.of(createMeasurement(SOCKETS, 2.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Product tag is cleared (set to null)
    assertTrue(
        result.getProductTag().isEmpty(), "Product tags should be empty when no PAYG tags remain");
  }

  @Test
  void normalizeEventWithOnlyPaygoTagsNoFiltering() {
    // Given: Event with only PAYG product tags
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon")); // PAYG only
    event.setMeasurements(List.of(createMeasurement(VCPUS, 4.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Tags and measurements unchanged
    assertNotNull(result.getProductTag());
    assertEquals(1, result.getProductTag().size());
    assertTrue(result.getProductTag().contains("rhel-for-x86-els-payg-addon"));
    assertNotNull(result.getMeasurements());
    assertEquals(1, result.getMeasurements().size());
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void normalizeEventWithNullProductTagNoChange() {
    // Given: Event with null product tag
    Event event = createEvent();
    event.setProductTag(null);
    event.setMeasurements(List.of(createMeasurement(VCPUS, 4.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Event unchanged
    assertNull(result.getProductTag());
    assertEquals(1, result.getMeasurements().size());
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void normalizeEventWithEmptyProductTagNoChange() {
    // Given: Event with empty product tag set
    Event event = createEvent();
    event.setProductTag(Set.of());
    event.setMeasurements(List.of(createMeasurement(VCPUS, 4.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Event unchanged
    assertTrue(result.getProductTag().isEmpty());
    assertEquals(1, result.getMeasurements().size());
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void normalizeEventWithMultiplePaygoTagsKeepsAllAndValidMeasurements() {
    // Given: Event with multiple PAYG tags
    Event event = createEvent();
    event.setProductTag(
        Set.of("rhel-for-x86-els-payg-addon", "rhel-for-x86-els-payg")); // both PAYG
    event.setMeasurements(List.of(createMeasurement(VCPUS, 4.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: All PAYG tags remain
    assertNotNull(result.getProductTag());
    assertEquals(2, result.getProductTag().size());
    assertTrue(result.getProductTag().contains("rhel-for-x86-els-payg-addon"));
    assertTrue(result.getProductTag().contains("rhel-for-x86-els-payg"));
    assertEquals(1, result.getMeasurements().size());
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void normalizeEventFiltersUnsupportedMeasurementsOnly() {
    // Given: Event with PAYG tag and mix of valid/invalid measurements
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon")); // Supports vCPUs only
    event.setMeasurements(
        List.of(
            createMeasurement(VCPUS, 4.0), // valid for PAYG
            createMeasurement(SOCKETS, 2.0), // invalid for PAYG
            createMeasurement(CORES, 8.0))); // invalid for PAYG

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Only valid measurements remain
    assertNotNull(result.getMeasurements());
    assertEquals(1, result.getMeasurements().size(), "Should filter out invalid measurements");
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void normalizeEventWithNullMeasurementsNoError() {
    // Given: Event with PAYG tag but null measurements
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon"));
    event.setMeasurements(null);

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: No error, measurements remain null
    assertNull(result.getMeasurements());
  }

  @Test
  void normalizeEventWithEmptyMeasurementsNoError() {
    // Given: Event with PAYG tag but empty measurements
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon"));
    event.setMeasurements(List.of());

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: No error, measurements remain empty
    assertTrue(result.getMeasurements().isEmpty());
  }

  @Test
  void normalizeEventAnsibleInfrastructureHourConvertsServiceType() {
    // Given: Event with "Ansible Infrastructure Hour" service type
    Event event = createEvent();
    event.setServiceType("Ansible Infrastructure Hour");
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon"));
    event.setMeasurements(List.of(createMeasurement(MetricIdUtils.getVCpus(), 4.0)));

    // When: Normalizing the event
    Event result = normalizer.normalizeEvent(event);

    // Then: Service type is converted
    assertEquals(
        "Ansible Managed Node",
        result.getServiceType(),
        "Service type should be converted from 'Ansible Infrastructure Hour' to 'Ansible Managed Node'");
  }

  @Test
  void flattenEventUsageWithSinglePaygoProductAndTwoValidMeasurements() {
    // Given: Event with single PAYG tag supporting both Cores and Instance-hours
    // OpenShift-dedicated-metrics supports both metrics
    Event event = createEvent();
    event.setProductTag(Set.of("OpenShift-dedicated-metrics"));
    event.setMeasurements(
        List.of(
            createMeasurement(CORES, 8.0),
            createMeasurement(MetricIdUtils.getInstanceHours(), 24.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 2 events (one per measurement, both valid for this tag)
    assertEquals(2, flattened.size());

    // Find the event for each measurement
    Event coresEvent =
        flattened.stream()
            .filter(
                e ->
                    e.getMeasurements()
                        .getFirst()
                        .getMetricId()
                        .equals(CORES.toUpperCaseFormatted()))
            .findFirst()
            .orElseThrow();
    Event instanceHoursEvent =
        flattened.stream()
            .filter(
                e ->
                    e.getMeasurements()
                        .getFirst()
                        .getMetricId()
                        .equals(MetricIdUtils.getInstanceHours().toUpperCaseFormatted()))
            .findFirst()
            .orElseThrow();

    // Verify Cores event
    assertEquals(1, coresEvent.getProductTag().size());
    assertTrue(coresEvent.getProductTag().contains("OpenShift-dedicated-metrics"));
    assertEquals(1, coresEvent.getMeasurements().size());
    assertEquals(8.0, coresEvent.getMeasurements().getFirst().getValue());

    // Verify Instance-hours event
    assertEquals(1, instanceHoursEvent.getProductTag().size());
    assertTrue(instanceHoursEvent.getProductTag().contains("OpenShift-dedicated-metrics"));
    assertEquals(1, instanceHoursEvent.getMeasurements().size());
    assertEquals(24.0, instanceHoursEvent.getMeasurements().getFirst().getValue());
  }

  @Test
  void flattenEventUsageWithTwoPaygoProductsEachWithOneMeasurement() {
    // Given: Event with 2 PAYG tags and 2 measurements, but each tag only supports one
    // OpenShift-metrics supports only Cores (not Instance-hours)
    // OpenShift-dedicated-metrics supports both Cores and Instance-hours
    Event event = createEvent();
    event.setProductTag(Set.of("OpenShift-metrics", "OpenShift-dedicated-metrics"));
    event.setMeasurements(
        List.of(
            createMeasurement(CORES, 8.0),
            createMeasurement(MetricIdUtils.getInstanceHours(), 24.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 3 events total
    // OpenShift-metrics + Cores (valid)
    // OpenShift-dedicated-metrics + Cores (valid)
    // OpenShift-dedicated-metrics + Instance-hours (valid)
    // OpenShift-metrics + Instance-hours is filtered out (invalid)
    assertEquals(3, flattened.size());

    // Verify all events have exactly 1 tag and 1 measurement
    for (Event result : flattened) {
      assertEquals(1, result.getProductTag().size());
      assertEquals(1, result.getMeasurements().size());
    }

    // Count events per tag
    long openshiftMetricsCount =
        flattened.stream().filter(e -> e.getProductTag().contains("OpenShift-metrics")).count();
    long openshiftDedicatedCount =
        flattened.stream()
            .filter(e -> e.getProductTag().contains("OpenShift-dedicated-metrics"))
            .count();

    assertEquals(1, openshiftMetricsCount, "OpenShift-metrics should have 1 event (Cores only)");
    assertEquals(
        2,
        openshiftDedicatedCount,
        "OpenShift-dedicated-metrics should have 2 events (Cores and Instance-hours)");

    // Verify OpenShift-metrics event only has Cores
    Event openshiftMetricsEvent =
        flattened.stream()
            .filter(e -> e.getProductTag().contains("OpenShift-metrics"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        CORES.toUpperCaseFormatted(),
        openshiftMetricsEvent.getMeasurements().getFirst().getMetricId());
  }

  @Test
  void flattenEventUsageWithSingleTagAndMixedValidInvalidMeasurements() {
    // Given: Event with PAYG tag supporting only vCPUs, but has Sockets and Cores too
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon"));
    event.setMeasurements(
        List.of(
            createMeasurement(VCPUS, 4.0),
            createMeasurement(SOCKETS, 2.0),
            createMeasurement(CORES, 8.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create only 1 event (only vCPUs is valid for this tag)
    assertEquals(1, flattened.size());

    Event result = flattened.getFirst();
    assertEquals(1, result.getProductTag().size());
    assertTrue(result.getProductTag().contains("rhel-for-x86-els-payg-addon"));
    assertEquals(1, result.getMeasurements().size());
    assertEquals(VCPUS.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, result.getMeasurements().getFirst().getValue());
  }

  @Test
  void flattenEventUsageWithMultipleTagsEachSupportingDifferentMeasurements() {
    // Given: Event with 2 PAYG tags, each supporting a different metric
    // OpenShift-metrics supports only Cores
    // rhel-for-x86-els-payg-addon supports only vCPUs
    Event event = createEvent();
    event.setProductTag(Set.of("OpenShift-metrics", "rhel-for-x86-els-payg-addon"));
    event.setMeasurements(List.of(createMeasurement(CORES, 8.0), createMeasurement(VCPUS, 4.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 2 events (one per tag with its supported metric)
    assertEquals(2, flattened.size());

    // Find the event for each tag
    Event openshiftEvent =
        flattened.stream()
            .filter(e -> e.getProductTag().contains("OpenShift-metrics"))
            .findFirst()
            .orElseThrow();
    Event rhelEvent =
        flattened.stream()
            .filter(e -> e.getProductTag().contains("rhel-for-x86-els-payg-addon"))
            .findFirst()
            .orElseThrow();

    // Verify OpenShift-metrics event has only Cores
    assertEquals(1, openshiftEvent.getProductTag().size());
    assertEquals(1, openshiftEvent.getMeasurements().size());
    assertEquals(
        CORES.toUpperCaseFormatted(), openshiftEvent.getMeasurements().getFirst().getMetricId());
    assertEquals(8.0, openshiftEvent.getMeasurements().getFirst().getValue());

    // Verify rhel-for-x86-els-payg-addon event has only vCPUs
    assertEquals(1, rhelEvent.getProductTag().size());
    assertEquals(1, rhelEvent.getMeasurements().size());
    assertEquals(
        VCPUS.toUpperCaseFormatted(), rhelEvent.getMeasurements().getFirst().getMetricId());
    assertEquals(4.0, rhelEvent.getMeasurements().getFirst().getValue());
  }

  @Test
  void flattenEventUsageWithMultipleTagsWithOverlappingMeasurementSupport() {
    // Given: Event with 2 PAYG tags that both support Cores, plus an unsupported measurement
    // OpenShift-metrics supports only Cores
    // OpenShift-dedicated-metrics supports Cores and Instance-hours
    Event event = createEvent();
    event.setProductTag(Set.of("OpenShift-metrics", "OpenShift-dedicated-metrics"));
    event.setMeasurements(List.of(createMeasurement(CORES, 8.0), createMeasurement(VCPUS, 4.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 2 events (both tags with Cores, vCPUs filtered out)
    assertEquals(2, flattened.size());

    for (Event result : flattened) {
      assertEquals(1, result.getProductTag().size());
      assertEquals(1, result.getMeasurements().size());
      assertEquals(CORES.toUpperCaseFormatted(), result.getMeasurements().getFirst().getMetricId());
      assertEquals(8.0, result.getMeasurements().getFirst().getValue());
    }

    // Verify both tags are present
    long openshiftMetricsCount =
        flattened.stream().filter(e -> e.getProductTag().contains("OpenShift-metrics")).count();
    long openshiftDedicatedCount =
        flattened.stream()
            .filter(e -> e.getProductTag().contains("OpenShift-dedicated-metrics"))
            .count();

    assertEquals(1, openshiftMetricsCount);
    assertEquals(1, openshiftDedicatedCount);
  }

  @Test
  void flattenEventUsageWithTagHavingNoValidMeasurements() {
    // Given: Event with PAYG tag that only supports vCPUs, but only has Sockets and Cores
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon"));
    event.setMeasurements(List.of(createMeasurement(SOCKETS, 2.0), createMeasurement(CORES, 8.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 0 events (no valid combinations)
    assertTrue(flattened.isEmpty());
  }

  @Test
  void flattenEventUsageWithEmptyTags() {
    // Given: Event with empty tag set
    Event event = createEvent();
    event.setProductTag(Set.of());
    event.setMeasurements(List.of(createMeasurement(VCPUS, 4.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 0 events
    assertTrue(flattened.isEmpty());
  }

  @Test
  void flattenEventUsageWithEmptyMeasurements() {
    // Given: Event with empty measurements list
    Event event = createEvent();
    event.setProductTag(Set.of("rhel-for-x86-els-payg-addon"));
    event.setMeasurements(List.of());

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Should create 0 events
    assertTrue(flattened.isEmpty());
  }

  @Test
  void flattenEventUsageWithNullMeasurements() {
    Event event = createEvent();
    event.setMeasurements(null);
    assertTrue(normalizer.flattenEventUsage(event).isEmpty());
  }

  @Test
  void flattenEventUsageWithNullProductTags() {
    Event event = createEvent();
    event.setProductTag(null);
    assertTrue(normalizer.flattenEventUsage(event).isEmpty());
  }

  // Helper methods

  private Event createEvent() {
    Event event = new Event();
    event.setOrgId("test-org");
    event.setInstanceId("test-instance");
    event.setServiceType("RHEL System");
    event.setMeasurements(List.of());
    event.setProductTag(Set.of());
    return event;
  }

  private Measurement createMeasurement(MetricId metricId, double value) {
    Measurement measurement = new Measurement();
    measurement.setMetricId(metricId.toUpperCaseFormatted());
    measurement.setValue(value);
    return measurement;
  }
}
