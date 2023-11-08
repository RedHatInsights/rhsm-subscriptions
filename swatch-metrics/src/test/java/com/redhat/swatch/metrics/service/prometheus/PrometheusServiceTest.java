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
package com.redhat.swatch.metrics.service.prometheus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.redhat.swatch.clients.prometheus.api.model.QueryResult;
import com.redhat.swatch.clients.prometheus.api.model.ResultType;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.metrics.resources.InjectPrometheus;
import com.redhat.swatch.metrics.resources.PrometheusQueryWiremock;
import com.redhat.swatch.metrics.resources.PrometheusResource;
import com.redhat.swatch.metrics.service.prometheus.model.QuerySummaryResult;
import com.redhat.swatch.metrics.service.promql.QueryBuilder;
import com.redhat.swatch.metrics.utils.QueryHelper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = PrometheusResource.class, restrictToAnnotatedClass = true)
class PrometheusServiceTest {
  @Inject PrometheusService service;

  @Inject QueryBuilder queryBuilder;
  @InjectPrometheus PrometheusQueryWiremock prometheusServer;

  @Test
  void testRangeQueryApi() {
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
  void testQueryApi() {
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
  void testRangeQueryApiWithSmallDataset() {
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
  void testRangeQueryApiWithEmptyResult() {
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
