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
package com.redhat.swatch.hbi.egress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.config.ApplicationComponentsProducer;
import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.MeasurementNormalizer;
import com.redhat.swatch.hbi.domain.normalization.NormalizedMeasurements;
import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.candlepin.subscriptions.json.Event;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Disabled
class SwatchEventBuilderTest {

  @Mock private MeasurementNormalizer measurementNormalizer;

  @Mock private ApplicationComponentsProducer config;

  @InjectMocks private SwatchEventBuilder swatchEventBuilder;

  public SwatchEventBuilderTest() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldBuildEventSuccessfully() {
    String eventType = "test_event";
    OffsetDateTime timestamp = OffsetDateTime.now();
    NormalizedMeasurements measurements = mock(NormalizedMeasurements.class);
    when(measurements.getCores()).thenReturn(Optional.of(4));
    when(measurements.getSockets()).thenReturn(Optional.of(2));
    when(measurementNormalizer.getMeasurements(
            any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(measurements);

    NormalizedFacts facts =
        NormalizedFacts.builder()
            .orgId("test-org")
            .instanceId("test-instance")
            .inventoryId("test-inventory")
            .insightsId("test-insights")
            .subscriptionManagerId("test-subscription")
            .displayName("test-name")
            .sla("premium")
            .usage("production")
            .is3rdPartyMigrated(true)
            .isHypervisor(false)
            .isUnmappedGuest(false)
            .isVirtual(true)
            .productIds(Set.of("prod1", "prod2"))
            .productTags(Set.of("tag1", "tag2"))
            .lastSeen(timestamp.minusDays(1))
            .cloudProvider(Event.CloudProvider.AWS)
            .hardwareType(Event.HardwareType.VIRTUAL)
            .hypervisorUuid("test-hypervisor-uuid")
            .build();

    Host host = mock(Host.class);
    when(host.getSystemProfileFacts()).thenReturn(null);
    when(host.getRhsmFacts()).thenReturn(Optional.empty());

    Event result = swatchEventBuilder.build(eventType, facts, host, timestamp);

    assertEquals("HBI_HOST_TEST_EVENT", result.getEventType());
    assertEquals("HBI_HOST", result.getServiceType());
    assertEquals("HBI_EVENT", result.getEventSource());
    assertEquals("test-org", result.getOrgId());
    assertEquals("test-instance", result.getInstanceId());
    assertEquals(Optional.of("test-inventory"), result.getInventoryId());
    assertEquals(Optional.of("test-insights"), result.getInsightsId());
    assertEquals(Optional.of("test-subscription"), result.getSubscriptionManagerId());
    assertEquals(Optional.of("test-name"), result.getDisplayName());
    assertEquals(Event.Sla.PREMIUM, result.getSla());
    assertEquals(Event.Usage.PRODUCTION, result.getUsage());
    assertTrue(result.getConversion());
    assertFalse(result.getIsHypervisor());
    assertTrue(result.getIsVirtual());
    assertFalse(result.getIsUnmappedGuest());
    assertEquals(Set.of("prod1", "prod2"), result.getProductIds());
    assertEquals(Set.of("tag1", "tag2"), result.getProductTag());
    assertEquals(timestamp, result.getTimestamp());
    assertEquals(Optional.of(timestamp.plusHours(1)), result.getExpiration());
    assertEquals(Event.CloudProvider.AWS, result.getCloudProvider());
    assertEquals(Event.HardwareType.VIRTUAL, result.getHardwareType());
    assertEquals(Optional.of("test-hypervisor-uuid"), result.getHypervisorUuid());
    assertEquals(timestamp.minusDays(1), result.getLastSeen());
    assertNotNull(result.getMeasurements());
    assertEquals(2, result.getMeasurements().size());
  }

  @Test
  void shouldHandleEmptyMeasurements() {
    String eventType = "empty_measurements";
    OffsetDateTime timestamp = OffsetDateTime.now();
    when(measurementNormalizer.getMeasurements(
            any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(new NormalizedMeasurements());

    NormalizedFacts facts =
        NormalizedFacts.builder().orgId("test-org").instanceId("test-instance").build();

    Host host = mock(Host.class);

    Event result = swatchEventBuilder.build(eventType, facts, host, timestamp);

    assertNotNull(result.getMeasurements());
    assertTrue(result.getMeasurements().isEmpty());
  }

  @Test
  void shouldHandlePartialFacts() {
    String eventType = "partial_facts";
    OffsetDateTime timestamp = OffsetDateTime.now();
    when(measurementNormalizer.getMeasurements(
            any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(new NormalizedMeasurements());

    NormalizedFacts facts =
        NormalizedFacts.builder()
            .orgId("partial-org")
            .instanceId("partial-instance")
            .isVirtual(true)
            .build();

    Host host = mock(Host.class);

    Event result = swatchEventBuilder.build(eventType, facts, host, timestamp);

    assertEquals("HBI_HOST_PARTIAL_FACTS", result.getEventType());
    assertEquals("partial-org", result.getOrgId());
    assertEquals("partial-instance", result.getInstanceId());
    assertTrue(result.getIsVirtual());
    assertNull(result.getSla());
    assertNull(result.getUsage());
  }

  @Test
  void shouldHandleNullOptionalValuesInFacts() {
    String eventType = "null_optionals";
    OffsetDateTime timestamp = OffsetDateTime.now();
    when(measurementNormalizer.getMeasurements(
            any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(new NormalizedMeasurements());

    NormalizedFacts facts =
        NormalizedFacts.builder()
            .orgId("null-org")
            .instanceId("null-instance")
            .inventoryId(null)
            .insightsId(null)
            .subscriptionManagerId(null)
            .displayName(null)
            .hypervisorUuid(null)
            .isVirtual(false)
            .build();

    Host host = mock(Host.class);

    Event result = swatchEventBuilder.build(eventType, facts, host, timestamp);

    assertEquals("HBI_HOST_NULL_OPTIONALS", result.getEventType());
    assertEquals("null-org", result.getOrgId());
    assertEquals("null-instance", result.getInstanceId());
    assertFalse(result.getIsVirtual());
    assertEquals(Optional.empty(), result.getInventoryId());
    assertEquals(Optional.empty(), result.getInsightsId());
    assertEquals(Optional.empty(), result.getSubscriptionManagerId());
    assertEquals(Optional.empty(), result.getDisplayName());
    assertEquals(Optional.empty(), result.getHypervisorUuid());
  }
}
