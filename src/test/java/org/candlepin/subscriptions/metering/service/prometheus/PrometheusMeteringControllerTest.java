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
package org.candlepin.subscriptions.metering.service.prometheus;

import static org.candlepin.subscriptions.metering.MeteringEventFactory.getEventType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redhat.swatch.configuration.registry.MetricId;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.json.BaseEvent;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.candlepin.subscriptions.util.MetricIdUtils;
import org.candlepin.subscriptions.util.SpanGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      PrometheusQueryWiremockExtension.PROM_URL,
      "EVENT_SOURCE=" + PrometheusMeteringControllerTest.PROMETHEUS
    })
@ActiveProfiles({"openshift-metering-worker", "test"})
@Import(TestClockConfiguration.class)
@ExtendWith(PrometheusQueryWiremockExtension.class)
class PrometheusMeteringControllerTest {

  static final String PROMETHEUS = "prometheus";

  @MockBean private PrometheusEventsProducer eventsProducer;

  @MockBean AccountConfigRepository accountConfigRepository;

  @MockBean OrgConfigRepository orgConfigRepository;

  @Autowired private PrometheusService service;

  @Autowired private MetricProperties metricProperties;

  @Autowired private QueryBuilder queryBuilder;

  @Captor private ArgumentCaptor<BaseEvent> eventsSent;

  @MockBean private OptInController optInController;

  @MockBean private SpanGenerator spanGenerator;

  @Autowired
  @Qualifier("openshiftMetricRetryTemplate")
  RetryTemplate openshiftRetry;

  @Autowired private ApplicationClock clock;

  private final String expectedAccount = "my-test-account";
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
  private PrometheusMeteringController controller;
  private QueryHelper queries;

  @BeforeEach
  void setupTest() {
    openshiftRetry.setBackOffPolicy(new NoBackOffPolicy());
    controller =
        new PrometheusMeteringController(
            clock,
            metricProperties,
            service,
            queryBuilder,
            eventsProducer,
            openshiftRetry,
            optInController,
            spanGenerator);

    queries = new QueryHelper(queryBuilder);

    when(spanGenerator.generate()).thenReturn(expectedSpanId);
  }

  @Test
  void testRetryWhenOpenshiftServiceReturnsError(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    QueryResult errorResponse = new QueryResult();
    errorResponse.setStatus(StatusType.ERROR);
    errorResponse.setError("FORCED!!");

    QueryResult good =
        buildOpenShiftClusterQueryResult(
            expectedAccount,
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
  void datesAdjustedWhenReportingOpenShiftMetrics(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    OffsetDateTime start = clock.now().withSecond(30).withMinute(22);
    OffsetDateTime end = start.plusHours(4);
    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedAccount,
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            List.of(List.of(new BigDecimal("12312.345"), new BigDecimal(24))));
    prometheusServer.stubQueryRange(data);

    whenCollectMetrics(start, end);
    verifyQueryRange(prometheusServer, clock.startOfHour(start), end);
  }

  @Test
  void orgIdGetsOptedInWhenReportingMetrics(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = start.plusHours(4);
    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedAccount,
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedBillingProvider,
            expectedBillingAccountId,
            List.of(List.of(new BigDecimal("12312.345"), new BigDecimal(24))));
    prometheusServer.stubQueryRange(data);

    whenCollectMetrics(start, end);
    verifyQueryRange(prometheusServer, start, end);
    verify(optInController).optInByOrgId(expectedOrgId, OptInType.PROMETHEUS);
  }

