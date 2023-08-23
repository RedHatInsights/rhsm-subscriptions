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

import java.time.OffsetDateTime;
import java.util.Map;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.registry.TagProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties =
        "rhsm-subscriptions.metering.prometheus.client.url=http://localhost:${WIREMOCK_PORT:8101}")
@ActiveProfiles({"openshift-metering-worker", "test"})
@ExtendWith(PrometheusQueryWiremockExtension.class)
class PrometheusServiceTest {

  @Autowired private PrometheusService service;

  @Autowired private QueryBuilder queryBuilder;

  @Autowired private TagProfile tagProfile;

  @Test
  void testRangeQueryApi(
      PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    QueryHelper queries = new QueryHelper(tagProfile, queryBuilder);

    String expectedQuery = queries.expectedQuery("OpenShift-metrics", Map.of("orgId", "o1"));
    QueryResult expectedResult = new QueryResult();

    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(2);
    int expectedTimeout = 1;
    int expectedStep = 3600;

    prometheusServer.stubQueryRange(
        expectedQuery, start, end, expectedStep, expectedTimeout, expectedResult);

    QueryResult result =
        service.runRangeQuery(expectedQuery, start, end, expectedStep, expectedTimeout);
    assertEquals(expectedResult, result);
  }

  @Test
  void testQueryApi(PrometheusQueryWiremockExtension.PrometheusQueryWiremock prometheusServer) {
    QueryHelper queries = new QueryHelper(tagProfile, queryBuilder);

    String expectedQuery = queries.expectedQuery("OpenShift-metrics", Map.of("orgId", "o1"));
    int expectedTimeout = 1;
    OffsetDateTime expectedTime = OffsetDateTime.now();
    QueryResult expectedResult = new QueryResult();

    prometheusServer.stubQuery(expectedQuery, expectedTimeout, expectedTime, expectedResult);

    QueryResult result = service.runQuery(expectedQuery, expectedTime, expectedTimeout);
    assertEquals(expectedResult, result);
  }
}
