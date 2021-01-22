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
package org.candlepin.subscriptions.db;

import org.candlepin.subscriptions.db.model.Subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Repository for Subscription Entities */
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

  /**
   * Object a set of subscriptions
   *
   * @param ownerId the ownerId of the subscriptions
   * @param subscriptionIds the list of subscriptionIds to filter on
   * @return a list of subscriptions with the specified ownerId and a subscriptionId from the
   *     provided list
   */
  List<Subscription> findByOwnerIdAndSubscriptionIdIn(String ownerId, List<String> subscriptionIds);
}