  @Test
  @SuppressWarnings("indentation")
  void collectOpenShiftMetricsWillPersistEvents(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);
    BigDecimal time2 = BigDecimal.valueOf(222222.222);
    BigDecimal val2 = BigDecimal.valueOf(120L);

    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedAccount,
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
                expectedAccount,
                expectedOrgId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                PROMETHEUS,
                clock.dateFromUnix(time1).minusSeconds(metricProperties.getStep()),
                clock.dateFromUnix(time1),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedMetricId,
                val1.doubleValue(),
                expectedProductTag,
                expectedSpanId),
            MeteringEventFactory.createMetricEvent(
                expectedAccount,
                expectedOrgId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                PROMETHEUS,
                clock.dateFromUnix(time2).minusSeconds(metricProperties.getStep()),
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

    verify(eventsProducer, times(expectedEvents.size())).produce(eventsSent.capture());
    verifyQueryRange(prometheusServer, start, end);

    assertEquals(expectedEvents.size(), eventsSent.getAllValues().size());
    assertTrue(eventsSent.getAllValues().containsAll(expectedEvents));
  }

  @Test
  void verifyExistingEventsAreUpdatedWhenReportedByPrometheusAndDeletedIfStale(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    BigDecimal time1 = BigDecimal.valueOf(123456.234);
    BigDecimal val1 = BigDecimal.valueOf(100L);
    BigDecimal time2 = BigDecimal.valueOf(222222.222);
    BigDecimal val2 = BigDecimal.valueOf(120L);

    QueryResult data =
        buildOpenShiftClusterQueryResult(
            expectedAccount,
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
            expectedAccount,
            expectedOrgId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedRole,
            PROMETHEUS,
            clock.dateFromUnix(time1).minusSeconds(metricProperties.getStep()),
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
                expectedAccount,
                expectedOrgId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                PROMETHEUS,
                clock.dateFromUnix(time2).minusSeconds(metricProperties.getStep()),
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
    verify(eventsProducer, times(expectedEvents.size())).produce(eventsSent.capture());
    verifyQueryRange(prometheusServer, start, end);
    assertEquals(expectedEvents.size(), eventsSent.getAllValues().size());
    assertTrue(eventsSent.getAllValues().containsAll(expectedEvents));
  }

  @Test
  void verifyConflictingSlaCausesSavesFirstValue(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    QueryResultDataResultInner standardResultItem =
        new QueryResultDataResultInner()
            .putMetricItem("_id", expectedClusterId)
            .putMetricItem("support", "Standard")
            .putMetricItem("usage", "Production")
            .putMetricItem("role", "osd")
            .putMetricItem("ebs_account", expectedAccount)
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
            .putMetricItem("ebs_account", expectedAccount)
            .putMetricItem("external_organization", expectedOrgId)
            .putMetricItem("billing_marketplace", "red hat")
            .putMetricItem("billing_marketplace_account", expectedBillingAccountId)
            .addValuesItem(List.of(BigDecimal.valueOf(1616787308L), BigDecimal.valueOf(4.0)));
    QueryResultData queryResultData =
        new QueryResultData().addResultItem(standardResultItem).addResultItem(premiumResultItem);
    QueryResult data = new QueryResult().data(queryResultData);

    prometheusServer.stubQueryRange(data);

    OffsetDateTime start = clock.startOfCurrentHour();
    OffsetDateTime end = clock.endOfHour(start.plusDays(1));

    Event updatedEvent =
        MeteringEventFactory.createMetricEvent(
            expectedAccount,
            expectedOrgId,
            expectedClusterId,
            "Standard",
            expectedUsage,
            expectedRole,
            PROMETHEUS,
            clock.dateFromUnix(1616787308L).minusSeconds(metricProperties.getStep()),
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
    verify(eventsProducer, times(expectedEvents.size())).produce(eventsSent.capture());

    verifyQueryRange(prometheusServer, start, end);
    assertEquals(expectedEvents.size(), eventsSent.getAllValues().size());
    assertTrue(eventsSent.getAllValues().containsAll(expectedEvents));
  }

  private void whenCollectMetrics(OffsetDateTime start, OffsetDateTime end) {
    whenCollectMetrics(expectedOrgId, start, end);
  }

  private void whenCollectMetrics(String expectedOrgId, OffsetDateTime start, OffsetDateTime end) {
    controller.collectMetrics(
        expectedProductTag, MetricIdUtils.getCores(), expectedOrgId, start, end);
  }

  private void verifyQueryRange(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer,
      OffsetDateTime start,
      OffsetDateTime end) {
    prometheusServer.verifyQueryRange(
        queries.expectedQuery(expectedProductTag, Map.of("orgId", expectedOrgId)),
        start.plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout());
  }

  private QueryResult buildOpenShiftClusterQueryResult(
      String account,
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
            .putMetricItem("ebs_account", account)
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
