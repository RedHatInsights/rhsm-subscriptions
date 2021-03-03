/*
 * Copyright (c) 2021 Red Hat, Inc.
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

import org.candlepin.subscriptions.prometheus.resources.QueryApi;
import org.candlepin.subscriptions.prometheus.resources.QueryRangeApi;


/**
 * A prometheus Query API provider that returns stubbed data.
 */
public class StubApiProvider implements ApiProvider {

    private QueryApi queryApi;
    private QueryRangeApi rangeApi;

    public StubApiProvider() {
        this(new StubQueryApi(), new StubQueryRangeApi());
    }

    public StubApiProvider(QueryApi queryApi, QueryRangeApi rangeApi) {
        this.queryApi = queryApi;
        this.rangeApi = rangeApi;
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
