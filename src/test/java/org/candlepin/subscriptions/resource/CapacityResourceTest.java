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
package org.candlepin.subscriptions.resource;

import static org.candlepin.subscriptions.utilization.api.model.ProductId.RHEL;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javax.ws.rs.core.Response;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionCapacityRepository;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportByMetricId;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshotByMetricId;
import org.candlepin.subscriptions.utilization.api.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.model.MetricId;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@WithMockRedHatPrincipal("123456")
@ActiveProfiles({"api", "test"})
class CapacityResourceTest {

  private static final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private static final OffsetDateTime max = OffsetDateTime.now().plusDays(4);

  @MockBean SubscriptionCapacityRepository repository;

  @MockBean PageLinkCreator pageLinkCreator;

  @MockBean AccountConfigRepository accountConfigRepository;

  @Autowired CapacityResource resource;

  @BeforeEach
  public void setupTests() {
    when(accountConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel.PREMIUM, Usage._ANY, min, max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, ServiceLevelType.PREMIUM, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseUsageQueryParam() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage.PRODUCTION, min, max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, null, UsageType.PRODUCTION);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptySlaAsNull() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, ServiceLevelType.EMPTY, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptyUsageAsNull() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, null, UsageType.EMPTY);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldCalculateCapacityBasedOnMultipleSubscriptions() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    SubscriptionCapacity capacity2 = new SubscriptionCapacity();
    capacity2.setHypervisorSockets(7);
    capacity2.setSockets(11);
    capacity2.setHypervisorCores(14);
    capacity2.setCores(22);
    capacity2.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity2.setEndDate(max);

