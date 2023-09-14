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

import static org.awaitility.Awaitility.await;
import static org.candlepin.subscriptions.metering.MeteringEventFactory.EVENT_SOURCE;
import static org.candlepin.subscriptions.metering.MeteringEventFactory.getEventType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.swatch.configuration.registry.MetricId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.db.model.EventKey;
import org.candlepin.subscriptions.db.model.EventRecord;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.util.MetricIdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      PrometheusQueryWiremockExtension.PROM_URL,
      "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
      // In tests, messages may be sent before the listener has been assigned the topic
      // so we ensure that when the listener comes online it starts from first message.
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@ActiveProfiles({"openshift-metering-worker", "worker", "test"})
@ExtendWith(PrometheusQueryWiremockExtension.class)
@EmbeddedKafka(
    partitions = 1,
    topics = {"${rhsm-subscriptions.service-instance-ingress.incoming.topic}"})
class MeteringMetricsFromPrometheusToDatabaseIT {

  private static final int NUM_METRICS_TO_SEND = 5;
  private static final Duration TIMEOUT_TO_WAIT_FOR_METRICS = Duration.ofSeconds(5);
  private static final String PRODUCT_TAG = "rosa";
  private static final MetricId METRIC = MetricIdUtils.getCores();
  private static final String ORG_ID = "1111";

  @Autowired private PrometheusMeteringController controller;
  @Autowired private EventRecordRepository repository;

  private OffsetDateTime start;
  private OffsetDateTime end;
  private EventRecord existingEventSameTimeWindow;
  private EventRecord existingEventBeforeTimeWindow;

  @BeforeEach
  public void setup() {
    this.start = OffsetDateTime.now().minusHours(5);
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
    givenMetricsInPrometheus(prometheusServer);

    // all insert
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabase();
    // store existing events to assert that event ID didn't change.
    List<UUID> snapshotOfExistingEvents = getEventIDsFromRepository();

    // all update
    prometheusServer.resetScenario();
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabase();
    assertEquals(snapshotOfExistingEvents, getEventIDsFromRepository());
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
    event.setEventSource(EVENT_SOURCE);
    event.setTimestamp(timestamp);
    event.setInstanceId("test");
    return repository.save(event);
  }

  private void givenMetricsInPrometheus(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
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
                  new BigDecimal(OffsetDateTime.now().plusSeconds(100).toEpochSecond()),
                  new BigDecimal(1))));
      Map<String, String> labels = new HashMap<>();
      labels.put("_id", "id" + i);
      labels.put("product", "ocp");
      labels.put("support", "Premium");
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

  private void verifyAllEventsAreStoredInDatabase() {
    await()
        .atMost(TIMEOUT_TO_WAIT_FOR_METRICS)
        .untilAsserted(() -> assertEquals(NUM_METRICS_TO_SEND, repository.count()));
  }

  private void verifyExistingEventWithinSameTimeWindowWasDeleted() {
    await()
        .atMost(TIMEOUT_TO_WAIT_FOR_METRICS)
        .untilAsserted(
            () -> assertFalse(repository.existsById(toEventKey(existingEventSameTimeWindow))));
  }

  private void verifyExistingEventBeforeTimeWindowWasNotDeleted() {
    assertTrue(repository.existsById(toEventKey(existingEventBeforeTimeWindow)));
  }

  private List<UUID> getEventIDsFromRepository() {
    return repository.findAll().stream().map(EventRecord::getEventId).toList();
  }

  private static EventKey toEventKey(EventRecord eventRecord) {
    return new EventKey(
        eventRecord.getOrgId(),
        eventRecord.getEventSource(),
        eventRecord.getEventType(),
        eventRecord.getInstanceId(),
        eventRecord.getTimestamp());
  }
}
