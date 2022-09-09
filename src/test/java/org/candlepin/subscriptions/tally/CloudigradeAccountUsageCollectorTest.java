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
package org.candlepin.subscriptions.tally;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.candlepin.subscriptions.cloudigrade.CloudigradeService;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrencyReport;
import org.candlepin.subscriptions.cloudigrade.api.model.ConcurrentUsage;
import org.candlepin.subscriptions.cloudigrade.api.model.UsageCount;
import org.candlepin.subscriptions.db.model.BillingProvider;
import org.candlepin.subscriptions.db.model.HardwareMeasurementType;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class CloudigradeAccountUsageCollectorTest {
  public static final String ACCOUNT = "foo123";
  public static final String ORG_ID = "Org123";

  @MockBean CloudigradeService cloudigradeService;

  @Autowired CloudigradeAccountUsageCollector collector;

  @Test
  void testEnrichUsageWithCloudigradeDataHaltsWithBadOrg() throws Exception {
    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(false);
    var calculations = new HashMap<String, AccountUsageCalculation>();
    collector.enrichUsageWithCloudigradeData(calculations, ACCOUNT, ORG_ID);
    verify(cloudigradeService, never())
        .listDailyConcurrentUsages(any(), any(), any(), any(), any());
  }

  @Test
  void testWhenOrgIsAbsentEnrichUsageWithCloudigradeDataReturnsNull() throws Exception {
    var calculations = new HashMap<String, AccountUsageCalculation>();
    assertNull(collector.getDailyConcurrencyReport(null));
  }

  @Test
  void testEnrichUsageWithCloudigradeDataWorksWhenNoDataInHbi() throws Exception {
    HashMap<String, AccountUsageCalculation> calculations = new HashMap<>();
    UsageCount usageCount =
        new UsageCount().instancesCount(1).role("_ANY").sla("_ANY").usage("_ANY").arch("_ANY");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Collections.singletonList(concurrentUsage));
    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(true);
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);
    collector.enrichUsageWithCloudigradeData(calculations, ACCOUNT, ORG_ID);
    assertEquals(1, calculations.size());
  }

  @Test
  void testEnrichUsageIgnoresExcessUsages() throws Exception {
    UsageCount usageCount =
        new UsageCount().instancesCount(1).role("_ANY").sla("_ANY").usage("_ANY").arch("_ANY");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Arrays.asList(concurrentUsage, concurrentUsage));
    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(true);
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);

    AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
    collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage), ACCOUNT, ORG_ID);
    assertEquals(
        1,
        accountUsage
            .getCalculation(
                new UsageCalculation.Key(
                    "RHEL", ServiceLevel._ANY, Usage._ANY, BillingProvider._ANY, "_ANY"))
            .getTotals(HardwareMeasurementType.AWS_CLOUDIGRADE)
            .getInstances());
  }

  @Test
  void testEnrichUsageIgnoresArchRoleCombo() throws Exception {
    UsageCount usageCount =
        new UsageCount()
            .instancesCount(1)
            .role("RHEL Server")
            .sla("_ANY")
            .usage("_ANY")
            .arch("x86_64");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Collections.singletonList(concurrentUsage));
    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(true);
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);

    AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
    collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage), ACCOUNT, ORG_ID);
    assertEquals(0, accountUsage.getKeys().size());
  }

  static Stream<Arguments> enrichmentParameterProvider() {
    // This method returns a tuple of role, arch, and productID
    return Stream.of(
        arguments("_ANY", "_ANY", "RHEL"),
        arguments("Red Hat Enterprise Linux Server", "_ANY", "RHEL Server"),
        arguments("_ANY", "x86_64", "RHEL for x86"));
  }

  @ParameterizedTest
  @MethodSource("enrichmentParameterProvider")
  void testEnrichUsageMatches() throws Exception {
    UsageCount usageCount =
        new UsageCount().instancesCount(1).role("_ANY").sla("_ANY").usage("_ANY").arch("_ANY");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Collections.singletonList(concurrentUsage));
    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(true);
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);
    AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
    collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage), ACCOUNT, ORG_ID);
    assertEquals(
        1,
        accountUsage
            .getCalculation(
                new UsageCalculation.Key(
                    "RHEL", ServiceLevel._ANY, Usage._ANY, BillingProvider._ANY, "_ANY"))
            .getTotals(HardwareMeasurementType.AWS_CLOUDIGRADE)
            .getInstances());
  }

  @Test
  void testEnrichUsageIgnoresInvalidRole() throws Exception {
    UsageCount usageCount =
        new UsageCount()
            .instancesCount(1)
            .role("RHEL Server")
            .sla("_ANY")
            .usage("_ANY")
            .arch("_ANY");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Collections.singletonList(concurrentUsage));
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);
    AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
    collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage), ACCOUNT, ORG_ID);
    assertEquals(0, accountUsage.getKeys().size());
  }

  @Test
  void testEnrichUsageIgnoresInvalidArch() throws Exception {
    UsageCount usageCount =
        new UsageCount().instancesCount(1).role("_ANY").sla("_ANY").usage("_ANY").arch("foobar");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Collections.singletonList(usageCount));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Collections.singletonList(concurrentUsage));
    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(true);
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);
    AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
    collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage), ACCOUNT, ORG_ID);
    assertEquals(0, accountUsage.getKeys().size());
  }

  @Test
  void testEnrichUsageSkipsServiceTypeForNow() throws Exception {
    UsageCount usageCountAnyServiceType =
        new UsageCount()
            .instancesCount(1)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .serviceType("_ANY")
            .arch("_ANY");
    UsageCount usageCountUnsetServiceType =
        new UsageCount()
            .instancesCount(2)
            .role("_ANY")
            .sla("_ANY")
            .usage("_ANY")
            .serviceType("")
            .arch("_ANY");
    ConcurrentUsage concurrentUsage =
        new ConcurrentUsage()
            .date(LocalDate.now())
            .maximumCounts(Arrays.asList(usageCountAnyServiceType, usageCountUnsetServiceType));
    ConcurrencyReport report =
        new ConcurrencyReport().data(Collections.singletonList(concurrentUsage));

    when(cloudigradeService.cloudigradeUserExists(ORG_ID)).thenReturn(true);
    when(cloudigradeService.listDailyConcurrentUsages(any(), any(), any(), any(), any()))
        .thenReturn(report);
    AccountUsageCalculation accountUsage = new AccountUsageCalculation(ACCOUNT);
    collector.enrichUsageWithCloudigradeData(usageMapOf(ACCOUNT, accountUsage), ACCOUNT, ORG_ID);
    assertEquals(1, accountUsage.getKeys().size());
    Optional<UsageCalculation.Key> usageKey = accountUsage.getKeys().stream().findFirst();
    assertTrue(usageKey.isPresent());
    assertEquals(
        1,
        accountUsage
            .getCalculation(usageKey.get())
            .getTotals(HardwareMeasurementType.TOTAL)
            .getSockets());
  }

  private Map<String, AccountUsageCalculation> usageMapOf(
      String account, AccountUsageCalculation accountUsage) {
    HashMap<String, AccountUsageCalculation> map = new HashMap<>();
    map.put(account, accountUsage);
    return map;
  }
}
