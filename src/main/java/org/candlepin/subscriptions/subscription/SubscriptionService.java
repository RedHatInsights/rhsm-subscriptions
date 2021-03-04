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

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The Subscription Service wrapper for all subscription service interfaces.
 */
@Component
public class SubscriptionService {

    private final SearchApi searchApi;

    public SubscriptionService(SearchApi searchApi) {
        this.searchApi = searchApi;
    }

    /**
     * Obtain Subscription Service Subscription Models for an orgId.
     * @param accountNumber the account number of the customer. Also refered to as the Oracle account number.
     * @return a list of Subscription models.
     * @throws ApiException if an error occurrs in fulfilling this request.
     */
    public List<Subscription> getSubscriptions(String accountNumber) throws ApiException {
        String criteria = String.format(
            "criteria;oracle_account_number=%s;statusList=active;statusList=temporary", accountNumber
        );
        String options = "options;products=ALL";
        return searchApi.searchSubscriptions(criteria, options);
    }
}
