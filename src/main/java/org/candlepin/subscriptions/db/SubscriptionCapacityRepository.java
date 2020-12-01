/*
 * Copyright (c) 2019 Red Hat, Inc.
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
package org.candlepin.subscriptions.db;

import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.SubscriptionCapacity;
import org.candlepin.subscriptions.db.model.SubscriptionCapacityKey;

import org.candlepin.subscriptions.db.model.SubscriptionView;
import org.candlepin.subscriptions.db.model.Usage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Repository for subscription-provided product capacities.
 */
public interface SubscriptionCapacityRepository extends
    JpaRepository<SubscriptionCapacity, SubscriptionCapacityKey>,
    CustomizedSubscriptionCapacityRepository {

    List<SubscriptionCapacity> findByKeyOwnerIdAndKeySubscriptionIdIn(String ownerId,
        List<String> subscriptionIds);

    @Query(
            value = "SELECT b.sku AS sku," +
                    " min(b.endDate) AS endDate," +
                    " max(b.beginDate) AS beginDate," +
                    " max(b.physicalSockets) AS physicalSockets," +
                    " max(b.virtualSockets) AS virtualSockets," +
                    " max(b.physicalCores) AS physicalCores," +
                    " max(b.virtualCores) AS virtualCores" +
                    " FROM SubscriptionCapacity b" +
                    " WHERE b.accountNumber = :account" +
                    " AND b.key.productId = :product" +
                    " AND b.usage = :sanitizedUsage" +
                    " AND b.serviceLevel = :sanitizedSla" +
                    " AND b.beginDate < current_date" +
                    " AND b.endDate > current_date" +
                    " GROUP BY b.sku",
            countQuery = "SELECT count(b.sku)" +
                    " FROM SubscriptionCapacity b" +
                    " WHERE b.accountNumber = :account" +
                    " AND b.key.productId = :product" +
                    " AND b.usage = :sanitizedUsage" +
                    " AND b.serviceLevel = :sanitizedSla" +
                    " AND b.beginDate < current_date" +
                    " AND b.endDate > current_date" +
                    " GROUP BY b.sku"
    )
    Page<SubscriptionView> getSubscriptionViews(
            String account, String product, ServiceLevel sanitizedSla, Usage sanitizedUsage, Pageable page);

}
