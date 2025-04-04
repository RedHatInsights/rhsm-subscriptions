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

import com.redhat.swatch.hbi.config.ApplicationProperties;
import com.redhat.swatch.hbi.domain.normalization.facts.NormalizedFacts;
import com.redhat.swatch.hbi.domain.normalization.facts.RhsmFacts;
import com.redhat.swatch.hbi.dto.HbiHost;
import com.redhat.swatch.hbi.infra.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.hbi.infra.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import org.candlepin.clock.ApplicationClock;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(InMemoryMessageBrokerKafkaResource.class)
class FactNormalizerIT {

  @Inject FactNormalizer factNormalizer;
  @Inject ApplicationClock clock;
  @Inject ApplicationProperties settings;

  @Test
  void shouldNormalizeRealisticHost() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("test-org");
    hbiHost.setSubscriptionManagerId("test-sm-id");
    hbiHost.setDisplayName("integration-test-host");
    hbiHost.setUpdated(OffsetDateTime.now().toString());
    hbiHost.setFacts(List.of()); // Prevent NPE

    Host host = new Host(hbiHost);

    NormalizedFacts facts = factNormalizer.normalize(host);

    assertNotNull(facts.getOrgId());
    assertEquals("test-org", facts.getOrgId());
    assertEquals("test-sm-id", facts.getSubscriptionManagerId());
    assertEquals("integration-test-host", facts.getDisplayName());
    assertNotNull(facts.getLastSeen());
  }

  @Test
  void shouldHandleHostWithoutOptionalFacts() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("missing-facts-org");
    hbiHost.setSubscriptionManagerId("missing-facts-sm");
    hbiHost.setDisplayName("no-optional-facts");
    hbiHost.setFacts(List.of()); // Prevent NPE

    Host host = new Host(hbiHost);

    NormalizedFacts facts = factNormalizer.normalize(host);

    assertEquals("missing-facts-org", facts.getOrgId());
    assertNull(facts.getUsage());
    assertNull(facts.getSla());
    assertFalse(facts.isHypervisor());
    assertFalse(facts.isUnmappedGuest());
  }

  @Test
  void shouldRespectSyncTimestampSkippingRhsm() {
    HbiHost hbiHost = new HbiHost();
    hbiHost.setOrgId("rhsm-skip-org");
    hbiHost.setSubscriptionManagerId("rhsm-skip-sm");
    hbiHost.setDisplayName("rhsm-skipped");
    hbiHost.setFacts(List.of()); // Prevent NPE

    OffsetDateTime stale =
        clock.startOfToday().minus(settings.getHostLastSyncThreshold()).minusMinutes(1);

    RhsmFacts rhsmFacts =
        RhsmFacts.builder()
            .syncTimestamp(stale.toString())
            .sla("Premium") // should be ignored
            .usage("Development") // should be ignored
            .isVirtual(false)
            .productIds(new HashSet<>())
            .build();

    Host host =
        Host.builder()
            .hbiHost(hbiHost)
            .rhsmFacts(rhsmFacts)
            .systemProfileFacts(
                new com.redhat.swatch.hbi.domain.normalization.facts.SystemProfileFacts(hbiHost))
            .build();

    NormalizedFacts facts = factNormalizer.normalize(host);
    assertNull(facts.getSla(), "SLA should be skipped due to stale RHSM sync timestamp");
    assertNull(facts.getUsage(), "Usage should be skipped due to stale RHSM sync timestamp");
  }
}
