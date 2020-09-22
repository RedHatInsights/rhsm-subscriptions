/*
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.subscriptions.cloudigrade.ApiException;
import org.candlepin.subscriptions.cloudigrade.CloudigradeService;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrentUsage;
import org.candlepin.subscriptions.cloudigrade.api.model.UsageCount;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@TestPropertySource("classpath:/test.properties")
class CloudigradeAccountUsageCollectorTest {
    public static final String ACCOUNT = "foo123";

    @MockBean
    CloudigradeService cloudigradeService;

    @Autowired
    CloudigradeAccountUsageCollector collector;

    @Test
    void testEnrichUsageWithCloudigradeDataWorksWhenNoDataInHbi() throws IOException, ApiException {
        HashMap<String, AccountUsageCalculation> calculations = new HashMap<>();
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .arch("_ANY");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);
        collector.enrichUsageWithCloudigradeData(calculations, Collections.singletonList(ACCOUNT));
        assertEquals(1, calculations.size());
    }

    @Test
    void testEnrichUsageIgnoresExcessUsages() throws IOException, ApiException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .arch("_ANY");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Arrays.asList(concurrentUsage, concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);

        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(1, accountUsage.getCalculation(new UsageCalculation.Key("RHEL", ServiceLevel.ANY,
            Usage.ANY)).getTotals(HardwareMeasurementType.AWS_CLOUDIGRADE).getInstances());
    }

    @Test
    void testEnrichUsageIgnoresArchRoleCombo() throws IOException, ApiException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("RHEL Server")
            .sla("_ANY")
            .usage("_ANY")
            .arch("x86_64");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);

        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(0, accountUsage.getKeys().size());
    }

    @Test
    void testEnrichUsageMatchesRHEL() throws IOException, ApiException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .arch("_ANY");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);
        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(1, accountUsage.getCalculation(new UsageCalculation.Key("RHEL", ServiceLevel.ANY,
            Usage.ANY)).getTotals(HardwareMeasurementType.AWS_CLOUDIGRADE).getInstances());
    }

    @Test
    void testEnrichUsageMatchesByRole() throws IOException, ApiException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("Red Hat Enterprise Linux Server")
            .sla("_ANY")
            .usage("_ANY")
            .arch("_ANY");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);
        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(1, accountUsage.getCalculation(new UsageCalculation.Key("RHEL Server", ServiceLevel.ANY,
            Usage.ANY)).getTotals(HardwareMeasurementType.AWS_CLOUDIGRADE).getInstances());
    }

    @Test
    void testEnrichUsageIgnoresInvalidRole() throws IOException, ApiException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("RHEL Server")
            .sla("_ANY")
            .usage("_ANY")
            .arch("_ANY");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);
        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(0, accountUsage.getKeys().size());
    }

    @Test
    void testEnrichUsageMatchesByArch() throws ApiException, IOException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .arch("x86_64");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);
        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(1, accountUsage.getCalculation(new UsageCalculation.Key("RHEL for x86", ServiceLevel.ANY,
            Usage.ANY)).getTotals(HardwareMeasurementType.AWS_CLOUDIGRADE).getInstances());
    }

    @Test
    void testEnrichUsageIgnoresInvalidArch() throws ApiException, IOException {
        UsageCount usageCount = new UsageCount()
            .instancesCount(1)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .arch("foobar");
        ConcurrentUsage concurrentUsage = new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
        ConcurrencyReport report = new ConcurrencyReport()
            .data(Collections.singletonList(concurrentUsage));
        when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
            .thenReturn(report);
        AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
        collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage),
            Collections.singletonList(ACCOUNT));
        assertEquals(0, accountUsage.getKeys().size());
    }

    private Map<String, AccountUsageCalculation> usageMapOf(String account,
        AccountUsageCalculation accountUsage) {
        HashMap<String, AccountUsageCalculation> map = new HashMap<>();
        map.put(account, accountUsage);
        return map;
    }
}
