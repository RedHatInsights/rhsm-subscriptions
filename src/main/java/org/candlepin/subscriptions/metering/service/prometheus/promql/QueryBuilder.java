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

import java.util.Optional;
import org.candlepin.subscriptions.metering.service.prometheus.PrometheusMetricsProperties;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/** Builds PromQL queries based on a configured template. */
@Component
public class QueryBuilder {

  /**
   * The default metric query key. A query with this key must be defined in the config file as a
   * query template.
   *
   * @see PrometheusMetricsProperties
   */
  public static final String DEFAULT_METRIC_QUERY_KEY = "default";

  private final PrometheusMetricsProperties metricsProperties;

  public QueryBuilder(PrometheusMetricsProperties metricsProperties) {
    this.metricsProperties = metricsProperties;
  }

  public String build(QueryDescriptor queryDescriptor) {
    String templateKey = queryDescriptor.getTag().getQueryKey();
    Optional<String> template = metricsProperties.getQueryTemplate(templateKey);

    if (template.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Unable to find query template for key: %s", templateKey));
    }
    return buildQuery(template.get(), queryDescriptor);
  }

  public String buildAccountLookupQuery(QueryDescriptor queryDescriptor) {
    String templateKey = queryDescriptor.getTag().getAccountQueryKey();
    Optional<String> template = metricsProperties.getAccountQueryTemplate(templateKey);
    if (template.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Unable to find account query template for key: %s", templateKey));
    }
    return buildQuery(template.get(), queryDescriptor);
  }

  private String buildQuery(String template, QueryDescriptor descriptor) {
    ExpressionParser parser = new SpelExpressionParser();
    StandardEvaluationContext context = new StandardEvaluationContext(descriptor);

    // Only allow nested expressions based on a config setting. We need to do this
    // to prevent potential infinite recursion.
    String query = template;
    for (int i = 0; i < metricsProperties.getTemplateParameterDepth(); i++) {
      query = (String) parser.parseExpression(query, new TemplateParserContext()).getValue(context);
      if (query == null) {
        throw new IllegalStateException(
            String.format("Unable to parse query template! %s", template));
      }
    }
    return query;
  }
}
