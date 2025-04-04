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
package com.redhat.swatch.hbi.domain.filtering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.redhat.swatch.hbi.config.ApplicationProperties;
import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.SystemProfileFacts;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HostEventFilterTest {

  private HostEventFilter filter;
  private ApplicationClock clock;
  private ApplicationProperties properties;

  @BeforeEach
  void setUp() {
    clock = mock(ApplicationClock.class);
    properties = mock(ApplicationProperties.class);
    filter = new HostEventFilter(clock, properties);
  }

  @Test
  void shouldSkipDueToMarketplaceBilling() {
    Host host = mock(Host.class);
    RhsmFacts rhsmFacts = mock(RhsmFacts.class);
    SystemProfileFacts systemFacts = mock(SystemProfileFacts.class);

    when(rhsmFacts.getBillingModel()).thenReturn("marketplace");
    when(host.getRhsmFacts()).thenReturn(Optional.of(rhsmFacts));
    when(systemFacts.getHostType()).thenReturn("standard");
    when(host.getSystemProfileFacts()).thenReturn(systemFacts);

    boolean result = filter.shouldSkip(host, OffsetDateTime.now());
    assertTrue(result, "Expected to skip due to marketplace billing");
  }

  @Test
  void shouldSkipDueToEdgeHost() {
    Host host = mock(Host.class);
    SystemProfileFacts systemFacts = mock(SystemProfileFacts.class);

    when(systemFacts.getHostType()).thenReturn("edge");
    when(host.getSystemProfileFacts()).thenReturn(systemFacts);
    when(host.getRhsmFacts()).thenReturn(Optional.empty());

    boolean result = filter.shouldSkip(host, OffsetDateTime.now());
    assertTrue(result, "Expected to skip due to edge host type");
  }

  @Test
  void shouldSkipDueToStaleTimestamp() {
    Host host = mock(Host.class);
    SystemProfileFacts systemFacts = mock(SystemProfileFacts.class);

    when(systemFacts.getHostType()).thenReturn("standard");
    when(host.getSystemProfileFacts()).thenReturn(systemFacts);
    when(host.getRhsmFacts()).thenReturn(Optional.empty());

    OffsetDateTime staleTime = OffsetDateTime.now().minusDays(2);
    when(clock.now()).thenReturn(OffsetDateTime.now());
    when(properties.getCullingOffset()).thenReturn(Duration.ofHours(24));

    boolean result = filter.shouldSkip(host, staleTime);
    assertTrue(result, "Expected to skip due to stale timestamp");
  }

  @Test
  void shouldNotSkipIfFreshAndNotEdgeOrMarketplace() {
    Host host = mock(Host.class);
    SystemProfileFacts systemFacts = mock(SystemProfileFacts.class);

    when(systemFacts.getHostType()).thenReturn("standard");
    when(host.getSystemProfileFacts()).thenReturn(systemFacts);
    when(host.getRhsmFacts()).thenReturn(Optional.empty());

    OffsetDateTime recent = OffsetDateTime.now();
    when(clock.now()).thenReturn(recent);
    when(properties.getCullingOffset()).thenReturn(Duration.ofHours(48));

    boolean result = filter.shouldSkip(host, recent.minusHours(1));
    assertFalse(result, "Expected NOT to skip fresh, non-edge, non-marketplace host");
  }
}
