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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.candlepin.subscriptions.db.AccountConfigRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.OrgConfigRepository;
import org.candlepin.subscriptions.db.model.config.OptInType;
import org.candlepin.subscriptions.event.EventController;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.MeteringEventFactory;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.registry.BillingWindow;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.security.OptInController;
import org.candlepin.subscriptions.test.TestClockConfiguration;
import org.candlepin.subscriptions.util.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties =
        "rhsm-subscriptions.metering.prometheus.client.url=http://localhost:${WIREMOCK_PORT:8101}")
@ActiveProfiles({"openshift-metering-worker", "test"})
@Import(TestClockConfiguration.class)
@ExtendWith(PrometheusQueryWiremockExtension.class)
class PrometheusMeteringControllerTest {

  @MockBean private EventController eventController;

  @MockBean AccountConfigRepository accountConfigRepository;

  @MockBean OrgConfigRepository orgConfigRepository;

  @Autowired private PrometheusService service;

  @Autowired private MetricProperties metricProperties;

  @Autowired private QueryBuilder queryBuilder;

  @MockBean private OptInController optInController;

  @Autowired
  @Qualifier("openshiftMetricRetryTemplate")
  RetryTemplate openshiftRetry;

  @Autowired private ApplicationClock clock;

  private final String expectedAccount = "my-test-account";
  private final String expectedOrgId = "my-test-org";
  private final String expectedMetricId = "CORES";
  private final String expectedClusterId = "C1";
  private final String expectedSla = "Premium";
  private final String expectedUsage = "Production";
  private final String expectedRole = "ocm";
  private final String expectedServiceType = "OpenShift Cluster";
  private final String expectedBillingProvider = "red hat";
  private final String expectedBillingAccountId = "mktp-account";
  private final Uom expectedUom = Uom.CORES;
  private final String expectedProductTag = "OpenShift-metrics";

  private PrometheusMeteringController controller;
  private QueryHelper queries;
  private TagMetric tagMetric;

  @BeforeEach
  void setupTest() {
    openshiftRetry.setBackOffPolicy(new NoBackOffPolicy());
    controller =
        new PrometheusMeteringController(
            clock,
            metricProperties,
            service,
            queryBuilder,
            eventController,
            openshiftRetry,
            optInController);

    queries = new QueryHelper(queryBuilder);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("product", "ocp");
    queryParams.put("prometheusMetric", "cluster:usage:workload:capacity_physical_cpu_hours");
    queryParams.put("prometheusMetadataMetric", "ocm_subscription");
    tagMetric =
        TagMetric.builder()
            .tag("OpenShift-metrics")
            .metricId(expectedMetricId)
            .rhmMetricId(expectedMetricId)
            .awsDimension(null)
            .uom(Measurement.Uom.CORES)
            .billingFactor(1.0)
            .billingWindow(BillingWindow.MONTHLY)
            .queryKey("default")
            .accountQueryKey("default")
            .queryParams(queryParams)
            .build();
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

    controller.collectMetrics("OpenShift-metrics", Uom.CORES, "account", start, end);
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

    controller.collectMetrics("OpenShift-metrics", Uom.CORES, expectedOrgId, start, end);
    prometheusServer.verifyQueryRange(
        queries.expectedQuery("OpenShift-metrics", Map.of("orgId", expectedOrgId)),
        clock.startOfHour(start).plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout());
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

    controller.collectMetrics("OpenShift-metrics", Uom.CORES, expectedOrgId, start, end);
    prometheusServer.verifyQueryRange(
        queries.expectedQuery("OpenShift-metrics", Map.of("orgId", expectedOrgId)),
        start.plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout());
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

    List<Event> expectedEvents =
        List.of(
            MeteringEventFactory.createMetricEvent(
                expectedAccount,
                expectedOrgId,
                expectedMetricId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                clock.dateFromUnix(time1).minusSeconds(metricProperties.getStep()),
                clock.dateFromUnix(time1),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedUom,
                val1.doubleValue(),
                expectedProductTag),
            MeteringEventFactory.createMetricEvent(
                expectedAccount,
                expectedOrgId,
                expectedMetricId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                clock.dateFromUnix(time2).minusSeconds(metricProperties.getStep()),
                clock.dateFromUnix(time2),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedUom,
                val2.doubleValue(),
                expectedProductTag));

    controller.collectMetrics("OpenShift-metrics", Uom.CORES, expectedOrgId, start, end);

    ArgumentCaptor<Collection> saveCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(eventController).saveAll(saveCaptor.capture());

    prometheusServer.verifyQueryRange(
        queries.expectedQuery("OpenShift-metrics", Map.of("orgId", expectedOrgId)),
        start.plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout());
    verify(eventController).saveAll(any());

    // Attempted to verify the eventController.saveAll(events) but
    // couldn't find a way to get mockito to match on the collection
    // of HashMap.Value. Using a capture works just as well, but is a less convenient.
    assertEquals(expectedEvents.size(), saveCaptor.getValue().size());
    assertTrue(saveCaptor.getValue().containsAll(expectedEvents));
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
            expectedMetricId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedRole,
            clock.dateFromUnix(time1).minusSeconds(metricProperties.getStep()),
            clock.dateFromUnix(time1),
            expectedServiceType,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedUom,
            val1.doubleValue(),
            expectedProductTag);

