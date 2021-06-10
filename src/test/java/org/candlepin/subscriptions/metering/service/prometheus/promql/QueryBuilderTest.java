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
import org.candlepin.subscriptions.files.TagMetric;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QueryBuilderTest {

  @Autowired private PrometheusMetricsProperties props2;

  @Test
  void testBuildQuery() {
    String templateKey = "test_template";
    String template = "Account: #{runtime['account']} Metric ID: #{tag.metricId}";

    String metricId = "CORES";
    String account = "12345";

    PrometheusMetricsProperties props = new PrometheusMetricsProperties();
    props.getQueryTemplates().put(templateKey, template);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put("account", account);

    QueryDescriptor queryDesc =
        QueryDescriptor.builder()
            .tag(
                TagMetric.builder()
                    .prometheusQueryTemplateKey(templateKey)
                    .metricId(metricId)
                    .build())
            .runtime(runtimeParams)
            .build();

    QueryBuilder builder = new QueryBuilder(props);
    String query = builder.build(queryDesc);
    assertEquals(query, String.format("Account: %s Metric ID: %s", account, metricId));
  }

  @Test
  void testExceptionWhenInvalidTemplateSpecified() {
    String key = "UNKNOWN_KEY";
    QueryBuilder builder = new QueryBuilder(new PrometheusMetricsProperties());
    Throwable e =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              builder.build(
                  QueryDescriptor.builder()
                      .tag(TagMetric.builder().prometheusQueryTemplateKey(key).build())
                      .build());
            });

    assertEquals(
        String.format(
            "The query descriptor's tag did not define an existing template key: %s", key),
        e.getMessage());
  }

  @Test
  void supportsNestedExpressions() {
    String templateKey = "test_template";
    String template = "#{runtime['account_exp']} #{runtime['metric_exp']}";

    String accountExp = "Account: #{runtime['account']}";
    String metricExp = "Metric ID: #{tag.metricId}";

    String metricId = "CORES";
    String account = "12345";

    PrometheusMetricsProperties props = new PrometheusMetricsProperties();
    props.getQueryTemplates().put(templateKey, template);

    Map<String, String> runtimeParams = new HashMap<>();
    runtimeParams.put("account", account);
    // TODO [mstead] These should come from the tag data once in place.
    runtimeParams.put("account_exp", accountExp);
    runtimeParams.put("metric_exp", metricExp);

    QueryDescriptor queryDesc =
        QueryDescriptor.builder()
            .tag(
                TagMetric.builder()
                    .prometheusQueryTemplateKey(templateKey)
                    .metricId(metricId)
                    .build())
            .runtime(runtimeParams)
            .build();

    QueryBuilder builder = new QueryBuilder(props);
    String query = builder.build(queryDesc);
    assertEquals(query, String.format("Account: %s Metric ID: %s", account, metricId));
  }
}
