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
package org.candlepin.subscriptions.metering.service.prometheus.promql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.candlepin.subscriptions.metering.service.prometheus.MetricProperties;
import org.candlepin.subscriptions.registry.TagMetric;
import org.junit.jupiter.api.Test;

class QueryBuilderTest {

  @Test
  void testBuildQuery() {
    String templateKey = "test_template";
    String template =
        "Account: #{runtime[account]} Metric ID: #{metric.metricId} P1: #{metric.queryParams[p1]}";

    String metricId = "CORES";
    String account = "12345";
    String param1 = "PARAM_1";

    MetricProperties props = new MetricProperties();
    props.getQueryTemplates().put(templateKey, template);

    Map<String, String> params = new HashMap<>();
    params.put("p1", param1);

    TagMetric tagMetric =
        TagMetric.builder().queryKey(templateKey).metricId(metricId).queryParams(params).build();

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put("account", account);

    QueryDescriptor queryDesc = new QueryDescriptor(tagMetric);
    queryDesc.addRuntimeVar("account", account);

    QueryBuilder builder = new QueryBuilder(props);
    assertEquals(
        String.format("Account: %s Metric ID: %s P1: %s", account, metricId, param1),
        builder.build(queryDesc));
  }

  @Test
  void testExceptionWhenInvalidTemplateSpecified() {
    String key = "UNKNOWN_KEY";
    QueryBuilder builder = new QueryBuilder(new MetricProperties());
    QueryDescriptor descriptor = new QueryDescriptor(TagMetric.builder().queryKey(key).build());
    Throwable e = assertThrows(IllegalArgumentException.class, () -> builder.build(descriptor));

    assertEquals(String.format("Unable to find query template for key: %s", key), e.getMessage());
  }

  @Test
  void supportsNestedExpressions() {
    String templateKey = "test_template";
    String template = "#{metric.queryParams[account_exp]} #{metric.queryParams[metric_exp]}";

    String accountExp = "Account: #{runtime[account]}";
    String metricExp = "Metric ID: #{metric.metricId}";

    String metricId = "CORES";
    String account = "12345";

    MetricProperties props = new MetricProperties();
    props.getQueryTemplates().put(templateKey, template);

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("account_exp", accountExp);
    queryParams.put("metric_exp", metricExp);

    QueryDescriptor queryDesc =
        new QueryDescriptor(
            TagMetric.builder()
                .queryKey(templateKey)
                .metricId(metricId)
                .queryParams(queryParams)
                .build());
    queryDesc.addRuntimeVar("account", account);

    QueryBuilder builder = new QueryBuilder(props);
    String query = builder.build(queryDesc);
    assertEquals(String.format("Account: %s Metric ID: %s", account, metricId), query);
  }
}
