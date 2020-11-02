/*
 * Copyright (c) 2019 - 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.controller;

import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.db.TallySnapshotRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.retention.TallyRetentionPolicy;
import org.candlepin.subscriptions.tally.AccountListSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
class TallyRetentionControllerTest {
    @MockBean private TallyRetentionPolicy policy;
    @MockBean private TallySnapshotRepository repository;
    @MockBean private AccountListSource accountListSource;

    @Autowired private TallyRetentionController controller;

    @Test
    void retentionControllerShouldRemoveSnapshotsForGranularitiesConfigured() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
        when(policy.getCutoffDate(Granularity.DAILY)).thenReturn(cutoff);
        controller.cleanStaleSnapshotsForAccount("123456");
        verify(repository).deleteAllByAccountNumberAndGranularityAndSnapshotDateBefore(
            eq("123456"),
            eq(Granularity.DAILY),
            eq(cutoff)
        );
        verifyNoMoreInteractions(repository);
    }

    @Test
    void retentionControllerShouldIgnoreGranularityWithoutCutoff() throws Exception {
        when(policy.getCutoffDate(Granularity.DAILY)).thenReturn(null);
        controller.cleanStaleSnapshotsForAccount("123456");
        verifyZeroInteractions(repository);
    }

    @Test
    void testPurgeSnapshots() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
        when(policy.getCutoffDate(Granularity.DAILY)).thenReturn(cutoff);

        List<String> testList = Arrays.asList("1", "2", "3", "4");
        when(accountListSource.purgeReportAccounts()).thenReturn(testList.stream());

        controller.purgeSnapshots();

        verify(repository, times(4)).deleteAllByAccountNumberAndGranularityAndSnapshotDateBefore(
            anyString(),
            eq(Granularity.DAILY),
            eq(cutoff)
        );
    }
}
