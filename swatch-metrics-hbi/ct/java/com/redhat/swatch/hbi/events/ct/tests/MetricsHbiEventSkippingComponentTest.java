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
package com.redhat.swatch.hbi.events.ct.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.swatch.component.tests.api.TestPlanName;
import com.redhat.swatch.component.tests.api.hbi.HostBuilder;
import com.redhat.swatch.component.tests.api.hbi.HostTemplates;
import com.redhat.swatch.component.tests.utils.AwaitilityUtils;
import com.redhat.swatch.hbi.events.dtos.hbi.HbiHostCreateUpdateEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MetricsHbiEventSkippingComponentTest extends BaseSMHBIComponentTest {

  @BeforeAll
  static void enableEmitEventsFeatureFlag() {
    unleash.enableFlag(EMIT_EVENTS);
  }

  @AfterAll
  static void disableEmitEventsFeatureFlag() {
    unleash.disableFlag(EMIT_EVENTS);
  }

  @TestPlanName("metrics-hbi-event-skipping-TC001")
  @ParameterizedTest
  @ValueSource(strings = {"created", "updated"})
  void shouldSkipProducingEventWhenBillingModelSetToMarketplace(String hbiEventType) {
    // Initial count of invalid billing model warning in logs
    String warning =
        "Incoming HBI event will be skipped due to an invalid billing model: marketplace";
    int countBefore = countLogsContaining(warning);

    // Given: A physical RHEL for x86 host with billing model set to marketplace
    HostBuilder host = physicalRhelHost();
    host.rhsmFacts(host.rhsmFacts().toBuilder().billingModel("marketplace").build());
    HbiHostCreateUpdateEvent hbiEvent = createEvent(host, hbiEventType);

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Event is skipped due to its marketplace billing model
    // Check new invalid billing model warning is in logs
    AwaitilityUtils.untilIsTrue(() -> countLogsContaining(warning) > countBefore);
    assertEquals(countBefore + 1, countLogsContaining(warning));
  }

  @TestPlanName("metrics-hbi-event-skipping-TC002")
  @ParameterizedTest
  @ValueSource(strings = {"created", "updated"})
  void shouldSkipProducingEventWhenHostTypeSetToEdge(String hbiEventType) {
    // Initial count of invalid host type warning in logs
    String warning = "Incoming HBI event will be skipped due to an invalid host type: edge";
    int countBefore = countLogsContaining(warning);

    // Given: A physical RHEL for x86 host with host type set to edge
    HostBuilder host = physicalRhelHost();
    host.systemProfileFacts(host.getSystemProfileFacts().toBuilder().hostType("edge").build());
    HbiHostCreateUpdateEvent hbiEvent = createEvent(host, hbiEventType);

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Event is skipped due to its edge host type
    // Check new invalid host type warning is in logs
    AwaitilityUtils.untilIsTrue(() -> countLogsContaining(warning) > countBefore);
    assertEquals(countBefore + 1, countLogsContaining(warning));
  }

  @TestPlanName("metrics-hbi-event-skipping-TC003")
  @ParameterizedTest
  @ValueSource(strings = {"created", "updated"})
  void shouldSkipProducingEventWhenTimestampIsStale(String hbiEventType) {
    // Initial count of stale warning in logs
    String warning = "Incoming HBI event will be skipped because it is stale";
    int countBefore = countLogsContaining(warning);

    // Given: A stale physical RHEL for x86 host based on its timestamp
    HbiHostCreateUpdateEvent hbiEvent = createEvent(physicalRhelHost(), hbiEventType);
    hbiEvent
        .getHost()
        .setStaleTimestamp(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).toString());

    // When: HBI event is produced to Kafka
    hostEvents.publish(hbiEvent);

    // Then: Event is skipped because its stale timestamp is too old
    // Check new stale warning is in logs
    AwaitilityUtils.untilIsTrue(() -> countLogsContaining(warning) > countBefore);
    assertEquals(countBefore + 1, countLogsContaining(warning));
  }

  private HostBuilder physicalRhelHost() {
    return hostEvents.createHost(orgId).apply(HostTemplates.conduitReportedPhysicalRhel(2, 4));
  }

  private HbiHostCreateUpdateEvent createEvent(HostBuilder host, String eventType) {
    return host.toEvent()
        .type(eventType)
        .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
        .buildCreateUpdate();
  }
}
