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
package com.redhat.swatch.contract.resource.api.v1;

import static com.redhat.swatch.common.security.RhIdentityHeaderAuthenticationMechanism.RH_IDENTITY_HEADER;
import static com.redhat.swatch.contract.security.RhIdentityUtils.CUSTOMER_IDENTITY_HEADER;
import static io.restassured.RestAssured.given;
import static java.util.Objects.isNull;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import com.redhat.swatch.common.model.Usage;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.registry.ProductId;
import com.redhat.swatch.contract.openapi.model.CapacityReportByMetricId;
import com.redhat.swatch.contract.openapi.model.CapacitySnapshotByMetricId;
import com.redhat.swatch.contract.openapi.model.GranularityType;
import com.redhat.swatch.contract.openapi.model.ReportCategory;
import com.redhat.swatch.contract.openapi.model.ServiceLevelType;
import com.redhat.swatch.contract.openapi.model.UsageType;
import com.redhat.swatch.contract.repository.HypervisorReportCategory;
import com.redhat.swatch.contract.repository.OfferingEntity;
import com.redhat.swatch.contract.repository.SubscriptionEntity;
import com.redhat.swatch.contract.repository.SubscriptionMeasurementKey;
import com.redhat.swatch.contract.repository.SubscriptionRepository;
import com.redhat.swatch.contract.test.resources.DisableRbacResource;
import io.quarkus.panache.common.Sort;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ExtractableResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@TestProfile(DisableRbacResource.class)
class CapacityResourceV1Test {

