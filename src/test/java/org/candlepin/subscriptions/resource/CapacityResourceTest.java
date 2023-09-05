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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.HypervisorReportCategory;
import org.candlepin.subscriptions.db.SubscriptionRepository;
import org.candlepin.subscriptions.db.model.DbReportCriteria;
import org.candlepin.subscriptions.db.model.Granularity;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Subscription;
import org.candlepin.subscriptions.db.model.SubscriptionMeasurementKey;
import org.candlepin.subscriptions.db.model.Usage;
import org.candlepin.subscriptions.exception.SubscriptionsException;
import org.candlepin.subscriptions.resteasy.PageLinkCreator;
import org.candlepin.subscriptions.security.WithMockRedHatPrincipal;
import org.candlepin.subscriptions.utilization.api.model.CapacityReport;
import org.candlepin.subscriptions.utilization.api.model.CapacityReportByMetricId;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshot;
import org.candlepin.subscriptions.utilization.api.model.CapacitySnapshotByMetricId;
import org.candlepin.subscriptions.utilization.api.model.GranularityType;
import org.candlepin.subscriptions.utilization.api.model.ReportCategory;
import org.candlepin.subscriptions.utilization.api.model.ServiceLevelType;
import org.candlepin.subscriptions.utilization.api.model.UsageType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@WithMockRedHatPrincipal("123456")
@ActiveProfiles({"api", "test"})
class CapacityResourceTest {

  private static final OffsetDateTime min = OffsetDateTime.now().minusDays(4);
  private static final OffsetDateTime max = OffsetDateTime.now().plusDays(4);
  private static final String BASILISK = "BASILISK";
  private static final String RHEL_FOR_ARM = "RHEL for ARM";
  private static final String METRIC_ID_CORES = "Cores";
  private static final String METRIC_ID_SOCKETS = "Sockets";

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

    var newProductIds = s.getSubscriptionProductIds();
    newProductIds.add(RHEL_FOR_ARM);
    s.setSubscriptionProductIds(newProductIds);

