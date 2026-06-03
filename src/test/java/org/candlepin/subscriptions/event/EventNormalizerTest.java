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
    assertNull(result.getProductTag(), "Product tag should be null when no PAYG tags remain");
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
  void flattenEventUsageCreatesCartesianProduct() {
    // Given: Event with 2 tags and 2 measurements
    Event event = createEvent();
    event.setProductTag(Set.of("tag1", "tag2"));
    event.setMeasurements(List.of(createMeasurement(VCPUS, 1.0), createMeasurement(SOCKETS, 2.0)));

    // When: Flattening the event
    List<Event> flattened = normalizer.flattenEventUsage(event);

    // Then: Creates 2x2=4 events (cartesian product)
    assertEquals(4, flattened.size(), "Should create cartesian product of tags x measurements");

    // Verify each flattened event has exactly 1 tag and 1 measurement
    for (Event flattenedEvent : flattened) {
      assertEquals(1, flattenedEvent.getProductTag().size(), "Each event should have 1 tag");
      assertEquals(
          1, flattenedEvent.getMeasurements().size(), "Each event should have 1 measurement");
    }
  }

  // Helper methods

  private Event createEvent() {
    Event event = new Event();
    event.setOrgId("test-org");
    event.setInstanceId("test-instance");
    event.setServiceType("RHEL System");
    return event;
  }

  private Measurement createMeasurement(MetricId metricId, double value) {
    Measurement measurement = new Measurement();
    measurement.setMetricId(metricId.toUpperCaseFormatted());
    measurement.setValue(value);
    return measurement;
  }
}
