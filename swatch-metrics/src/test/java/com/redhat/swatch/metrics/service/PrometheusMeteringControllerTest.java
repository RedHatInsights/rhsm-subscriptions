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

import static com.redhat.swatch.metrics.util.MeteringEventFactory.getEventType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.redhat.swatch.clients.prometheus.api.model.QueryResult;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultData;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultDataResultInner;
import com.redhat.swatch.clients.prometheus.api.model.ResultType;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.metrics.configuration.MetricProperties;
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
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.json.BaseEvent;
import org.candlepin.subscriptions.json.Event;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
  private final String expectedRole = "ocm";
  private final String expectedServiceType = "OpenShift Cluster";
  private final String expectedBillingProvider = "red hat";
  private final String expectedBillingAccountId = "mktp-account";
  private final MetricId expectedMetricId = MetricIdUtils.getCores();
  private final String expectedProductTag = "OpenShift-metrics";
  private final UUID expectedSpanId = UUID.randomUUID();
  @Inject PrometheusMeteringController controller;

  @Inject MetricProperties metricProperties;

  @Inject QueryBuilder queryBuilder;

  @InjectMock SpanGenerator spanGenerator;

  @Inject ApplicationClock clock;
  @InjectPrometheus PrometheusQueryWiremock prometheusServer;
  @Inject @Any InMemoryConnector connector;

  private InMemorySink<BaseEvent> results;
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
            List.of(List.of(new BigDecimal("12312.345"), new BigDecimal(24))));

    prometheusServer.stubQueryRange(errorResponse, errorResponse, good);

    OffsetDateTime start = OffsetDateTime.now();
    OffsetDateTime end = start.plusDays(1);

    whenCollectMetrics("account", start, end);
    prometheusServer.verifyQueryRangeWasCalled(3);
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
            List.of(List.of(new BigDecimal("12312.345"), new BigDecimal(24))));
    prometheusServer.stubQueryRange(data);

    whenCollectMetrics(start, end);
    verifyQueryRange(clock.startOfHour(start), end);
  }

  @Test
  @SuppressWarnings("indentation")
  void testCollectOpenShiftMetricsWillPersistEvents() {
    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);
    BigDecimal time2 = BigDecimal.valueOf(222222.222);
    BigDecimal val2 = BigDecimal.valueOf(120L);

    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            List.of(List.of(time1, val1), List.of(time2, val2)));
    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusDays(1);

    List<BaseEvent> expectedEvents =
        List.of(
            MeteringEventFactory.createMetricEvent(
                expectedOrgId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                PROMETHEUS,
                clock.dateFromUnix(time1).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time1),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedMetricId,
                val1.doubleValue(),
                expectedProductTag,
                expectedSpanId),
            MeteringEventFactory.createMetricEvent(
                expectedOrgId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                PROMETHEUS,
                clock.dateFromUnix(time2).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time2),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedMetricId,
                val2.doubleValue(),
                expectedProductTag,
                expectedSpanId),
            MeteringEventFactory.createCleanUpEvent(
                expectedOrgId,
                getEventType(expectedMetricId.toString(), expectedProductTag),
                PROMETHEUS,
                start,
                end,
                expectedSpanId));

    whenCollectMetrics(start, end);

    assertEquals(expectedEvents.size(), results.received().size());
    verifyQueryRange(start, end);
    assertTrue(
        results.received().stream().map(Message::getPayload).allMatch(expectedEvents::contains));
  }

  @Test
  void testVerifyExistingEventsAreUpdatedWhenReportedByPrometheusAndDeletedIfStale() {
    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);
    BigDecimal time2 = BigDecimal.valueOf(222222.222);
    BigDecimal val2 = BigDecimal.valueOf(120L);

    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            List.of(List.of(time1, val1), List.of(time2, val2)));
    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusDays(1);

    Event updatedEvent =
        MeteringEventFactory.createMetricEvent(
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedRole,
            PROMETHEUS,
            clock.dateFromUnix(time1).minusSeconds(metricProperties.step()),
            clock.dateFromUnix(time1),
            expectedServiceType,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedMetricId,
            val1.doubleValue(),
            expectedProductTag,
            expectedSpanId);

    List<BaseEvent> expectedEvents =
        List.of(
            updatedEvent,
            MeteringEventFactory.createMetricEvent(
                expectedOrgId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                PROMETHEUS,
                clock.dateFromUnix(time2).minusSeconds(metricProperties.step()),
                clock.dateFromUnix(time2),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedMetricId,
                val2.doubleValue(),
                expectedProductTag,
                expectedSpanId),
            MeteringEventFactory.createCleanUpEvent(
                expectedOrgId,
                getEventType(expectedMetricId.toString(), expectedProductTag),
                PROMETHEUS,
                start,
                end,
                expectedSpanId));

    whenCollectMetrics(start, end);
    assertEquals(expectedEvents.size(), results.received().size());
    verifyQueryRange(start, end);
    assertTrue(
        results.received().stream().map(Message::getPayload).allMatch(expectedEvents::contains));
  }

  @Test
  void testVerifyConflictingSlaCausesSavesFirstValue() {
    QueryResultDataResultInner standardResultItem =
        new QueryResultDataResultInner()
            .putMetricItem("_id", expectedClusterId)
            .putMetricItem("support", "Standard")
            .putMetricItem("usage", "Production")
            .putMetricItem("role", "osd")
            .putMetricItem("external_organization", expectedOrgId)
            .putMetricItem("billing_marketplace", "red hat")
            .putMetricItem("billing_marketplace_account", expectedBillingAccountId)
            .addValuesItem(List.of(BigDecimal.valueOf(1616787308L), BigDecimal.valueOf(4.0)));
    QueryResultDataResultInner premiumResultItem =
        new QueryResultDataResultInner()
            .putMetricItem("_id", expectedClusterId)
            .putMetricItem("support", "Standard")
            .putMetricItem("usage", "Production")
            .putMetricItem("role", "osd")
            .putMetricItem("external_organization", expectedOrgId)
            .putMetricItem("billing_marketplace", "red hat")
            .putMetricItem("billing_marketplace_account", expectedBillingAccountId)
            .addValuesItem(List.of(BigDecimal.valueOf(1616787308L), BigDecimal.valueOf(4.0)));
    QueryResultData queryResultData =
        new QueryResultData().addResultItem(standardResultItem).addResultItem(premiumResultItem);
    QueryResult data = new QueryResult().data(queryResultData).status(StatusType.SUCCESS);

    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = clock.endOfHour(start.plusDays(1));

    Event updatedEvent =
        MeteringEventFactory.createMetricEvent(
            expectedOrgId,
            expectedClusterId,
            "Standard",
            expectedUsage,
            expectedRole,
            PROMETHEUS,
            clock.dateFromUnix(1616787308L).minusSeconds(metricProperties.step()),
            clock.dateFromUnix(1616787308L),
            expectedServiceType,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedMetricId,
            4.0,
            expectedProductTag,
            expectedSpanId);

    List<BaseEvent> expectedEvents =
        List.of(
            updatedEvent,
            MeteringEventFactory.createCleanUpEvent(
                expectedOrgId,
                getEventType(expectedMetricId.toString(), expectedProductTag),
                PROMETHEUS,
                start,
                end,
                expectedSpanId));
    whenCollectMetrics(start, end);
    assertEquals(expectedEvents.size(), results.received().size());

    verifyQueryRange(start, end);
    assertTrue(
        results.received().stream().map(Message::getPayload).allMatch(expectedEvents::contains));
  }

  private void whenCollectMetrics(OffsetDateTime start, OffsetDateTime end) {
    whenCollectMetrics(expectedOrgId, start, end);
  }

  private void whenCollectMetrics(String expectedOrgId, OffsetDateTime start, OffsetDateTime end) {
    controller.collectMetrics(
        expectedProductTag, MetricIdUtils.getCores(), expectedOrgId, start, end);
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
      List<List<BigDecimal>> timeValueTuples) {
    QueryResultDataResultInner dataResult =
        new QueryResultDataResultInner()
            .putMetricItem("_id", clusterId)
            .putMetricItem("support", sla)
            .putMetricItem("usage", usage)
            .putMetricItem("external_organization", orgId)
            .putMetricItem("billing_marketplace", billingProvider)
            .putMetricItem("billing_marketplace_account", billingAccountId);

    // NOTE: A tuple is [unix_time,value]
    timeValueTuples.forEach(dataResult::addValuesItem);

    return new QueryResult()
        .status(StatusType.SUCCESS)
        .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));
  }
}
