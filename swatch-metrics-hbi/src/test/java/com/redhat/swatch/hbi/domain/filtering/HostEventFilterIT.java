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

import com.redhat.swatch.hbi.config.ApplicationProperties;
import com.redhat.swatch.hbi.domain.normalization.Host;
import com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.SystemProfileFacts;
import com.redhat.swatch.hbi.dto.HbiHost;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HostEventFilterIT {

  @Inject HostEventFilter filter;

  @Inject ApplicationProperties config;

  @Test
  void shouldSkipIfMarketplaceBilling() {
    RhsmFacts rhsmFacts =
        RhsmFacts.builder().billingModel("marketplace").productIds(Collections.emptySet()).build();

    Host host =
        Host.builder()
            .hbiHost(new HbiHost())
            .rhsmFacts(rhsmFacts)
            .systemProfileFacts(new SystemProfileFacts(new HbiHost()))
            .build();

    assertTrue(
        filter.shouldSkip(host, OffsetDateTime.now()),
        "Host should be skipped due to marketplace billing model");
  }

  @Test
  void shouldSkipIfEdgeHostType() {
    HbiHost hbiHost = new HbiHost();

    SystemProfileFacts edgeHostFacts =
        new SystemProfileFacts(hbiHost) {
          @Override
          public String getHostType() {
            return "edge";
          }
        };

    Host host = Host.builder().hbiHost(hbiHost).systemProfileFacts(edgeHostFacts).build();

    assertTrue(
        filter.shouldSkip(host, OffsetDateTime.now()),
        "Host should be skipped due to edge host type");
  }

  @Test
  void shouldSkipIfStale() {
    OffsetDateTime staleTime =
        OffsetDateTime.now().minus(config.getCullingOffset()).minus(1, ChronoUnit.MINUTES);

    Host host =
        Host.builder()
            .hbiHost(new HbiHost())
            .systemProfileFacts(new SystemProfileFacts(new HbiHost()))
            .build();

    assertTrue(filter.shouldSkip(host, staleTime), "Host should be skipped due to staleness");
  }

  @Test
  void shouldNotSkipFreshValidHost() {
    OffsetDateTime now = OffsetDateTime.now();

    SystemProfileFacts normalFacts =
        new SystemProfileFacts(new HbiHost()) {
          @Override
          public String getHostType() {
            return "standard";
          }
        };

    RhsmFacts rhsmFacts =
        RhsmFacts.builder()
            .billingModel("non-marketplace")
            .isVirtual(false)
            .productIds(Set.of("prod-1"))
            .build();

    Host host =
        Host.builder()
            .hbiHost(new HbiHost())
            .rhsmFacts(rhsmFacts)
            .systemProfileFacts(normalFacts)
            .build();

    assertFalse(filter.shouldSkip(host, now), "Host should NOT be skipped");
  }
}
