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
package com.redhat.swatch.hbi.domain.normalization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.redhat.swatch.common.model.HardwareMeasurementType;
import com.redhat.swatch.hbi.config.ApplicationProperties;
import com.redhat.swatch.hbi.domain.HbiHostManager;
import com.redhat.swatch.hbi.domain.normalization.facts.*;
import com.redhat.swatch.hbi.dto.HbiHost;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FactNormalizerTest {

  private FactNormalizer factNormalizer;
  private ApplicationClock clock;
  private ApplicationProperties config;
  private HbiHostManager hostManager;

  @BeforeEach
  void setup() {
    clock = mock(ApplicationClock.class);
    config = mock(ApplicationProperties.class);
    hostManager = mock(HbiHostManager.class);
    factNormalizer = new FactNormalizer(clock, config, hostManager);
  }

  @Test
  void shouldNormalizeMinimalHostWithDefaults() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("org123");
    hbiHost.setSubscriptionManagerId("sm-456");

    Host host =
        Host.builder().hbiHost(hbiHost).systemProfileFacts(new SystemProfileFacts(hbiHost)).build();

    when(hostManager.isHypervisor("org123", "sm-456")).thenReturn(false);

    NormalizedFacts result = factNormalizer.normalize(host);

    assertEquals("org123", result.getOrgId());
    assertFalse(result.isVirtual());
    assertFalse(result.isHypervisor());
    assertFalse(result.isUnmappedGuest());
  }

  @Test
  void shouldTreatUnknownUsageAsEmpty() {
    RhsmFacts rhsmFacts = mock(RhsmFacts.class);
    when(rhsmFacts.getUsage()).thenReturn("nonsense-usage");

    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("o");
    hbiHost.setSubscriptionManagerId("s");

    Host host =
        Host.builder()
            .hbiHost(hbiHost)
            .rhsmFacts(rhsmFacts)
            .systemProfileFacts(new SystemProfileFacts(hbiHost))
            .build();

    NormalizedFacts facts = factNormalizer.normalize(host);
    assertNull(facts.getUsage());
  }

  @Test
  void shouldTreatUnknownSlaAsEmpty() {
    RhsmFacts rhsmFacts = mock(RhsmFacts.class);
    when(rhsmFacts.getSla()).thenReturn("strange-sla");

    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("orgx");
    hbiHost.setSubscriptionManagerId("smx");

    Host host =
        Host.builder()
            .hbiHost(hbiHost)
            .rhsmFacts(rhsmFacts)
            .systemProfileFacts(new SystemProfileFacts(hbiHost))
            .build();

    NormalizedFacts facts = factNormalizer.normalize(host);
    assertNull(facts.getSla());
  }

  @Test
  void shouldUseCloudHardwareTypeIfProviderRecognized() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("o");
    hbiHost.setSubscriptionManagerId("s");

    SystemProfileFacts profile = mock(SystemProfileFacts.class);
    when(profile.getCloudProvider()).thenReturn("aws");

    Host host = Host.builder().hbiHost(hbiHost).systemProfileFacts(profile).build();

    NormalizedFacts facts = factNormalizer.normalize(host);
    assertEquals(HardwareMeasurementType.AWS.name(), facts.getCloudProviderType().name());
    assertEquals("CLOUD", facts.getHardwareType().name());
  }

  @Test
  void shouldFallbackToInstanceIdIfProviderIdMissing() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("o");
    hbiHost.setId(UUID.randomUUID());

    Host host =
        Host.builder().hbiHost(hbiHost).systemProfileFacts(new SystemProfileFacts(hbiHost)).build();
    NormalizedFacts facts = factNormalizer.normalize(host);
    assertEquals(hbiHost.getId().toString(), facts.getInstanceId());
  }

  @Test
  void shouldParseLastSeenDateFromUpdatedField() {
    OffsetDateTime now = OffsetDateTime.now();
    HbiHost hbiHost = new HbiHost();
    hbiHost.setUpdated(now.toString());
    hbiHost.setOrgId("x");
    hbiHost.setSubscriptionManagerId("y");

    Host host =
        Host.builder().hbiHost(hbiHost).systemProfileFacts(new SystemProfileFacts(hbiHost)).build();
    NormalizedFacts facts = factNormalizer.normalize(host);
    assertEquals(
        now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
        facts.getLastSeen().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
  }

  @Test
  void shouldHandleNullSyncTimestampGracefully() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("o");
    hbiHost.setSubscriptionManagerId("s");

    RhsmFacts rhsm = mock(RhsmFacts.class);
    when(rhsm.getSyncTimestamp()).thenReturn(null);

    Host host =
        Host.builder()
            .hbiHost(hbiHost)
            .rhsmFacts(rhsm)
            .systemProfileFacts(new SystemProfileFacts(hbiHost))
            .build();

    NormalizedFacts facts = factNormalizer.normalize(host);
    assertNotNull(facts);
  }

  static Stream<Arguments> instanceIdParams() {
    UUID inventoryId = UUID.randomUUID();
    String providerId = UUID.randomUUID().toString();
    return Stream.of(
        Arguments.of(null, null, null),
        Arguments.of(inventoryId, null, inventoryId.toString()),
        Arguments.of(null, providerId, providerId),
        Arguments.of(inventoryId, providerId, providerId));
  }

  @ParameterizedTest
  @MethodSource("instanceIdParams")
  void testInstanceIdNormalization(UUID inventoryId, String providerId, String expectedInstanceId) {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setId(inventoryId);
    hbiHost.setProviderId(providerId);
    Host host =
        Host.builder().hbiHost(hbiHost).systemProfileFacts(new SystemProfileFacts(hbiHost)).build();
    assertEquals(expectedInstanceId, factNormalizer.normalize(host).getInstanceId());
  }

  @Test
  void testIs3rdPartyMigrated() {
    HbiHost hbiHost = new HbiHost();
    Map<String, Object> conversions = new HashMap<>();
    conversions.put("activity", true);
    Map<String, Object> sysProfile = new HashMap<>();
    sysProfile.put("conversions", conversions);
    hbiHost.setSystemProfile(sysProfile);

    Host host =
        Host.builder().hbiHost(hbiHost).systemProfileFacts(new SystemProfileFacts(hbiHost)).build();
    assertTrue(factNormalizer.normalize(host).is3rdPartyMigrated());
  }

  @Test
  void testIsNot3rdPartyMigrated() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setSystemProfile(new HashMap<>());
    Host host =
        Host.builder().hbiHost(hbiHost).systemProfileFacts(new SystemProfileFacts(hbiHost)).build();
    assertFalse(factNormalizer.normalize(host).is3rdPartyMigrated());
  }
}
