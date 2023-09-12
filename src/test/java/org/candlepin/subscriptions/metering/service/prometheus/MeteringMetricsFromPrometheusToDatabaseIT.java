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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.candlepin.subscriptions.db.EventRecordRepository;
import org.candlepin.subscriptions.json.Measurement;
import org.candlepin.subscriptions.prometheus.model.*;
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
  private static final Measurement.Uom METRIC = Measurement.Uom.CORES;
  private static final String ORG_ID = "1111";

  @Autowired private PrometheusMeteringController controller;
  @Autowired private EventRecordRepository repository;

  @Test
  void testEndToEndScenario(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    givenMetricsInPrometheus(prometheusServer);

    // all insert
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabase();
    // all update
    prometheusServer.resetScenario();
    whenCollectMetrics();
    verifyAllEventsAreStoredInDatabase();
  }

  private void givenMetricsInPrometheus(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
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

  private void whenCollectMetrics() {
    OffsetDateTime start = OffsetDateTime.now().minusHours(5);
    OffsetDateTime end = start.plusHours(1);
    controller.collectMetrics(PRODUCT_TAG, METRIC, ORG_ID, start, end);
  }

  private void verifyAllEventsAreStoredInDatabase() {
    await()
        .atMost(TIMEOUT_TO_WAIT_FOR_METRICS)
        .untilAsserted(() -> assertEquals(NUM_METRICS_TO_SEND, repository.count()));
  }
}
