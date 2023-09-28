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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.candlepin.subscriptions.metering.MeteringEventFactory.getEventType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.registry.MetricId;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.clock.ApplicationClock;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.json.Event;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.test.ExtendWithEmbeddedKafka;
import org.candlepin.subscriptions.test.ExtendWithPrometheusWiremock;
import org.candlepin.subscriptions.test.ExtendWithSwatchDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "EVENT_SOURCE=" + MeteringMetricsFromPrometheusToDatabaseIT.PROMETHEUS)
@ActiveProfiles({"openshift-metering-worker", "worker", "test-inventory"})
class MeteringMetricsFromPrometheusToDatabaseIT
    implements ExtendWithPrometheusWiremock, ExtendWithEmbeddedKafka, ExtendWithSwatchDatabase {

  static final String PROMETHEUS = "prometheus";

  private static final int NUM_METRICS_TO_SEND = 5;
  private static final Duration TIMEOUT_TO_WAIT_FOR_METRICS = Duration.ofSeconds(5);
  private static final String PRODUCT_TAG = "rosa";
  private static final MetricId METRIC = MetricIdUtils.getCores();
  private static final String ORG_ID = "1111";

  @Autowired private ApplicationClock clock;
  @Autowired private PrometheusMeteringController controller;
  @Autowired private EventRecordRepository repository;
  @Autowired private MetricProperties metricProperties;

  private OffsetDateTime start;
  private OffsetDateTime end;
  private EventRecord existingEventSameTimeWindow;
  private EventRecord existingEventBeforeTimeWindow;

  @BeforeEach
  public void setup() {
    this.start = clock.now().minusHours(5);
    this.end = start.plusHours(1);
    this.existingEventSameTimeWindow = null;
    this.existingEventBeforeTimeWindow = null;
    this.repository.deleteAll();
  }

  @Test
  void testDeletionOfExistingEventsWithinSameTimeWindow(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    givenEmptyMetricsInPrometheus(prometheusServer);
    givenExistingEventWithinSameTimeWindow();
    givenExistingEventBeforeTimeWindow();

    whenCollectMetrics();

    verifyExistingEventWithinSameTimeWindowWasDeleted();
    verifyExistingEventBeforeTimeWindowWasNotDeleted();
  }

  @Test
  void testCreateNewMetricsAndUpdateExistingEvents(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    OffsetDateTime timestampForEvents = OffsetDateTime.now();

    givenMetricsInPrometheusWithUsage(
        prometheusServer, timestampForEvents, Event.Usage.DEVELOPMENT_TEST);

    // all insert
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabaseWithUsage(Event.Usage.DEVELOPMENT_TEST);
    // store existing events to assert that event ID didn't change.
    List<UUID> snapshotOfExistingEvents = getEventIDsFromRepository();

    // all update
    givenMetricsInPrometheusWithUsage(prometheusServer, timestampForEvents, Event.Usage.PRODUCTION);
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabaseWithUsage(Event.Usage.PRODUCTION);
    verifyAllEventsUseEventSourcePrometheus();
    assertThat(snapshotOfExistingEvents)
        .containsExactlyInAnyOrderElementsOf(getEventIDsFromRepository());
  }

  private void givenExistingEventWithinSameTimeWindow() {
    existingEventSameTimeWindow = givenExistingEventWithTimestamp(start.plusMinutes(30));
  }

  private void givenExistingEventBeforeTimeWindow() {
    existingEventBeforeTimeWindow = givenExistingEventWithTimestamp(start.minusHours(5));
  }

  private EventRecord givenExistingEventWithTimestamp(OffsetDateTime timestamp) {
    EventRecord event = new EventRecord();
    event.setEventId(UUID.randomUUID());
    event.setEventType(getEventType(METRIC.toString(), PRODUCT_TAG));
    event.setOrgId(ORG_ID);
    event.setEventSource(metricProperties.getEventSource());
    event.setTimestamp(timestamp);
    event.setInstanceId("test");
    return repository.save(event);
  }

  private void givenMetricsInPrometheusWithUsage(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer,
      OffsetDateTime timestamp,
      Event.Usage usage) {
    prometheusServer.resetScenario();
    QueryResult expectedResult = new QueryResult();
    expectedResult.status(StatusType.SUCCESS);
    QueryResultData expectedData = new QueryResultData();
    expectedData.resultType(ResultType.MATRIX);
    List<QueryResultDataResultInner> metricsList = new ArrayList<>();
    for (int i = 0; i < NUM_METRICS_TO_SEND; i++) {
      QueryResultDataResultInner data = new QueryResultDataResultInner();
      data.values(
          List.of(
              List.of(
                  new BigDecimal(clock.now().plusSeconds(100).toEpochSecond()),
                  new BigDecimal(1))));

      Map<String, String> labels = new HashMap<>();
      labels.put("_id", "id" + i);
      labels.put("product", "ocp");
      labels.put("support", "Premium");
      labels.put("usage", usage.value());
      data.metric(labels);
      metricsList.add(data);
    }
    expectedData.result(metricsList);
    expectedResult.data(expectedData);

    prometheusServer.stubQueryRange(expectedResult);
  }

  private void givenEmptyMetricsInPrometheus(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    prometheusServer.resetScenario();
    QueryResult expectedResult = new QueryResult();
    expectedResult.status(StatusType.SUCCESS);
    QueryResultData expectedData = new QueryResultData();
    expectedData.resultType(ResultType.MATRIX);
    expectedData.result(List.of());
    expectedResult.data(expectedData);

    prometheusServer.stubQueryRange(expectedResult);
  }

  private void whenCollectMetrics() {
    controller.collectMetrics(PRODUCT_TAG, METRIC, ORG_ID, start, end);
  }

  private void verifyAllEventsAreStoredInDatabaseWithUsage(Event.Usage expectedUsage) {
    await()
        .atMost(TIMEOUT_TO_WAIT_FOR_METRICS)
        .untilAsserted(
            () -> {
              assertEquals(NUM_METRICS_TO_SEND, repository.count());
              assertTrue(repository.findAll().stream().allMatch(e -> e.getRecordDate() != null));
              assertTrue(
                  repository.findAll().stream()
                      .allMatch(e -> expectedUsage == e.getEvent().getUsage()));
            });
  }

  private void verifyAllEventsUseEventSourcePrometheus() {
    repository
        .findAll()
        .forEach(
            event -> {
              assertEquals(PROMETHEUS, event.getEventSource());
            });
  }

  private void verifyExistingEventWithinSameTimeWindowWasDeleted() {
    await()
        .atMost(TIMEOUT_TO_WAIT_FOR_METRICS)
        .untilAsserted(
            () ->
                assertFalse(repository.existsById(existingEventSameTimeWindow.getEventRecordId())));
  }

  private void verifyExistingEventBeforeTimeWindowWasNotDeleted() {
    assertTrue(repository.existsById(existingEventBeforeTimeWindow.getEventRecordId()));
  }

  private List<UUID> getEventIDsFromRepository() {
    return repository.findAll().stream().map(EventRecord::getEventId).toList();
  }
}
