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

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.candlepin.subscriptions.db.OrgConfigRepository;
import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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
  @MockBean private TallySnapshotRepository repository;
  @MockBean private OrgConfigRepository orgConfigRepository;

  @Autowired private TallyRetentionController controller;

  @Test
  void retentionControllerShouldRemoveSnapshotsForGranularitiesConfigured() throws Exception {
    OffsetDateTime cutoff = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
    when(policy.getCutoffDate(Granularity.DAILY)).thenReturn(cutoff);
    controller.cleanStaleSnapshotsForOrgId("123456");
    verify(repository)
        .deleteAllByOrgIdAndGranularityAndSnapshotDateBefore("123456", Granularity.DAILY, cutoff);
    verifyNoMoreInteractions(repository);
  }

  @Test
  void retentionControllerShouldIgnoreGranularityWithoutCutoff() throws Exception {
    when(policy.getCutoffDate(Granularity.DAILY)).thenReturn(null);
    controller.cleanStaleSnapshotsForOrgId("123456");
    verifyNoInteractions(repository);
  }

  @Test
  void testPurgeSnapshots() throws Exception {
    OffsetDateTime cutoff = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
    when(policy.getCutoffDate(Granularity.DAILY)).thenReturn(cutoff);

    List<String> testList = Arrays.asList("1", "2", "3", "4");
    when(orgConfigRepository.findSyncEnabledOrgs()).thenReturn(testList.stream());

    controller.purgeSnapshotsAsync();

    verify(repository, times(4))
        .deleteAllByOrgIdAndGranularityAndSnapshotDateBefore(
            anyString(), eq(Granularity.DAILY), eq(cutoff));
  }
}
