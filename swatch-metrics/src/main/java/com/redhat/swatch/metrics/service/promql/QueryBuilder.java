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
package com.redhat.swatch.metrics.service.promql;

import com.redhat.swatch.metrics.configuration.MetricProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

/** Builds PromQL queries based on a configured template. */
@Slf4j
@ApplicationScoped
public class QueryBuilder {
  private static final Map<String, BiFunction<QueryDescriptor, String, String>> KEY_VALUE_RULES =
      Map.of(
          "#{metric.prometheus.queryParams[",
              (descriptor, key) -> descriptor.getMetric().getPrometheus().getQueryParams().get(key),
          "#{runtime[", (descriptor, key) -> descriptor.getRuntime().get(key));

  private static final Map<String, Function<QueryDescriptor, String>> DIRECT_RULES =
      Map.of(
          "#{metric.id}", descriptor -> descriptor.getMetric().getId(),
          "#{metric.rhmMetricId}", descriptor -> descriptor.getMetric().getRhmMetricId(),
          "#{metric.awsDimension}", descriptor -> descriptor.getMetric().getAwsDimension(),
          "#{metric.prometheus.queryKey}",
              descriptor -> descriptor.getMetric().getPrometheus().getQueryKey());
  private static final String KEY_VALUE_CLOSE_TAG = "]}";
  private static final int INDEX_NOT_FOUND = -1;

  @Inject MetricProperties metricProperties;

  public String build(QueryDescriptor queryDescriptor) {
    String templateKey = queryDescriptor.getMetric().getPrometheus().getQueryKey();
    Optional<String> template = metricProperties.getQueryTemplate(templateKey);

    if (template.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Unable to find query template for key: %s", templateKey));
    }
    log.debug("Building metric lookup PromQL.");
    return buildQuery(template.get(), queryDescriptor);
  }

  public String buildAccountLookupQuery(QueryDescriptor queryDescriptor) {
    String templateKey = queryDescriptor.getMetric().getPrometheus().getQueryKey();
    Optional<String> template = metricProperties.getAccountQueryTemplate(templateKey);
    if (template.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Unable to find account query template for key: %s", templateKey));
    }
    log.debug("Building account lookup PromQL.");
    return buildQuery(template.get(), queryDescriptor);
  }

  private String buildQuery(String query, QueryDescriptor descriptor) {
    // Support of key-value properties that use a map as collection. The expected format is:
    // "#{map[key]}"
    for (var rule : KEY_VALUE_RULES.entrySet()) {
      if (query.contains(rule.getKey())) {
        String key = substringBetween(query, rule.getKey());
        String value = rule.getValue().apply(descriptor, key);
        query = query.replace(rule.getKey() + key + KEY_VALUE_CLOSE_TAG, value);
        return buildQuery(query, descriptor);
      }
    }

    // Support of direct properties that is mapped with instance values. The expected format is:
    // "#{instance.key}"
    for (var rule : DIRECT_RULES.entrySet()) {
      if (query.contains(rule.getKey())) {
        String value = rule.getValue().apply(descriptor);
        query = query.replace(rule.getKey(), value);
        return buildQuery(query, descriptor);
      }
    }

    log.debug("PromQL: {}", query);
    return query;
  }

  private static String substringBetween(String str, String open) {
    final int start = str.indexOf(open);
    if (start != INDEX_NOT_FOUND) {
      final int end = str.indexOf(KEY_VALUE_CLOSE_TAG, start + open.length());
      if (end != INDEX_NOT_FOUND) {
        return str.substring(start + open.length(), end);
      }
    }
    return null;
  }
}
