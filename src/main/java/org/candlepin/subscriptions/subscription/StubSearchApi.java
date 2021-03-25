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
package org.candlepin.subscriptions.subscription;

import org.candlepin.subscriptions.subscription.api.model.Subscription;
import org.candlepin.subscriptions.subscription.api.resources.SearchApi;

import java.util.Collections;
import java.util.List;

/**
 * Stub version of the SearchApi for the Subscription service for local testing.
 */
public class StubSearchApi extends SearchApi {

    @Override
    public Subscription getSubscriptionById(String id) throws ApiException {
        return createData();
    }

    @Override
    public List<Subscription> searchSubscriptionsByAccountNumber(String accountNumber, Integer index,
        Integer pageSize) throws ApiException {
        return Collections.singletonList(createData());
    }

    @Override
    public List<Subscription> searchSubscriptionsByOrgId(String orgId, Integer index, Integer pageSize)
        throws ApiException {
        return Collections.singletonList(createData());
    }

    private Subscription createData() {
        return new Subscription().subscriptionNumber("2253591");
    }
}
