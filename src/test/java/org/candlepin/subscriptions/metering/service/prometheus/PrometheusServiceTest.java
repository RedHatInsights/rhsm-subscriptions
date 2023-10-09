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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.OffsetDateTime;
import java.util.Map;
import org.candlepin.subscriptions.metering.service.prometheus.model.QuerySummaryResult;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.candlepin.subscriptions.test.ExtendWithPrometheusWiremock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"openshift-metering-worker", "test"})
class PrometheusServiceTest implements ExtendWithPrometheusWiremock {

  @Autowired private PrometheusService service;

  @Autowired private QueryBuilder queryBuilder;

  @Test
  void testRangeQueryApi(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    QueryHelper queries = new QueryHelper(queryBuilder);

    String expectedQuery = queries.expectedQuery("OpenShift-metrics", Map.of("orgId", "o1"));
    QueryResult expectedResult = new QueryResult();
    expectedResult.status(StatusType.SUCCESS);

    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(2);
    int expectedTimeout = 1;
    int expectedStep = 3600;

    prometheusServer.stubQueryRange(
        expectedQuery, start, end, expectedStep, expectedTimeout, expectedResult);

    QuerySummaryResult result =
        service.runRangeQuery(expectedQuery, start, end, expectedStep, expectedTimeout, item -> {});
    assertEquals(expectedResult.getStatus(), result.getStatus());
    assertEquals(0, result.getNumOfResults());
  }

  @Test
  void testQueryApi(PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    QueryHelper queries = new QueryHelper(queryBuilder);

    String expectedQuery = queries.expectedQuery("OpenShift-metrics", Map.of("orgId", "o1"));
    int expectedTimeout = 1;
    OffsetDateTime expectedTime = OffsetDateTime.now();
    QueryResult expectedResult = new QueryResult();
    expectedResult.status(StatusType.ERROR);
    expectedResult.error(null);

    prometheusServer.stubQuery(expectedQuery, expectedTimeout, expectedTime, expectedResult);

    QuerySummaryResult result =
        service.runQuery(expectedQuery, expectedTime, expectedTimeout, item -> {});
    assertEquals(expectedResult.getStatus(), result.getStatus());
  }

  @Test
  void testRangeQueryApiWithSmallDataset(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    prometheusServer.stubQueryRangeWithFile("prometheus_small.json");

    QueryHelper queries = new QueryHelper(queryBuilder);
    String query = queries.expectedQuery("OpenShift-metrics", Map.of("orgId", "o1"));

    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(2);

    QuerySummaryResult result = service.runRangeQuery(query, start, end, 3600, 1, r -> {});
    assertNotNull(result);
    assertEquals(StatusType.SUCCESS, result.getStatus());
    assertEquals(ResultType.VECTOR, result.getResultType());
    assertEquals(5, result.getNumOfResults());
  }

  @Test
  void testRangeQueryApiWithEmptyResult(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    prometheusServer.stubQueryRangeWithFile("prometheus_empty_result.json");

    QueryHelper queries = new QueryHelper(queryBuilder);
    String query = queries.expectedQuery("OpenShift-metrics", Map.of("orgId", "o1"));

    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(2);

    QuerySummaryResult result =
        service.runRangeQuery(
            query,
            start,
            end,
            3600,
            1,
            r -> {
              fail("Should have nothing to operate against");
            });
    assertNotNull(result);
    assertEquals(StatusType.SUCCESS, result.getStatus());
    assertEquals(ResultType.MATRIX, result.getResultType());
    assertEquals(0, result.getNumOfResults());
  }
}
