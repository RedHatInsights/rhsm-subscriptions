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
package com.redhat.swatch.metrics.utils;

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import com.redhat.swatch.metrics.service.promql.QueryBuilder;
import com.redhat.swatch.metrics.service.promql.QueryDescriptor;
import java.util.Map;
import java.util.Optional;

/** Common query utilities used for testing. */
public class QueryHelper {
  private final QueryBuilder queryBuilder;

  public QueryHelper(QueryBuilder builder) {
    this.queryBuilder = builder;
  }

  public String expectedQuery(String productTag, Map<String, String> queryParams) {
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(productTag);
    Optional<Metric> tagMetric = subDefOptional.flatMap(subDef -> subDef.getMetric("Cores"));
    if (tagMetric.isEmpty()) {
      throw new RuntimeException("Bad test configuration! Could not find Tag with Metric!");
    }

    QueryDescriptor descriptor = new QueryDescriptor(tagMetric.get());
    queryParams.forEach(descriptor::addRuntimeVar);
    return queryBuilder.build(descriptor);
  }
}
