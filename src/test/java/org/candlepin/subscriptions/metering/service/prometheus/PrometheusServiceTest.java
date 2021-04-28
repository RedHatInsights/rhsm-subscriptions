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
import static org.mockito.Mockito.when;

import com.google.common.net.UrlEscapers;
import java.time.OffsetDateTime;
import org.candlepin.subscriptions.prometheus.api.ApiProvider;
import org.candlepin.subscriptions.prometheus.api.StubApiProvider;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.resources.QueryApi;
import org.candlepin.subscriptions.prometheus.resources.QueryRangeApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"openshift-metering-worker", "test"})
class PrometheusServiceTest {

  @MockBean private QueryApi queryApi;

  @MockBean private QueryRangeApi rangeApi;

  @MockBean private PrometheusAccountSource accountSource;

  @Autowired private PrometheusMetricsProperties props;

  @Test
  void testRangeQueryApi() throws Exception {

    String expectedQuery =
        UrlEscapers.urlFragmentEscaper().escape(props.getOpenshift().getMetricPromQL());
    QueryResult expectedResult = new QueryResult();

    OffsetDateTime end = OffsetDateTime.now();
    OffsetDateTime start = end.minusDays(2);
    String step = "3600";

    when(rangeApi.queryRange(expectedQuery, start.toEpochSecond(), end.toEpochSecond(), step, 1))
        .thenReturn(expectedResult);

    ApiProvider provider = new StubApiProvider(queryApi, rangeApi);
    PrometheusService service = new PrometheusService(provider);

    QueryResult result =
        service.runRangeQuery(props.getOpenshift().getMetricPromQL(), start, end, 3600, 1);
    assertEquals(expectedResult, result);
  }

  @Test
  void testQueryApi() throws Exception {
    String expectedQuery =
        UrlEscapers.urlFragmentEscaper().escape(props.getOpenshift().getMetricPromQL());
    QueryResult expectedResult = new QueryResult();

    OffsetDateTime time = OffsetDateTime.now();
    when(queryApi.query(expectedQuery, time, 1)).thenReturn(expectedResult);

    ApiProvider provider = new StubApiProvider(queryApi, rangeApi);
    PrometheusService service = new PrometheusService(provider);

    QueryResult result = service.runQuery(props.getOpenshift().getMetricPromQL(), time, 1);
    assertEquals(expectedResult, result);
  }
}