  private static final OffsetDateTime min =
      OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).minusDays(4);
  private static final OffsetDateTime max =
      OffsetDateTime.now().withOffsetSameInstant(ZoneOffset.UTC).plusDays(4);
  private static final ProductId BASILISK = ProductId.fromString("BASILISK");
  private static final ProductId RHEL_FOR_ARM = ProductId.fromString("RHEL for ARM");
  private static final MetricId METRIC_ID_CORES = MetricId.fromString("Cores");
  private static final MetricId METRIC_ID_SOCKETS = MetricId.fromString("Sockets");

  @InjectMock SubscriptionRepository subscriptionRepository;

  private static SubscriptionEntity datedSubscription(OffsetDateTime start, OffsetDateTime end) {
    return SubscriptionEntity.builder()
        .subscriptionId("subscription123")
        .startDate(start)
        .endDate(end)
        .orgId("org123")
        .build();
  }

  private static SubscriptionEntity enhancedSubscription(
      Map<SubscriptionMeasurementKey, Double> measurements) {
    SubscriptionEntity s =
        datedSubscription(
            min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1),
            max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1));

    var newMeasurements = new HashMap<>(s.getSubscriptionMeasurements());
    newMeasurements.putAll(measurements);
    s.setSubscriptionMeasurements(newMeasurements);

    return s;
  }

  private static SubscriptionEntity basicSubscription() {
    return SubscriptionEntity.builder()
        .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
        .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
        .orgId("org123")
        .subscriptionMeasurements(basicMeasurement())
        .build();
  }

  static Map<SubscriptionMeasurementKey, Double> createMeasurement(
      String type, MetricId metric, double value) {
    return Map.of(new SubscriptionMeasurementKey(metric.toString(), type), value);
  }

  private static Map<SubscriptionMeasurementKey, Double> basicMeasurement() {
    return createMeasurement("PHYSICAL", METRIC_ID_CORES, 42.0);
  }

  @Test
  void testReportByMetricIdShouldUseQueryBasedOnHeaderAndParameters() {
    when(subscriptionRepository.findByCriteria(
            argThat(
                argument ->
                    argument.getOrgId().equals("org123")
                        && argument
                            .getHypervisorReportCategory()
                            .equals(HypervisorReportCategory.NON_HYPERVISOR.toString())),
            any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "category", ReportCategory.PHYSICAL.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldUseSlaQueryParam() {
    when(subscriptionRepository.findByCriteria(
            argThat(
                argument ->
                    argument.getOrgId().equals("org123")
                        && argument.getServiceLevel().equals(ServiceLevelType.PREMIUM.toString())),
            any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "sla", ServiceLevelType.PREMIUM.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldUseUsageQueryParam() {
    when(subscriptionRepository.findByCriteria(
            argThat(
                argument ->
                    argument.getOrgId().equals("org123")
                        && argument.getUsage().equals(Usage.PRODUCTION.getValue())),
            any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "usage", UsageType.PRODUCTION.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldUseBillingAccountQueryParam() {
    when(subscriptionRepository.findByCriteria(
            argThat(
                argument ->
                    argument.getOrgId().equals("org123")
                        && argument.getBillingAccountId().equals("account123456")),
            any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "billing_account_id", "account123456")
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldTreatEmptySlaAsNull() {
    when(subscriptionRepository.findByCriteria(
            argThat(
                argument ->
                    argument.getOrgId().equals("org123") && isNull(argument.getServiceLevel())),
            any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "sla", ServiceLevelType.EMPTY.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldTreatEmptyUsageAsNull() {
    when(subscriptionRepository.findByCriteria(
            argThat(
                argument -> argument.getOrgId().equals("org123") && isNull(argument.getUsage())),
            any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "usage", UsageType.EMPTY.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(9, report.getData().size());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityBasedOnMultipleSubscriptions() {
    var hypSock1 = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypSock2 = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 7.0);

    var hypCore1 = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var hypCore2 = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 14.0);

    var sock1 = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var sock2 = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 11.0);

    var cores1 = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);
    var cores2 = createMeasurement("PHYSICAL", METRIC_ID_CORES, 22.0);

    var subs =
        Stream.of(sock1, sock2, cores1, cores2, hypSock1, hypSock2, hypCore1, hypCore2)
            .map(
                m ->
                    SubscriptionEntity.builder()
                        .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                        .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                        .orgId("org123")
                        .subscriptionMeasurements(m)
                        .build())
            .toList();

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(subs);

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(64, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityAllSockets() {
    var hypSock = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);

    var subs =
        Stream.of(hypSock, hypCore, sock, cores)
            .map(
                m ->
                    SubscriptionEntity.builder()
                        .startDate(min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                        .endDate(max.truncatedTo(ChronoUnit.DAYS).minusSeconds(1))
                        .orgId("owner123456")
                        .subscriptionMeasurements(m)
                        .build())
            .toList();

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(subs);

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_SOCKETS.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(7, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualSockets() {
    var hypSock = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(enhancedSubscription(newMeasurements)));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "category", ReportCategory.VIRTUAL.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_SOCKETS.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(2, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityPhysicalSockets() {
    var hypSock = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(enhancedSubscription(newMeasurements)));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "category", ReportCategory.PHYSICAL.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_SOCKETS.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(2, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityAllCores() {
    var hypSock = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(enhancedSubscription(newMeasurements)));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(28, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityVirtualCores() {
    var hypSock = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(enhancedSubscription(newMeasurements)));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "category", ReportCategory.VIRTUAL.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(8, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityPhysicalCores() {
    var hypSock = createMeasurement("HYPERVISOR", METRIC_ID_SOCKETS, 5.0);
    var hypCore = createMeasurement("HYPERVISOR", METRIC_ID_CORES, 20.0);
    var sock = createMeasurement("PHYSICAL", METRIC_ID_SOCKETS, 2.0);
    var cores = createMeasurement("PHYSICAL", METRIC_ID_CORES, 8.0);

    var newMeasurements = new HashMap<SubscriptionMeasurementKey, Double>();

    List.of(sock, cores, hypCore, hypSock).forEach(newMeasurements::putAll);

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(enhancedSubscription(newMeasurements)));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "category", ReportCategory.PHYSICAL.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(8, capacitySnapshot.getValue());
  }

  @Test
  void testReportByMetricIdShouldThrowExceptionOnBadOffset() {
    given()
        .queryParams(
            "offset", "11",
            "limit", "10",
            "granularity", GranularityType.DAILY.toString(),
            "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get(
            String.format(
                "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
        .then()
        .body("title", equalTo("Offset must be divisible by limit"))
        .statusCode(400);
  }

  @Test
  void testReportByMetricIdShouldRespectOffsetAndLimit() {
    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(basicSubscription()));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "offset", "1",
                "limit", "1",
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    assertEquals(1, report.getData().size());
    assertEquals(
        min.truncatedTo(ChronoUnit.DAYS).plusDays(1),
        report.getData().get(0).getDate().truncatedTo(ChronoUnit.DAYS));
  }

  @Test
  void testReportByMetricIdAccessDeniedWhenUserIsNotAConsumer() {
    given()
        .queryParams(
            "granularity", GranularityType.DAILY.toString(),
            "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
        .header(RH_IDENTITY_HEADER, "placeholder")
        .get(
            String.format(
                "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
        .then()
        .statusCode(401);
  }

  @Test
  void testReportByMetricIdGetCapacitiesWeekly() {
    OffsetDateTime begin = OffsetDateTime.parse("2020-12-03T10:15:30+00:00");
    OffsetDateTime end = OffsetDateTime.parse("2020-12-17T10:15:30+00:00");
    var s = datedSubscription(begin, end);
    s.setSubscriptionMeasurements(basicMeasurement());

    when(subscriptionRepository.findByCriteria(
            argThat(argument -> argument.getOrgId().equals("org123")), any(Sort.class)))
        .thenReturn(List.of(s));

    ExtractableResponse actual =
        given()
            .queryParams(
                "granularity", GranularityType.WEEKLY.toString(),
                "beginning", begin.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", end.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "category", ReportCategory.HYPERVISOR.toString(),
                "sla", ServiceLevelType.STANDARD.toString(),
                "usage", UsageType.PRODUCTION.toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract();

    // Add one because we generate reports including both endpoints on the timeline
    long expected = ChronoUnit.WEEKS.between(begin, end) + 1;
    assertEquals(expected, actual.body().jsonPath().getList("data").size());
  }

  @Test
  void testReportByMetricIdShouldCalculateCapacityEvenWhenUnlimited() {
    var begin = min.truncatedTo(ChronoUnit.DAYS).minusSeconds(1);
    var unlimited = datedSubscription(begin, max);
    unlimited.setSubscriptionId("unlimited123");
    var unlimitedOffering =
        OfferingEntity.builder().sku("unlimitedsku").hasUnlimitedUsage(true).build();
    unlimited.setOffering(unlimitedOffering);

    var limited = datedSubscription(begin, max);
    limited.setSubscriptionId("limited123");

    limited.setSubscriptionMeasurements(createMeasurement("PHYSICAL", METRIC_ID_CORES, 4.0));

    var limitedOffering =
        OfferingEntity.builder().sku("limitedsku").hasUnlimitedUsage(false).build();
    limited.setOffering(limitedOffering);

    when(subscriptionRepository.findByCriteria(
            argThat(
                argument ->
                    argument.getOrgId().equals("org123")
                        && argument.getMetricId().equals(METRIC_ID_CORES.toString())),
            any(Sort.class)))
        .thenReturn(List.of(limited));

    when(subscriptionRepository.findUnlimited(
            argThat(argument -> argument.getOrgId().equals("org123"))))
        .thenReturn(List.of(unlimited));

    CapacityReportByMetricId report =
        given()
            .queryParams(
                "granularity", GranularityType.DAILY.toString(),
                "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
                "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
            .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
            .get(
                String.format(
                    "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                    RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
            .then()
            .statusCode(200)
            .extract()
            .as(CapacityReportByMetricId.class);

    CapacitySnapshotByMetricId capacitySnapshot = report.getData().get(0);
    assertEquals(4, capacitySnapshot.getValue());
  }

  @Test
  void testValidateGranularityIncompatible() {
    given()
        .queryParams(
            "granularity", GranularityType.HOURLY.toString(),
            "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get(
            String.format(
                "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                RHEL_FOR_ARM.getValue(), METRIC_ID_CORES.getValue()))
        .then()
        .statusCode(400);
  }

  @ParameterizedTest
  @MethodSource("generateFinestGranularityCases")
  void testValidateGranularity(ProductId productId, GranularityType granularity) {
    given()
        .queryParams(
            "granularity", granularity.toString(),
            "beginning", min.withOffsetSameInstant(ZoneOffset.UTC).toString(),
            "ending", max.withOffsetSameInstant(ZoneOffset.UTC).toString())
        .header(RH_IDENTITY_HEADER, CUSTOMER_IDENTITY_HEADER)
        .get(
            String.format(
                "/api/rhsm-subscriptions/v1/capacity/products/%s/%s",
                productId.toString(), METRIC_ID_CORES.getValue()))
        .then()
        .statusCode(200)
        .extract()
        .as(CapacityReportByMetricId.class);
  }

  private static Stream<Arguments> generateFinestGranularityCases() {
    return Stream.of(
        Arguments.of(BASILISK, GranularityType.HOURLY),
        Arguments.of(RHEL_FOR_ARM, GranularityType.YEARLY),
        Arguments.of(BASILISK, GranularityType.YEARLY),
        Arguments.of(RHEL_FOR_ARM, GranularityType.DAILY));
  }
}
