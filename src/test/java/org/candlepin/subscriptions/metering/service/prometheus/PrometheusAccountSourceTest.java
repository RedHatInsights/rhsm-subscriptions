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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.configuration.util.MetricIdUtils;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.candlepin.subscriptions.metering.service.prometheus.model.QuerySummaryResult;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.prometheus.model.QueryResultData;
import org.candlepin.subscriptions.prometheus.model.QueryResultDataResultInner;
import org.candlepin.subscriptions.prometheus.model.ResultType;
import org.candlepin.subscriptions.prometheus.model.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrometheusAccountSourceTest {

  final String TEST_ACCT_QUERY_KEY = "default";
  final String TEST_ACCOUNT_QUERY = "default";
  final String TEST_PROD_TAG = "OpenShift-dedicated-metrics";

  @Mock PrometheusService service;

  PrometheusAccountSource accountSource;
  MetricProperties metricProperties;
  QueryBuilder queryBuilder;
  Metric tag;

  @BeforeEach
  void setupTest() {

    metricProperties = new MetricProperties();
    metricProperties.setAccountQueryTemplates(Map.of(TEST_ACCT_QUERY_KEY, TEST_ACCOUNT_QUERY));

    queryBuilder = new QueryBuilder(metricProperties);
    accountSource = new PrometheusAccountSource(service, metricProperties, queryBuilder);

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
            eq(metricProperties.getQueryTimeout()),
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
            eq(metricProperties.getQueryTimeout()),
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
