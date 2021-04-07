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
     * Object a subscription model by ID.
     * @param id the Subscription ID.
     * @return a subscription model.
     * @throws ApiException if an error occurs in fulfilling this request.
     */
    public Subscription getSubscriptionById(String id) throws ApiException {
        return searchApi.getSubscriptionById(id);
    }

    /**
     * Obtain Subscription Service Subscription Models for an orgId.
     * @param accountNumber the account number of the customer. Also refered to as the Oracle account number.
     * @param index the starting index for results.
     * @param pageSize the number of results in the page.
     * @return a list of Subscription models.
     * @throws ApiException if an error occurs in fulfilling this request.
     */
    public List<Subscription> getSubscriptionsByAccountNumber(String accountNumber, int index, int pageSize)
        throws ApiException {
        return searchApi.searchSubscriptionsByAccountNumber(accountNumber, index, pageSize);
    }

    /**
     * Obtain Subscription Service Subscription Models for an orgId.
     * @param orgId the orgId of the customer.
     * @param index the starting index for results.
     * @param pageSize the number of results in the page.
     * @return a list of Subscription models.
     * @throws ApiException if an error occurs in fulfilling this request.
     */
    public List<Subscription> getSubscriptionsByOrgId(String orgId, int index, int pageSize)
        throws ApiException {
        return searchApi.searchSubscriptionsByOrgId(orgId, index, pageSize);
    }
}
