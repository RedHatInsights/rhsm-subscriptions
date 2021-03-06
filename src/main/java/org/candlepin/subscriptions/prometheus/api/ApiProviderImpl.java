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
package org.candlepin.subscriptions.prometheus.api;

import org.candlepin.subscriptions.prometheus.ApiClient;
import org.candlepin.subscriptions.prometheus.resources.QueryApi;
import org.candlepin.subscriptions.prometheus.resources.QueryRangeApi;

/**
 * The default API prometheus API provider implementation that will connect to a Thanos/Prometheus
 * instance via a generated client.
 */
public class ApiProviderImpl implements ApiProvider {

  private QueryApi queryApi;
  private QueryRangeApi rangeApi;

  public ApiProviderImpl(ApiClient apiClient) {
    queryApi = new QueryApi(apiClient);
    rangeApi = new QueryRangeApi(apiClient);
  }

  @Override
  public QueryApi queryApi() {
    return this.queryApi;
  }

  @Override
  public QueryRangeApi queryRangeApi() {
    return this.rangeApi;
  }
}
