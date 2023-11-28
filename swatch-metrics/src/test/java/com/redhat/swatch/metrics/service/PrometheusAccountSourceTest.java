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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.redhat.swatch.clients.prometheus.api.model.QueryResult;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultData;
import com.redhat.swatch.clients.prometheus.api.model.QueryResultDataResultInner;
import com.redhat.swatch.clients.prometheus.api.model.ResultType;
import com.redhat.swatch.clients.prometheus.api.model.StatusType;
import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import com.redhat.swatch.metrics.configuration.MetricProperties;
import com.redhat.swatch.metrics.service.prometheus.PrometheusService;
import com.redhat.swatch.metrics.service.prometheus.model.QuerySummaryResult;
import com.redhat.swatch.metrics.service.promql.QueryBuilder;
import com.redhat.swatch.metrics.service.promql.QueryDescriptor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PrometheusAccountSourceTest {
  private final String TEST_PROD_TAG = "OpenShift-dedicated-metrics";

  @Inject PrometheusAccountSource accountSource;
  @Inject MetricProperties metricProperties;
  @Inject QueryBuilder queryBuilder;
  @InjectMock PrometheusService service;

  Metric tag;

  @BeforeEach
  void setupTest() {
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(TEST_PROD_TAG);
    subDefOptional
        .flatMap(subDef -> subDef.getMetric(MetricIdUtils.getCores().getValue()))
        .ifPresent(tag -> this.tag = tag);
  }

  @Test
  void buildsPromQLByAccountLookupTemplateKey() {
    OffsetDateTime expectedDate = OffsetDateTime.now();
    mockPrometheusServiceRangeQuery(expectedDate, buildAccountQueryResult(List.of("A1")));

    accountSource.getMarketplaceAccounts(
        TEST_PROD_TAG, MetricIdUtils.getCores(), expectedDate.minusHours(1), expectedDate);
    verify(service)
        .runRangeQuery(
            eq(queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag))),
            eq(expectedDate),
            eq(expectedDate),
            eq(3600),
            eq(metricProperties.queryTimeout()),
            any());
  }

  @Test
  void filtersOutNullAndEmptyAccounts() {
    String expectedAccount = "ACCOUNT_1";
    OffsetDateTime expectedDate = OffsetDateTime.now();

    // Note List.of(...) does not accept null
    List<String> accountList = new LinkedList<>();
    accountList.add(null);
    accountList.add("");
    accountList.add(expectedAccount);

    mockPrometheusServiceRangeQuery(expectedDate, buildAccountQueryResult(accountList));

    Set<String> accounts =
        accountSource.getMarketplaceAccounts(
            TEST_PROD_TAG, MetricIdUtils.getCores(), expectedDate.minusHours(1), expectedDate);
    assertEquals(1, accounts.size());
    assertTrue(accounts.contains(expectedAccount));
  }

  @Test
  void getAllAccounts() {
    List<String> expectedAccounts = List.of("A1", "A2");
    OffsetDateTime expectedDate = OffsetDateTime.now();
    mockPrometheusServiceRangeQuery(expectedDate, buildAccountQueryResult(expectedAccounts));
    Set<String> accounts =
        accountSource.getMarketplaceAccounts(
            TEST_PROD_TAG, MetricIdUtils.getCores(), expectedDate.minusHours(1), expectedDate);
    assertEquals(2, accounts.size());
    assertTrue(accounts.containsAll(expectedAccounts));
  }

  @SuppressWarnings("unchecked")
  private void mockPrometheusServiceRangeQuery(OffsetDateTime date, QueryResult queryResult) {
    doAnswer(
            answer -> {
              var prometheusServiceResult = QuerySummaryResult.builder();
              prometheusServiceResult.status(queryResult.getStatus());
              prometheusServiceResult.errorType(queryResult.getErrorType());
              prometheusServiceResult.error(queryResult.getError());

              var itemConsumer = (Consumer<QueryResultDataResultInner>) answer.getArgument(5);
              if (queryResult.getData() != null) {
                prometheusServiceResult.resultType(queryResult.getData().getResultType());
                if (queryResult.getData().getResult() != null) {
                  prometheusServiceResult.numOfResults(queryResult.getData().getResult().size());
                  for (var item : queryResult.getData().getResult()) {
                    itemConsumer.accept(item);
                  }
                }
              }

              return prometheusServiceResult.build();
            })
        .when(service)
        .runRangeQuery(
            eq(queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag))),
            eq(date),
            eq(date),
            eq(3600),
            eq(metricProperties.queryTimeout()),
            any());
  }

  private QueryResult buildAccountQueryResult(List<String> orgIds) {
    QueryResultData resultData = new QueryResultData().resultType(ResultType.MATRIX);
    orgIds.forEach(
        account -> {
          resultData.addResultItem(
              new QueryResultDataResultInner().putMetricItem("external_organization", account));
        });
    return new QueryResult().status(StatusType.SUCCESS).data(resultData);
  }
}