    return s;
  }

  private static Subscription enhancedSubscription(
      Map<SubscriptionMeasurementKey, Double> measurements) {
    Subscription s =
        datedSubscription(
            min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1),
            max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));

    var newMeasurements = new HashMap<>(s.getSubscriptionMeasurements());
    newMeasurements.putAll(measurements);
    s.setSubscriptionMeasurements(newMeasurements);

    return s;
  }

  Map<SubscriptionMeasurementKey, Double> createMeasurement(
      String type, MetricId metric, double value) {

    return Map.of(
        SubscriptionMeasurementKey.builder()
            .measurementType(type)
            .metricId(metric.toString())
            .build(),
        value);
  }

  private Map<SubscriptionMeasurementKey, Double> basicMeasurement() {
    return createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 42.0);
  }

  @Test
  void testShouldUseQueryBasedOnHeaderAndParameters() {

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseSlaQueryParam() {

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ServiceLevelType.PREMIUM,
            null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldUseUsageQueryParam() {

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .beginning(min)
            .ending(max)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, UsageType.PRODUCTION);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptySlaAsNull() {

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            ServiceLevelType.EMPTY,
            null);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldTreatEmptyUsageAsNull() {

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, UsageType.EMPTY);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testShouldCalculateCapacityBasedOnMultipleSubscriptions() {
    var hypSock1 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypSock2 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 7.0);

    var hypCore1 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var hypCore2 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 14.0);

    var sock1 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var sock2 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 11.0);

    var cores1 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);
    var cores2 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 22.0);

    var subs =
        Stream.of(sock1, sock2, cores1, cores2, hypSock1, hypSock2, hypCore1, hypCore2)
            .map(
                m -> {
                  var s =
                      Subscription.builder()
                          .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                          .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                          .orgId("owner123456")
                          .build();
                  s.getSubscriptionProductIds().add(RHEL_FOR_ARM);
                  s.getSubscriptionMeasurements().putAll(m);
                  return s;
                })
            .toList();

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted())).thenReturn(subs);

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, null);

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
              resource.getCapacityReport(
                  RHEL_FOR_ARM, GranularityType.DAILY, min, max, 11, 10, null, null);
            });
    assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testShouldRespectOffsetAndLimit() {

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReport report =
        resource.getCapacityReport(RHEL_FOR_ARM, GranularityType.DAILY, min, max, 1, 1, null, null);

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
          resource.getCapacityReport(
              RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, null);
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
          resource.getCapacityReport(
              RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, null);
        });
  }

  @Test
  void testGetCapacitiesWeekly() {
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    var subscription = datedSubscription(begin, end);

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .beginning(begin)
            .ending(end)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(subscription));

    List<CapacitySnapshot> actual =
        resource.getCapacities(
            "owner123456",
            ProductId.fromString(RHEL_FOR_ARM),
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
            .productId(RHEL_FOR_ARM)
            .beginning(min)
            .ending(max)
            .build();
    when(subscriptionRepository.findUnlimited(criteria)).thenReturn(List.of(subscription));

    DbReportCriteria dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .beginning(min)
            .ending(max)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(subscription));

    CapacityReport report =
        resource.getCapacityReport(
            RHEL_FOR_ARM, GranularityType.DAILY, min, max, null, null, null, null);

    CapacitySnapshot capacitySnapshot = report.getData().get(0);
    assertTrue(capacitySnapshot.getHasInfiniteQuantity());
  }

  @Test
  void testReportByMetricIdShouldUseQueryBasedOnHeaderAndParameters() {

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .hypervisorReportCategory(HypervisorReportCategory.NON_HYPERVISOR)
            .metricId(METRIC_ID_CORES)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel.PREMIUM)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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
    var hypSock1 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypSock2 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 7.0);

    var hypCore1 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var hypCore2 = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 14.0);

    var sock1 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var sock2 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 11.0);

    var cores1 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);
    var cores2 = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 22.0);

    var subs =
        Stream.of(sock1, sock2, cores1, cores2, hypSock1, hypSock2, hypCore1, hypCore2)
            .map(
                m -> {
                  var s =
                      Subscription.builder()
                          .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                          .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                          .orgId("owner123456")
                          .build();
                  s.getSubscriptionProductIds().add(RHEL_FOR_ARM);
                  s.getSubscriptionMeasurements().putAll(m);
                  return s;
                })
            .toList();

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted())).thenReturn(subs);

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(64, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityAllSockets() {
    var hypSock = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);

    var subs =
        Stream.of(hypSock, hypCore, sock, cores)
            .map(
                m -> {
                  var s =
                      Subscription.builder()
                          .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                          .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                          .orgId("owner123456")
                          .build();
                  s.getSubscriptionProductIds().add(RHEL_FOR_ARM);
                  s.getSubscriptionMeasurements().putAll(m);
                  return s;
                })
            .toList();

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_SOCKETS)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted())).thenReturn(subs);

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_SOCKETS,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(7, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualSockets() {
    var hypSock = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    var enhanced = enhancedSubscription(newMeasurements);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM.toString())
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .hypervisorReportCategory(HypervisorReportCategory.NON_HYPERVISOR)
            .metricId(METRIC_ID_SOCKETS)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(enhanced));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_SOCKETS,
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
    var hypSock = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    var enhanced = enhancedSubscription(newMeasurements);

    when(subscriptionRepository.findByCriteria(any(DbReportCriteria.class), any()))
        .thenReturn(List.of(enhanced));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_SOCKETS,
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
    var hypSock = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    var enhanced = enhancedSubscription(newMeasurements);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(enhanced));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(28, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualCores() {
    var hypSock = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    var enhanced = enhancedSubscription(newMeasurements);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .hypervisorReportCategory(HypervisorReportCategory.NON_HYPERVISOR)
            .metricId(METRIC_ID_CORES)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(enhanced));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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
    var hypSock = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_SOCKETS), 5.0);
    var hypCore = createMeasurement("HYPERVISOR", MetricId.fromString(METRIC_ID_CORES), 20.0);
    var sock = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_SOCKETS), 2.0);
    var cores = createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    var enhanced = enhancedSubscription(newMeasurements);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .hypervisorReportCategory(HypervisorReportCategory.NON_HYPERVISOR)
            .metricId(METRIC_ID_CORES)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(enhanced));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
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
                  RHEL_FOR_ARM,
                  METRIC_ID_CORES,
                  GranularityType.DAILY,
                  min,
                  max,
                  11,
                  10,
                  null,
                  null,
                  null);
            });
    assertEquals(Response.Status.BAD_REQUEST, e.getStatus());
  }

  @Test
  void testReportByMetricIdShouldRespectOffsetAndLimit() {

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage._ANY)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    Subscription s = new Subscription();
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM, METRIC_ID_CORES, GranularityType.DAILY, min, max, 1, 1, null, null, null);

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
              RHEL_FOR_ARM,
              METRIC_ID_CORES,
              GranularityType.DAILY,
              min,
              max,
              null,
              null,
              null,
              null,
              null);
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
              RHEL_FOR_ARM,
              METRIC_ID_CORES,
              GranularityType.DAILY,
              min,
              max,
              null,
              null,
              null,
              null,
              null);
        });
  }

  @Test
  void testReportByMetricIdGetCapacitiesWeekly() {
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    var s = datedSubscription(begin, end);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(ServiceLevel._ANY)
            .usage(Usage.PRODUCTION)
            .beginning(min)
            .ending(max)
            .hypervisorReportCategory(HypervisorReportCategory.HYPERVISOR)
            .metricId(METRIC_ID_CORES)
            .build();

    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(s));

    List<CapacitySnapshotByMetricId> actual =
        resource.getCapacitiesByMetricId(
            "owner123456",
            ProductId.fromString(RHEL_FOR_ARM),
            MetricId.fromString(METRIC_ID_CORES),
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

    limited.setSubscriptionMeasurements(
        createMeasurement("PHYSICAL", MetricId.fromString(METRIC_ID_CORES), 4.0));

    var limitedOffering = Offering.builder().sku("limitedsku").hasUnlimitedUsage(false).build();
    limited.setOffering(limitedOffering);

    var dbReportCriteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .serviceLevel(null)
            .usage(null)
            .beginning(min)
            .ending(max)
            .metricId(METRIC_ID_CORES)
            .build();

    when(subscriptionRepository.findByCriteria(dbReportCriteria, Sort.unsorted()))
        .thenReturn(List.of(limited));

    var criteria =
        DbReportCriteria.builder()
            .orgId("owner123456")
            .productId(RHEL_FOR_ARM)
            .beginning(min)
            .ending(max)
            .build();

    when(subscriptionRepository.findUnlimited(criteria)).thenReturn(List.of(unlimited));

    CapacityReportByMetricId report =
        resource.getCapacityReportByMetricId(
            RHEL_FOR_ARM,
            METRIC_ID_CORES,
            GranularityType.DAILY,
            min,
            max,
            null,
            null,
            null,
            null,
            null);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(4, capacitySnapshot.getValue());
  }

  @Test
  void testValidateGranularityIncompatible() {
    var thrownException =
        Assertions.assertThrows(
            BadRequestException.class,
            () ->
                resource.getCapacityReportByMetricId(
                    RHEL_FOR_ARM,
                    METRIC_ID_CORES,
                    GranularityType.HOURLY,
                    min,
                    max,
                    null,
                    null,
                    null,
                    null,
                    null));

    assertEquals(
        String.format(
            "%s does not support any granularity finer than %s",
            RHEL_FOR_ARM, GranularityType.HOURLY),
        thrownException.getMessage());
  }

  @ParameterizedTest
  @MethodSource("generateFinestGranularityCases")
  void testValidateGranularity(ProductId productId, GranularityType granularity) {
    assertDoesNotThrow(
        () ->
            resource.getCapacityReportByMetricId(
                productId.toString(),
                METRIC_ID_CORES,
                granularity,
                min,
                max,
                null,
                null,
                null,
                null,
                null));
  }

  private static Stream<Arguments> generateFinestGranularityCases() {
    return Stream.of(
        Arguments.of(BASILISK, GranularityType.HOURLY),
        Arguments.of(RHEL_FOR_ARM, GranularityType.YEARLY),
        Arguments.of(BASILISK, GranularityType.YEARLY),
        Arguments.of(RHEL_FOR_ARM, GranularityType.DAILY));
  }

  @Test()
  void testGetCapacityReportByMetricIdThrowsExceptionForUnknownMetricId() {
    assertThrows(
        BadRequestException.class,
        () ->
            resource.getCapacityReportByMetricId(
                RHEL_FOR_ARM,
                "NotAMetricId",
                GranularityType.DAILY,
                min,
                max,
                null,
                null,
                ReportCategory.PHYSICAL,
                null,
                null));
  }

  @Test()
  void testGetCapacityReportByMetricIdThrowsExceptionForUnknownProductId() {
    assertThrows(
        BadRequestException.class,
        () ->
            resource.getCapacityReportByMetricId(
                "NotARealProduct",
                METRIC_ID_CORES,
                GranularityType.DAILY,
                min,
                max,
                null,
                null,
                ReportCategory.PHYSICAL,
                null,
                null));
  }
}