    when(repository.findAllBy("owner123456", RHEL.toString(), null, null, min, max))
        .thenReturn(Arrays.asList(capacity, capacity2));

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);

    CapacitySnapshot capacitySnapshot = report.getData().get(0);
    assertEquals(12, capacitySnapshot.getHypervisorSockets().intValue());
    assertEquals(13, capacitySnapshot.getPhysicalSockets().intValue());
    assertEquals(34, capacitySnapshot.getHypervisorCores().intValue());
    assertEquals(30, capacitySnapshot.getPhysicalCores().intValue());
  }

  @Test
  void testShouldThrowExceptionOnBadOffset() {
    SubscriptionsException e =
        assertThrows(
            SubscriptionsException.class,
            () -> {
              resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, 11, 10, null, null);
            });
    assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testShouldRespectOffsetAndLimit() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, 1, 1, null, null);

    assertEquals(1, report.getData().size());
    assertEquals(
        OffsetDateTime.now().minusDays(3).truncatedTo(ChronoUnit.DAYS),
        report.getData().get(0).getDate());
  }

  @Test
  @WithMockRedHatPrincipal("1111")
  void testAccessDeniedWhenAccountIsNotInAllowlist() {
    assertThrows(
        AccessDeniedException.class,
        () -> {
          resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);
        });
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testAccessDeniedWhenUserIsNotAnAdmin() {
    assertThrows(
        AccessDeniedException.class,
        () -> {
          resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);
        });
  }

  @Test
  void testGetCapacitiesWeekly() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    capacity.setBeginDate(begin);
    capacity.setEndDate(end);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage.PRODUCTION, begin, end))
        .thenReturn(Collections.singletonList(capacity));

    List<CapacitySnapshot> actual =
        resource.getCapacities(
            "owner123456",
            RHEL,
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            Granularity.WEEKLY,
            begin,
            end);

    // Add one because we generate reports including both endpoints on the timeline
    long expected = ChronoUnit.WEEKS.between(begin, end) + 1;
    assertEquals(expected, actual.size());
  }

  @Test
  void testShouldCalculateCapacityWithUnlimitedUsage() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHasUnlimitedUsage(true);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy("owner123456", RHEL.toString(), null, null, min, max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);

    CapacitySnapshot capacitySnapshot = report.getData().get(0);
    assertTrue(capacitySnapshot.getHasInfiniteQuantity());
  }

  @ParameterizedTest
  @MethodSource("usageLists")
  void testShouldCalculateCapacityRegardlessOfUsageSeenFirst(List<SubscriptionCapacity> usages) {
    when(repository.findAllBy("owner123456", RHEL.toString(), null, null, min, max))
        .thenReturn(usages);

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);

    CapacitySnapshot capacitySnapshot = report.getData().get(0);
    assertTrue(capacitySnapshot.getHasInfiniteQuantity());
  }

  @Test
  void testReportByMetricIdShouldUseQueryBasedOnHeaderAndParameters() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ReportCategory.PHYSICAL,
            null,
            null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldUseSlaQueryParam() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel.PREMIUM,
            Usage._ANY,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            ServiceLevelType.PREMIUM,
            null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldUseUsageQueryParam() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            null,
            UsageType.PRODUCTION);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldTreatEmptySlaAsNull() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            ServiceLevelType.EMPTY,
            null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldTreatEmptyUsageAsNull() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            null,
            UsageType.EMPTY);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityBasedOnMultipleSubscriptions() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    SubscriptionCapacity capacity2 = new SubscriptionCapacity();
    capacity2.setHypervisorSockets(7);
    capacity2.setSockets(11);
    capacity2.setHypervisorCores(14);
    capacity2.setCores(22);
    capacity2.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity2.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.CORES, null, null, null, min, max))
        .thenReturn(Arrays.asList(capacity, capacity2));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(64, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityAllSockets() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.SOCKETS, null, null, null, min, max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.SOCKETS, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(7, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualSockets() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.SOCKETS,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.SOCKETS,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ReportCategory.VIRTUAL,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(2, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityPhysicalSockets() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.SOCKETS,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.SOCKETS,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ReportCategory.PHYSICAL,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(2, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityAllCores() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.CORES, null, null, null, min, max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(28, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualCores() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ReportCategory.VIRTUAL,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(8, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityPhysicalCores() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setHypervisorSockets(5);
    capacity.setSockets(2);
    capacity.setHypervisorCores(20);
    capacity.setCores(8);
    capacity.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(Arrays.asList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL,
            MetricId.CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ReportCategory.PHYSICAL,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(8, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldThrowExceptionOnBadOffset() {
    SubscriptionsException e =
        assertThrows(
            SubscriptionsException.class,
            () -> {
              resource.getCapacityReportByMetricId(
                  RHEL, MetricId.CORES, GranularityType.DAILY, min, max, 11, 10, null, null, null);
            });
    assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testReportByMetricIdShouldRespectOffsetAndLimit() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    capacity.setBeginDate(min);
    capacity.setEndDate(max);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, 1, 1, null, null, null);

    assertEquals(1, report.getData().size());
    assertEquals(
        OffsetDateTime.now().minusDays(3).truncatedTo(ChronoUnit.DAYS),
        report.getData().get(0).getDate());
  }

  @Test
  @WithMockRedHatPrincipal("1111")
  void testReportByMetridIdAccessDeniedWhenAccountIsNotInAllowlist() {
    assertThrows(
        AccessDeniedException.class,
        () -> {
          resource.getCapacityReportByMetricId(
              RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);
        });
  }

  @Test
  @WithMockRedHatPrincipal(
      value = "123456",
      roles = {})
  void testReportByMetricIdAccessDeniedWhenUserIsNotAnAdmin() {
    assertThrows(
        AccessDeniedException.class,
        () -> {
          resource.getCapacityReportByMetricId(
              RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);
        });
  }

  @Test
  void testReportByMetricIdGetCapacitiesWeekly() {
    SubscriptionCapacity capacity = new SubscriptionCapacity();
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    capacity.setBeginDate(begin);
    capacity.setEndDate(end);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.HYPERVISOR,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            min,
            max))
        .thenReturn(Collections.singletonList(capacity));

    List<CapacitySnapshotByMetricId> actual =
        resource.getCapacitiesByMetricId(
            "owner123456",
            RHEL,
            MetricId.CORES,
            HypervisorReportCategory.HYPERVISOR,
            ServiceLevel.STANDARD,
            Usage.PRODUCTION,
            Granularity.WEEKLY,
            begin,
            end);

    // Add one because we generate reports including both endpoints on the timeline
    long expected = ChronoUnit.WEEKS.between(begin, end) + 1;
    assertEquals(expected, actual.size());
  }

  @ParameterizedTest
  @MethodSource("usageLists")
  void testReportByMetricIdShouldCalculateCapacityRegardlessOfUsageSeenFirst(
      List<SubscriptionCapacity> usages) {
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.CORES, null, null, null, min, max))
        .thenReturn(usages);

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(4, capacitySnapshot.getValue());
  }

  static Stream<Arguments> usageLists() {
    SubscriptionCapacity limited = new SubscriptionCapacity();
    limited.setHasUnlimitedUsage(false);
    limited.setCores(4);
    limited.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    limited.setEndDate(max);
    SubscriptionCapacity unlimited = new SubscriptionCapacity();
    unlimited.setHasUnlimitedUsage(true);
    unlimited.setBeginDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));
    unlimited.setEndDate(max);

    return Stream.of(
        arguments(Arrays.asList(unlimited, limited)), arguments(Arrays.asList(limited, unlimited)));
  }
}
