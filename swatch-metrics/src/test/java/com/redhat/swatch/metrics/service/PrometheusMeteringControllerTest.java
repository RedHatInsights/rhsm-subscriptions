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
package com.redhat.swatch.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.prometheus.api.model.QueryResult;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultData;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultDataResultInner;
import com.redhat.swatch.clients.prometheus.api.model.ResultType;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import com.redhat.swatch.metrics.exception.ExternalServiceException;
import com.redhat.swatch.metrics.resources.InjectPrometheus;
import com.redhat.swatch.metrics.resources.PrometheusQueryWiremock;
import com.redhat.swatch.metrics.resources.PrometheusResource;
import com.redhat.swatch.metrics.service.promql.QueryBuilder;
import com.redhat.swatch.metrics.test.resources.InMemoryMessageBrokerKafkaResource;
import com.redhat.swatch.metrics.util.MeteringEventFactory;
import com.redhat.swatch.metrics.utils.QueryHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@QuarkusTest
@QuarkusTestResource(value = PrometheusResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(
    value = InMemoryMessageBrokerKafkaResource.class,
    restrictToAnnotatedClass = true)
class PrometheusMeteringControllerTest {

  static final String PROMETHEUS = "prometheus";

  private final String expectedOrgId = "my-test-org";
  private final String expectedClusterId = "C1";
  private final String expectedSla = "Premium";
  private final String expectedUsage = "Production";
  private final String expectedBillingProvider = "red hat";
  private final String expectedBillingAccountId = "mktp-account";
  private final String expectedDisplayName = "display name";
  private final String expectedProductTag = "OpenShift-metrics";
  private final UUID expectedSpanId = UUID.randomUUID();

  @Inject PrometheusMeteringController controller;

  @Inject MetricProperties metricProperties;

  @Inject QueryBuilder queryBuilder;

  @InjectMock SpanGenerator spanGenerator;

  @Inject ApplicationClock clock;
  @InjectPrometheus PrometheusQueryWiremock prometheusServer;
  @Inject @Any InMemoryConnector connector;

  private InMemorySink<Event> results;
  private QueryHelper queries;

  @BeforeEach
  void setupTest() {
    prometheusServer.resetScenario();
    results = connector.sink("events-out");
    results.clear();
    queries = new QueryHelper(queryBuilder);
    when(spanGenerator.generate()).thenReturn(expectedSpanId);
  }

  @Test
  void testProductLabelAsEngineeringIds() {
    var productTag = "rhel-for-x86-els-payg";
    var metricId = "vCPUs";
    var serviceType = "RHEL System";

    var clusterId = UUID.randomUUID().toString();
    var billingProvider = "aws";
    var billingMarketplaceAccount = UUID.randomUUID().toString();
    var billingMarketplaceInstanceId = UUID.randomUUID().toString();
    var billingModel = "marketplace";
    var displayName = "rhel-metering";
    var externalOrganization = "18078360";
    var products = "204,317,69";
    var receive = true;
    var socketCount = 1;
    var tenantId = UUID.randomUUID().toString();
    var is3rdPartyMigrationFlag = true;

    QueryResultDataResultInner dataResult =
        new QueryResultDataResultInner()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", expectedSla)
            .putMetricItem("usage", expectedUsage)
            .putMetricItem("external_organization", externalOrganization)
            .putMetricItem("billing_marketplace", billingProvider)
            .putMetricItem("billing_marketplace_account", billingMarketplaceAccount)
            .putMetricItem("display_name", displayName)
            .putMetricItem("billing_marketplace_instance_id", billingMarketplaceInstanceId)
            .putMetricItem("billing_marketplace_model", billingModel)
            .putMetricItem("product", products)
            .putMetricItem("receive", String.valueOf(receive))
            .putMetricItem("socket_count", String.valueOf(socketCount))
            .putMetricItem("tenant_id", tenantId)
            .putMetricItem("conversions_success", String.valueOf(is3rdPartyMigrationFlag));
    ;

    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);
    BigDecimal time2 = BigDecimal.valueOf(222222.222);
    BigDecimal val2 = BigDecimal.valueOf(120L);

    var timeValueTuples = List.of(List.of(time1, val1), List.of(time2, val2));
    timeValueTuples.forEach(dataResult::addValuesItem);

    var data =
        new QueryResult()
            .status(StatusType.SUCCESS)
            .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));

    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusDays(1);

    whenCollectMetrics(externalOrganization, productTag, MetricId.fromString(metricId), start, end);

    var actual = results.received().stream().map(Message::getPayload).toList();
    assertEquals(
        List.of(
            MeteringEventFactory.createMetricEvent(
                externalOrganization,
                clusterId,
                expectedSla,
                expectedUsage,
                null,
                PROMETHEUS, // this would actually be "rhelemeter" set by an env var
                clock.dateFromUnix(time1).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time1),
                serviceType,
                billingProvider,
                billingMarketplaceAccount,
                MetricId.fromString(metricId),
                val1.doubleValue(),
                productTag,
                expectedSpanId,
                controller.extractProductIdsFromProductLabel(products),
                displayName,
                is3rdPartyMigrationFlag),
            MeteringEventFactory.createMetricEvent(
                externalOrganization,
                clusterId,
                expectedSla,
                expectedUsage,
                null,
                PROMETHEUS, // this would actually be "rhelemeter" set by an env var
                clock.dateFromUnix(time2).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time2),
                serviceType,
                billingProvider,
                billingMarketplaceAccount,
                MetricId.fromString(metricId),
                val2.doubleValue(),
                productTag,
                expectedSpanId,
                controller.extractProductIdsFromProductLabel(products),
                displayName,
                is3rdPartyMigrationFlag)),
        actual);
  }

  @Test
  void testProductLabelNonProductIds() {
    var productTag = "rosa";
    var metricId = "Instance-hours";
    var serviceType = "rosa Instance";
    var clusterId = UUID.randomUUID().toString();
    var billingProvider = "aws";
    var billingMarketplaceAccount = UUID.randomUUID().toString();
    var billingMarketplaceInstanceId = UUID.randomUUID().toString();
    var billingModel = "marketplace";
    var displayName = "rhel-metering";
    var externalOrganization = "18078360";
    var product = "moa-hostedcontrolplane";
    var receive = true;
    var socketCount = 1;
    var tenantId = UUID.randomUUID().toString();
    var is3rdPartyMigrationFlag = false;

    QueryResultDataResultInner dataResult =
        new QueryResultDataResultInner()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", expectedSla)
            .putMetricItem("usage", expectedUsage)
            .putMetricItem("external_organization", externalOrganization)
            .putMetricItem("billing_marketplace", billingProvider)
            .putMetricItem("billing_marketplace_account", billingMarketplaceAccount)
            .putMetricItem("display_name", displayName)
            .putMetricItem("billing_marketplace_instance_id", billingMarketplaceInstanceId)
            .putMetricItem("billing_marketplace_model", billingModel)
            .putMetricItem("product", product)
            .putMetricItem("receive", String.valueOf(receive))
            .putMetricItem("socket_count", String.valueOf(socketCount))
            .putMetricItem("tenant_id", tenantId)
            .putMetricItem("conversions_success", String.valueOf(is3rdPartyMigrationFlag));

    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);

    var timeValueTuples = List.of(List.of(time1, val1));
    timeValueTuples.forEach(dataResult::addValuesItem);

    var data =
        new QueryResult()
            .status(StatusType.SUCCESS)
            .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));

    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusDays(1);

    whenCollectMetrics(externalOrganization, productTag, MetricId.fromString(metricId), start, end);

    var actual = results.received().stream().map(Message::getPayload).toList();
    assertEquals(
        List.of(
            MeteringEventFactory.createMetricEvent(
                externalOrganization,
                clusterId,
                expectedSla,
                expectedUsage,
                product,
                PROMETHEUS,
                clock.dateFromUnix(time1).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time1),
                serviceType,
                billingProvider,
                billingMarketplaceAccount,
                MetricId.fromString(metricId),
                val1.doubleValue(),
                productTag,
                expectedSpanId,
                List.of(),
                displayName,
                is3rdPartyMigrationFlag)),
        actual);
  }

  @Test
  void testWhenProductTagNotFound() {

    var clusterId = UUID.randomUUID().toString();
    var billingProvider = "aws";
    var billingMarketplaceAccount = UUID.randomUUID().toString();
    var billingMarketplaceInstanceId = UUID.randomUUID().toString();
    var billingModel = "marketplace";
    var displayName = "rhel-metering";
    var externalOrganization = "18078360";
    var products = "204,317,69";
    var receive = true;
    var socketCount = 1;
    var tenantId = UUID.randomUUID().toString();
    var is3rdPartyMigrationFlag = false;

    QueryResultDataResultInner dataResult =
        new QueryResultDataResultInner()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", expectedSla)
            .putMetricItem("usage", expectedUsage)
            .putMetricItem("external_organization", externalOrganization)
            .putMetricItem("billing_marketplace", billingProvider)
            .putMetricItem("billing_marketplace_account", billingMarketplaceAccount)
            .putMetricItem("display_name", displayName)
            .putMetricItem("billing_marketplace_instance_id", billingMarketplaceInstanceId)
            .putMetricItem("billing_marketplace_model", billingModel)
            .putMetricItem("product", products)
            .putMetricItem("receive", String.valueOf(receive))
            .putMetricItem("socket_count", String.valueOf(socketCount))
            .putMetricItem("tenant_id", tenantId)
            .putMetricItem("conversions_success", String.valueOf(is3rdPartyMigrationFlag));
    ;

    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);

    var timeValueTuples = List.of(List.of(time1, val1));
    timeValueTuples.forEach(dataResult::addValuesItem);

    var data =
        new QueryResult()
            .status(StatusType.SUCCESS)
            .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));

    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusDays(1);

    String productTag = "rhel-for-x86-els-payg";
    whenCollectMetrics(externalOrganization, productTag, MetricId.fromString("vCPUs"), start, end);

    var actual = results.received().stream().map(Message::getPayload).toList();
    assertEquals(List.of(), actual);
  }

  @Test
  void testResourceNameLabelAsRole() {

    var clusterId = UUID.randomUUID().toString();
    var billingProvider = "aws";
    var billingMarketplaceAccount = UUID.randomUUID().toString();
    var billingMarketplaceInstanceId = UUID.randomUUID().toString();
    var billingModel = "marketplace";
    var displayName = "rhel-metering";
    var externalOrganization = "18078360";
    var resourceName = "addon-open-data-hub";
    var receive = true;
    var socketCount = 1;
    var tenantId = UUID.randomUUID().toString();
    var is3rdPartyMigrationFlag = false;

    QueryResultDataResultInner dataResult =
        new QueryResultDataResultInner()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", expectedSla)
            .putMetricItem("usage", expectedUsage)
            .putMetricItem("external_organization", externalOrganization)
            .putMetricItem("billing_marketplace", billingProvider)
            .putMetricItem("billing_marketplace_account", billingMarketplaceAccount)
            .putMetricItem("display_name", displayName)
            .putMetricItem("billing_marketplace_instance_id", billingMarketplaceInstanceId)
            .putMetricItem("billing_marketplace_model", billingModel)
            .putMetricItem("resource_name", resourceName)
            .putMetricItem("receive", String.valueOf(receive))
            .putMetricItem("socket_count", String.valueOf(socketCount))
            .putMetricItem("tenant_id", tenantId)
            .putMetricItem("conversions_success", String.valueOf(is3rdPartyMigrationFlag));
    ;

    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);

    var timeValueTuples = List.of(List.of(time1, val1));
    timeValueTuples.forEach(dataResult::addValuesItem);

    var data =
        new QueryResult()
            .status(StatusType.SUCCESS)
            .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));

    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusDays(1);

    String productTag = "rhods";
    String metricId = "Cores";
    String serviceType = "Rhods Cluster";

    whenCollectMetrics(externalOrganization, productTag, MetricId.fromString(metricId), start, end);

    var actual = results.received().stream().map(Message::getPayload).toList();

    assertEquals(
        List.of(
            MeteringEventFactory.createMetricEvent(
                externalOrganization,
                clusterId,
                expectedSla,
                expectedUsage,
                resourceName,
                PROMETHEUS,
                clock.dateFromUnix(time1).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time1),
                serviceType,
                billingProvider,
                billingMarketplaceAccount,
                MetricId.fromString(metricId),
                val1.doubleValue(),
                productTag,
                expectedSpanId,
                List.of(),
                displayName,
                is3rdPartyMigrationFlag)),
        actual);
  }

  @ParameterizedTest
  @MethodSource("productLabelsToProductIdList")
  void testExtractProductIdsFromProductLabel(String productLabel, List<String> expectedResult) {
    var actual = controller.extractProductIdsFromProductLabel(productLabel);

    assertEquals(actual, expectedResult);
  }

  static Stream<Arguments> productLabelsToProductIdList() {
    return Stream.of(
        Arguments.of(null, List.of()),
        Arguments.of("", List.of()),
        Arguments.of("asdf", List.of()),
        Arguments.of("asdf,1", List.of()),
        Arguments.of("1", List.of("1")),
        Arguments.of("1,2", List.of("1", "2")),
        Arguments.of("-1", List.of()),
        Arguments.of("-1,88", List.of()),
        Arguments.of("204,5,6", List.of("204", "5", "6")));
  }

  @Test
  void testRetryWhenOpenshiftServiceReturnsError() {
    QueryResult errorResponse = new QueryResult();
    errorResponse.setStatus(StatusType.ERROR);
    errorResponse.setError("FORCED!!");

    QueryResult good =
        buildOpenShiftClusterQueryResult(
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedDisplayName,
            List.of(List.of(new BigDecimal("12312.345"), new BigDecimal(24))));

    prometheusServer.stubQueryRange(errorResponse, errorResponse, good);

    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusDays(1);

    whenCollectMetrics("account", start, end);
    prometheusServer.verifyQueryRangeWasCalled(3);
  }

  @Test
  void testCollectMetricsShouldRetryWhenPrometheusReturnsEmptyBody() {
    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusDays(1);
    prometheusServer.stubQueryRangeWithEmptyBody();
    assertThrows(ExternalServiceException.class, () -> whenCollectMetrics(start, end));
  }

  @Test
  void testDatesAdjustedWhenReportingOpenShiftMetrics() {
    OffsetDateTime start = clock.now().withSecond(30).withMinute(22);
    OffsetDateTime end = start.plusHours(4);
    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedDisplayName,
            List.of(List.of(new BigDecimal("12312.345"), new BigDecimal(24))));
    prometheusServer.stubQueryRange(data);

    whenCollectMetrics(start, end);
    verifyQueryRange(clock.startOfHour(start), end);
  }

  private void whenCollectMetrics(OffsetDateTime start, OffsetDateTime end) {
    whenCollectMetrics(expectedOrgId, start, end);
  }

  private void whenCollectMetrics(String expectedOrgId, OffsetDateTime start, OffsetDateTime end) {
    controller.collectMetrics(
        expectedProductTag, MetricIdUtils.getCores(), expectedOrgId, start, end);
  }

  private void whenCollectMetrics(
      String expectedOrgId,
      String productTag,
      MetricId metricId,
      OffsetDateTime start,
      OffsetDateTime end) {
    controller.collectMetrics(productTag, metricId, expectedOrgId, start, end);
  }

  private void verifyQueryRange(OffsetDateTime start, OffsetDateTime end) {
    prometheusServer.verifyQueryRange(
        queries.expectedQuery(expectedProductTag, Map.of("orgId", expectedOrgId)),
        start.plusHours(1),
        end,
        metricProperties.step(),
        metricProperties.queryTimeout());
  }

  private QueryResult buildOpenShiftClusterQueryResult(
      String orgId,
      String clusterId,
      String sla,
      String usage,
      String billingProvider,
      String billingAccountId,
      String displayName,
      List<List<BigDecimal>> timeValueTuples) {
    QueryResultDataResultInner dataResult =
        new QueryResultDataResultInner()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", sla)
            .putMetricItem("usage", usage)
            .putMetricItem("external_organization", orgId)
            .putMetricItem("billing_marketplace", billingProvider)
            .putMetricItem("billing_marketplace_account", billingAccountId)
            .putMetricItem("display_name", displayName)
            .putMetricItem("conversions_success", "true")
            .putMetricItem("product", "204,317,69");

    // NOTE: A tuple is [unix_time,value]
    timeValueTuples.forEach(dataResult::addValuesItem);

    return new QueryResult()
        .status(StatusType.SUCCESS)
        .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));
  }
}
