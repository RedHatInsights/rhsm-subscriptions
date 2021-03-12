/*
 * Copyright (c) 2019 - 2020 Red Hat, Inc.
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
package org.candlepin.subscriptions.tally;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.ApplicationProperties;
import org.candlepin.subscriptions.cloudigrade.ApiException;

import com.google.common.collect.ImmutableMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class TallySnapshotControllerTest {

    public static final String ACCOUNT = "foo123";

    @Autowired
    TallySnapshotController controller;

    @MockBean
    CloudigradeAccountUsageCollector cloudigradeCollector;

    @MockBean
    @Qualifier("OpenShiftMetricsUsageCollector")
    MetricUsageCollector metricUsageCollector;

    @MockBean
    InventoryAccountUsageCollector inventoryCollector;

    @Autowired
    ApplicationProperties props;

    private boolean defaultCloudigradeIntegrationEnablement;

    @BeforeEach
    void setup() {
        defaultCloudigradeIntegrationEnablement = props.isCloudigradeEnabled();
        when(inventoryCollector.collect(any(), any())).thenReturn(ImmutableMap.of(ACCOUNT,
            new AccountUsageCalculation(ACCOUNT)));
    }

    @AfterEach
    void restore() {
        props.setCloudigradeEnabled(defaultCloudigradeIntegrationEnablement);
    }

    @Test
    void testCloudigradeAccountUsageCollectorEnabled() throws IOException, ApiException {
        props.setCloudigradeEnabled(true);
        controller.produceSnapshotsForAccount(ACCOUNT);
        verify(cloudigradeCollector).enrichUsageWithCloudigradeData(any(), any());
    }

    @Test
    void testCloudigradeAccountUsageCollectorExceptionIgnored() throws IOException, ApiException {
        props.setCloudigradeEnabled(true);
        doThrow(new RuntimeException()).when(cloudigradeCollector).enrichUsageWithCloudigradeData(any(),
            any());
        controller.produceSnapshotsForAccount(ACCOUNT);
        verify(cloudigradeCollector, times(2)).enrichUsageWithCloudigradeData(any(), any());
    }

    @Test
    void testCloudigradeAccountUsageCollectorDisabled() {
        props.setCloudigradeEnabled(false);
        controller.produceSnapshotsForAccount(ACCOUNT);
        verifyZeroInteractions(cloudigradeCollector);
    }

    @Test
    void testBatchesLargerThanConfigIgnored() {
        controller.produceSnapshotsForAccounts(Collections.nCopies(props.getAccountBatchSize() + 1, "foo"));
        verifyZeroInteractions(cloudigradeCollector);
        verifyZeroInteractions(inventoryCollector);
    }

    @Test
    void testUsageMetricControllerDoes24Hours() {
        OffsetDateTime begin = OffsetDateTime.parse("2007-12-03T00:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2007-12-03T23:59:59.999Z");

        AccountUsageCalculation foo = new AccountUsageCalculation("foo");
        foo.getProducts().add("OpenShift Hourly");

        when(metricUsageCollector.collect(eq("foo"), any(), any()))
            .thenReturn(foo);
        controller.produceHourlySnapshotsForAccount("foo", begin, end);
        verify(metricUsageCollector, times(24)).collect(eq("foo"), any(), any());
    }
}
