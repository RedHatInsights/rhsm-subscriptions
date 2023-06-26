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
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.ws.rs.core.Response;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionMeasurementRepository;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurement;
import org.candlepin.subscriptions.db.model.SubscriptionProductId;
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

  @MockBean SubscriptionMeasurementRepository repository;
  @MockBean SubscriptionRepository subscriptionRepository;
  @MockBean PageLinkCreator pageLinkCreator;
  @MockBean AccountConfigRepository accountConfigRepository;

  @Autowired CapacityResource resource;

  @BeforeEach
  public void setupTests() {
    when(accountConfigRepository.existsByOrgId("owner123456")).thenReturn(true);
  }

  private static Subscription datedSubscription(OffsetDateTime start, OffsetDateTime end) {
    Subscription s =
        Subscription.builder()
            .subscriptionId("subscription123")
            .startDate(start)
            .endDate(end)
            .orgId("owner123456")
            .build();

    s.addSubscriptionProductId(SubscriptionProductId.builder().productId(RHEL.toString()).build());

    return s;
  }

  private static Subscription enhancedSubscription(List<SubscriptionMeasurement> measurements) {
    Subscription s =
        datedSubscription(
            min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1),
            max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));

    s.addSubscriptionMeasurements(measurements);
    return s;
  }

  SubscriptionMeasurement createMeasurement(String type, MetricId metric, double value) {
    return SubscriptionMeasurement.builder()
        .measurementType(type)
        .metricId(metric.toString().toUpperCase())
        .value(value)
        .build();
  }

  private static SubscriptionMeasurement basicMeasurement() {
    return SubscriptionMeasurement.builder()
        .measurementType("PHYSICAL")
        .metricId(MetricId.CORES.toString().toUpperCase())
        .value(42.0)
        .build();
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(List.of(basicMeasurement()));

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel.PREMIUM, Usage._ANY, min, max))
        .thenReturn(List.of(basicMeasurement()));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, ServiceLevelType.PREMIUM, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseUsageQueryParam() {
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage.PRODUCTION, min, max))
        .thenReturn(List.of(basicMeasurement()));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, null, UsageType.PRODUCTION);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptySlaAsNull() {
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(List.of(basicMeasurement()));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, ServiceLevelType.EMPTY, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptyUsageAsNull() {
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(List.of(basicMeasurement()));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL, GranularityType.DAILY, min, max, null, null, null, UsageType.EMPTY);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldCalculateCapacityBasedOnMultipleSubscriptions() {
    var hypSock1 = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypSock2 = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 7.0);

    var hypCore1 = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var hypCore2 = createMeasurement("HYPERVISOR", MetricId.CORES, 14.0);

    var sock1 = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var sock2 = createMeasurement("PHYSICAL", MetricId.SOCKETS, 11.0);

    var cores1 = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);
    var cores2 = createMeasurement("PHYSICAL", MetricId.CORES, 22.0);

    Subscription s =
        Subscription.builder()
            .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
            .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
            .orgId("owner123456")
            .build();

    s.addSubscriptionProductId(SubscriptionProductId.builder().productId(RHEL.toString()).build());
    List<SubscriptionMeasurement> measurements =
        List.of(sock1, sock2, cores1, cores2, hypSock1, hypSock2, hypCore1, hypCore2);
    s.addSubscriptionMeasurements(measurements);

    when(repository.findAllBy("owner123456", RHEL.toString(), null, null, min, max))
        .thenReturn(measurements);

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
    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage._ANY, min, max))
        .thenReturn(List.of(basicMeasurement()));

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
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    var subscription = datedSubscription(begin, end);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), ServiceLevel._ANY, Usage.PRODUCTION, begin, end))
        .thenReturn(subscription.getSubscriptionMeasurements());

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
    OffsetDateTime begin = min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1);
    var subscription = datedSubscription(begin, max);
    var offering = Offering.builder().sku("testsku").hasUnlimitedUsage(true).build();
    subscription.setOffering(offering);

    DbReportCriteria criteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL.toString())
            .beginning(min)
            .ending(max)
            .build();
    when(subscriptionRepository.findUnlimited(criteria)).thenReturn(List.of(subscription));

    when(repository.findAllBy("owner123456", RHEL.toString(), null, null, min, max))
        .thenReturn(subscription.getSubscriptionMeasurements());

    CapacityReport report =
        resource.getCapacityReport(RHEL, GranularityType.DAILY, min, max, null, null, null, null);

    CapacitySnapshot capacitySnapshot = report.getData().get(0);
    assertTrue(capacitySnapshot.getHasInfiniteQuantity());
  }

  @Test
  void testReportByMetricIdShouldUseQueryBasedOnHeaderAndParameters() {
    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(List.of(basicMeasurement()));

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
    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel.PREMIUM,
            Usage._ANY,
            min,
            max))
        .thenReturn(List.of(basicMeasurement()));

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
    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            min,
            max))
        .thenReturn(List.of(basicMeasurement()));

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
    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(List.of(basicMeasurement()));

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
    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(List.of(basicMeasurement()));

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
    var hypSock1 = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypSock2 = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 7.0);

    var hypCore1 = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var hypCore2 = createMeasurement("HYPERVISOR", MetricId.CORES, 14.0);

    var sock1 = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var sock2 = createMeasurement("PHYSICAL", MetricId.SOCKETS, 11.0);

    var cores1 = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);
    var cores2 = createMeasurement("PHYSICAL", MetricId.CORES, 22.0);

    List<SubscriptionMeasurement> measurements =
        List.of(sock1, sock2, cores1, cores2, hypSock1, hypSock2, hypCore1, hypCore2);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.CORES, null, null, null, min, max))
        .thenReturn(measurements);

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(64, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityAllSockets() {
    var hypSock = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);

    List<SubscriptionMeasurement> measurements = List.of(sock, cores, hypCore, hypSock);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.SOCKETS, null, null, null, min, max))
        .thenReturn(measurements);

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.SOCKETS, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(7, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualSockets() {
    var hypSock = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);

    List<SubscriptionMeasurement> measurements = List.of(sock, cores, hypCore, hypSock);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.SOCKETS,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(measurements);

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
    var hypSock = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);

    List<SubscriptionMeasurement> measurements = List.of(sock, cores, hypCore, hypSock);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.SOCKETS,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(measurements);

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
    var hypSock = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);

    List<SubscriptionMeasurement> measurements = List.of(sock, cores, hypCore, hypSock);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.CORES, null, null, null, min, max))
        .thenReturn(measurements);

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(28, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualCores() {
    var hypSock = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);

    List<SubscriptionMeasurement> measurements = List.of(sock, cores, hypCore, hypSock);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(measurements);

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
    var hypSock = createMeasurement("HYPERVISOR", MetricId.SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.CORES, 8.0);

    List<SubscriptionMeasurement> measurements = List.of(sock, cores, hypCore, hypSock);
    enhancedSubscription(measurements);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.NON_HYPERVISOR,
            null,
            null,
            min,
            max))
        .thenReturn(measurements);

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
    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            null,
            ServiceLevel._ANY,
            Usage._ANY,
            min,
            max))
        .thenReturn(List.of(basicMeasurement()));

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
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    var s = datedSubscription(begin, end);

    when(repository.findAllBy(
            "owner123456",
            RHEL.toString(),
            MetricId.CORES,
            HypervisorReportCategory.HYPERVISOR,
            ServiceLevel._ANY,
            Usage.PRODUCTION,
            min,
            max))
        .thenReturn(s.getSubscriptionMeasurements());

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

  @Test
  void testReportByMetricIdShouldCalculateCapacityEvenWhenUnlimited() {
    var begin = min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1);
    var unlimited = datedSubscription(begin, max);
    unlimited.setSubscriptionId("unlimited123");
    var unlimitedOffering = Offering.builder().sku("unlimitedsku").hasUnlimitedUsage(true).build();
    unlimited.setOffering(unlimitedOffering);

    var limited = datedSubscription(begin, max);
    limited.setSubscriptionId("limited123");
    limited.addSubscriptionMeasurements(
        List.of(createMeasurement("PHYSICAL", MetricId.CORES, 4.0)));
    var limitedOffering = Offering.builder().sku("limitedsku").hasUnlimitedUsage(false).build();
    limited.setOffering(limitedOffering);

    when(repository.findAllBy(
            "owner123456", RHEL.toString(), MetricId.CORES, null, null, null, min, max))
        .thenReturn(limited.getSubscriptionMeasurements());

    var criteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL.toString())
            .beginning(min)
            .ending(max)
            .build();

    when(subscriptionRepository.findUnlimited(criteria)).thenReturn(List.of(unlimited));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL, MetricId.CORES, GranularityType.DAILY, min, max, null, null, null, null, null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(4, capacitySnapshot.getValue());
  }
}
