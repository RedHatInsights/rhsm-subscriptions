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
package org.candlepin.subscriptions.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.TallyMeasurementKey;
import org.candlepin.subscriptions.db.model.TallySnapshot;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.db.model.config.OrgConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TallyRetentionControllerTest {
  @TestConfiguration
  @ComponentScan(basePackages = "org.candlepin.subscriptions.retention")
  public static class RetentionConfiguration {
    /* Intentionally empty */
  }

  @MockBean private TallyRetentionPolicy policy;
  @SpyBean private TallySnapshotRepository repository;
  @Autowired private OrgConfigRepository orgConfigRepository;
  @Autowired private TallyRetentionController controller;

  @BeforeEach
  void setup() {
    reset(repository);
  }

  @Test
  void retentionControllerShouldRemoveSnapshotsForGranularitiesConfigured() {
    OffsetDateTime cutoff = givenCutoffDateForGranularity(Granularity.DAILY);
    controller.purgeSnapshotsAsync();
    verify(repository).deleteAllByGranularityAndSnapshotDateBefore(Granularity.DAILY, cutoff);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void retentionControllerShouldIgnoreGranularityWithoutCutoff() {
    when(policy.getCutoffDate(any())).thenReturn(null);
    controller.purgeSnapshotsAsync();
    verifyNoInteractions(repository);
  }

  @Test
  void testPurgeSnapshots() {
    givenCutoffDateForAllGranularity();

    givenOrganization("1", "2", "3");
    givenSnapshotForOrganization("1");
    givenSnapshotForOrganization("3");
    controller.purgeSnapshotsAsync();
    assertEquals(0, repository.count());
    verify(repository, times(Granularity.values().length))
        .deleteAllByGranularityAndSnapshotDateBefore(any(), any());
  }

  private void givenCutoffDateForAllGranularity() {
    for (var granularity : Granularity.values()) {
      givenCutoffDateForGranularity(granularity);
    }
  }

  private OffsetDateTime givenCutoffDateForGranularity(Granularity granularity) {
    OffsetDateTime cutoff = OffsetDateTime.now();
    when(policy.getCutoffDate(granularity)).thenReturn(cutoff);
    return cutoff;
  }

  private void givenSnapshotForOrganization(String orgId) {
    TallySnapshot snapshot = new TallySnapshot();
    snapshot.setOrgId(orgId);
    snapshot.setSnapshotDate(OffsetDateTime.now().minusYears(1));
    snapshot.setBillingAccountId(UUID.randomUUID().toString());
    snapshot.setTallyMeasurements(
        Map.of(
            new TallyMeasurementKey(
                HardwareMeasurementType.AWS, MetricIdUtils.getCores().toString()),
            1.0));
    for (var granularity : Granularity.values()) {
      snapshot.setId(UUID.randomUUID());
      snapshot.setGranularity(granularity);
      repository.save(snapshot);
    }
  }

  private void givenOrganization(String... orgIds) {
    Stream.of(orgIds)
        .forEach(
            orgId -> {
              OrgConfig org = new OrgConfig();
              org.setOrgId(orgId);
              org.setOptInType(OptInType.API);
              org.setCreated(OffsetDateTime.now());
              org.setUpdated(OffsetDateTime.now());
              orgConfigRepository.save(org);
            });
  }
}
