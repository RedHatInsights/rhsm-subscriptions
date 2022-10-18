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

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.prometheus.model.QueryResult;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;
import org.springframework.util.StringUtils;

/** Provides account lists from Prometheus metrics. */
@Slf4j
public class PrometheusAccountSource {

  private PrometheusService service;
  private MetricProperties metricProperties;
  private TagProfile tagProfile;
  private QueryBuilder queryBuilder;

  public PrometheusAccountSource(
      PrometheusService service,
      MetricProperties metricProperties,
      QueryBuilder queryBuilder,
      TagProfile tagProfile) {
    this.service = service;
    this.metricProperties = metricProperties;
    this.queryBuilder = queryBuilder;
    this.tagProfile = tagProfile;
  }

  public Set<String> getMarketplaceAccounts(
      String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    log.debug("Querying for active accounts for range [{}, {})", start, end);
    QueryResult result =
        service.runRangeQuery(
            buildQuery(productTag, metric),
            start.plusHours(1),
            end,
            metricProperties.getStep(),
            metricProperties.getQueryTimeout());
    return result.getData().getResult().stream()
        .map(r -> r.getMetric().getOrDefault("external_organization", ""))
        .filter(StringUtils::hasText)
        .collect(Collectors.toSet());
  }

  private String buildQuery(String productTag, Uom metric) {
    Optional<TagMetric> tag = tagProfile.getTagMetric(productTag, metric);
    if (tag.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Could not find TagMetric for %s %s", productTag, metric));
    }
    return queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tag.get()));
  }
}