    List<Event> expectedEvents =
        List.of(
            updatedEvent,
            MeteringEventFactory.createMetricEvent(
                expectedAccount,
                expectedOrgId,
                expectedMetricId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                clock.dateFromUnix(time2).minusSeconds(metricProperties.getStep()),
                clock.dateFromUnix(time2),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedUom,
                val2.doubleValue(),
                expectedProductTag));

    Event purgedEvent =
        MeteringEventFactory.createMetricEvent(
            expectedAccount,
            expectedOrgId,
            expectedMetricId,
            "CLUSTER_NO_LONGER_EXISTS",
            expectedSla,
            expectedUsage,
            expectedRole,
            clock.dateFromUnix(time1).minusSeconds(metricProperties.getStep()),
            clock.dateFromUnix(time1),
            expectedServiceType,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedUom,
            val1.doubleValue(),
            expectedProductTag);

    List<Event> existingEvents =
        List.of(
            // This event will get updated by the incoming data from prometheus.
            MeteringEventFactory.createMetricEvent(
                expectedAccount,
                expectedOrgId,
                expectedMetricId,
                expectedClusterId,
                expectedSla,
                expectedUsage,
                expectedRole,
                updatedEvent.getTimestamp(),
                updatedEvent.getExpiration().get(),
                expectedServiceType,
                expectedBillingProvider,
                expectedBillingAccountId,
                expectedUom,
                144.4,
                expectedProductTag),
            // This event should get purged because prometheus did not report this cluster.
            purgedEvent);
    when(eventController.mapEventsInTimeRange(
            expectedOrgId,
            MeteringEventFactory.EVENT_SOURCE,
            MeteringEventFactory.getEventType(tagMetric.getMetricId(), tagMetric.getTag()),
            start,
            end))
        .thenReturn(
            existingEvents.stream()
                .collect(Collectors.toMap(EventKey::fromEvent, Function.identity())));

    controller.collectMetrics("OpenShift-metrics", Uom.CORES, expectedOrgId, start, end);

    ArgumentCaptor<Collection> saveCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(eventController).saveAll(saveCaptor.capture());

    ArgumentCaptor<Collection> purgeCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(eventController).deleteEvents(purgeCaptor.capture());

    prometheusServer.verifyQueryRange(
        queries.expectedQuery("OpenShift-metrics", Map.of("orgId", expectedOrgId)),
        start.plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout());

    // Attempted to verify the eventController calls below, but
    // couldn't find a way to get mockito to match on collection of HashMap.Value.
    // Using a capture works just as well, but is a less convenient.
    assertEquals(expectedEvents.size(), saveCaptor.getValue().size());
    assertTrue(saveCaptor.getValue().containsAll(expectedEvents));

    assertEquals(1, purgeCaptor.getValue().size());
    assertTrue(purgeCaptor.getValue().contains(purgedEvent));
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
            expectedMetricId,
            expectedClusterId,
            "Standard",
            expectedUsage,
            expectedRole,
            clock.dateFromUnix(1616787308L).minusSeconds(metricProperties.getStep()),
            clock.dateFromUnix(1616787308L),
            expectedServiceType,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedUom,
            4.0,
            expectedProductTag);

    var eventId = UUID.randomUUID();
    updatedEvent.setEventId(eventId);

    List<Event> expectedEvents = List.of(updatedEvent);

    var existingEvent =
        MeteringEventFactory.createMetricEvent(
            expectedAccount,
            expectedOrgId,
            expectedMetricId,
            expectedClusterId,
            expectedSla,
            expectedUsage,
            expectedRole,
            updatedEvent.getTimestamp(),
            updatedEvent.getExpiration().get(),
            expectedServiceType,
            expectedBillingProvider,
            expectedBillingAccountId,
            expectedUom,
            144.4,
            expectedProductTag);
    existingEvent.setEventId(eventId);
    List<Event> existingEvents =
        List.of(
            // This event will get updated by the incoming data from prometheus.
            existingEvent);
    when(eventController.mapEventsInTimeRange(
            expectedOrgId,
            MeteringEventFactory.EVENT_SOURCE,
            MeteringEventFactory.getEventType(tagMetric.getMetricId(), tagMetric.getTag()),
            start,
            end))
        .thenReturn(
            existingEvents.stream()
                .collect(Collectors.toMap(EventKey::fromEvent, Function.identity())));

    controller.collectMetrics("OpenShift-metrics", Uom.CORES, expectedOrgId, start, end);

    var saveCaptor = ArgumentCaptor.forClass(Collection.class);
    verify(eventController).saveAll(saveCaptor.capture());

    prometheusServer.verifyQueryRange(
        queries.expectedQuery("OpenShift-metrics", Map.of("orgId", expectedOrgId)),
        start.plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout());

    // Attempted to verify the eventController calls below, but
    // couldn't find a way to get mockito to match on collection of HashMap.Value.
    // Using a capture works just as well, but is a less convenient.
    assertEquals(expectedEvents.size(), saveCaptor.getValue().size());
    assertTrue(saveCaptor.getValue().containsAll(expectedEvents));
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
    timeValueTuples.forEach(tuple -> dataResult.addValuesItem(tuple));

    return new QueryResult()
        .status(StatusType.SUCCESS)
        .data(new QueryResultData().resultType(ResultType.MATRIX).addResultItem(dataResult));
  }
}
