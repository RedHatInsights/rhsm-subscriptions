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

import java.util.Map;
import java.util.Optional;
import org.candlepin.subscriptions.json.Measurement.Uom;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryBuilder;
import org.candlepin.subscriptions.metering.service.prometheus.promql.QueryDescriptor;
import org.candlepin.subscriptions.registry.TagMetric;
import org.candlepin.subscriptions.registry.TagProfile;

/** Common query utilities used for testing. */
public class QueryHelper {

  private TagProfile tagProfile;
  private QueryBuilder queryBuilder;

  public QueryHelper(TagProfile tagProfile, QueryBuilder builder) {
    this.tagProfile = tagProfile;
    this.queryBuilder = builder;
  }

  public String expectedQuery(String productTag, Map<String, String> queryParams) {
    Optional<TagMetric> tag = tagProfile.getTagMetric(productTag, Uom.CORES);
    if (tag.isEmpty()) {
      throw new RuntimeException("Bad test configuration! Could not find TagMetric!");
    }

    QueryDescriptor descriptor = new QueryDescriptor(tag.get());
    queryParams.forEach(descriptor::addRuntimeVar);
    return queryBuilder.build(descriptor);
  }
}
