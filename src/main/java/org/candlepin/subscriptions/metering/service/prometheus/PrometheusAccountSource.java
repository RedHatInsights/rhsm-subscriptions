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

import com.redhat.swatch.configuration.registry.Metric;
import com.redhat.swatch.configuration.registry.SubscriptionDefinition;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.springframework.util.StringUtils;

/** Provides account lists from Prometheus metrics. */
@Slf4j
public class PrometheusAccountSource {

  private PrometheusService service;
  private MetricProperties metricProperties;
  private QueryBuilder queryBuilder;

  public PrometheusAccountSource(
      PrometheusService service, MetricProperties metricProperties, QueryBuilder queryBuilder) {
    this.service = service;
    this.metricProperties = metricProperties;
    this.queryBuilder = queryBuilder;
  }

  public Set<String> getMarketplaceAccounts(
      String productTag, Uom metric, OffsetDateTime start, OffsetDateTime end) {
    log.debug("Querying for active accounts for range [{}, {})", start, end);
    Set<String> accounts = new HashSet<>();
    service.runRangeQuery(
        buildQuery(productTag, metric),
        start.plusHours(1),
        end,
        metricProperties.getStep(),
        metricProperties.getQueryTimeout(),
        item -> {
          String organization = item.getMetric().get("external_organization");
          if (StringUtils.hasText(organization)) {
            accounts.add(organization);
          }
        });

    return accounts;
  }

  private String buildQuery(String productTag, Uom metric) {
    var subDefOptional = SubscriptionDefinition.lookupSubscriptionByTag(productTag);
    Optional<Metric> tagMetric = subDefOptional.flatMap(subDef -> subDef.getMetric(metric.value()));
    if (tagMetric.isEmpty()) {
      throw new IllegalArgumentException(
          String.format("Could not find tag %s and metric %s!", productTag, metric));
    }

    return queryBuilder.buildAccountLookupQuery(new QueryDescriptor(tagMetric.get()));
  }
}
