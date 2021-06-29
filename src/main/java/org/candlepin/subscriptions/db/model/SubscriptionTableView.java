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
package org.candlepin.subscriptions.db.model;

import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;

/**
 * A data projection around SubscriptionCapacity and Offering necessary to give us a view of the
 * data to be returned in the Subscription Table API.
 */
public interface SubscriptionTableView {

  @Value("#{target.capacity.ownerId}")
  String getOwnerId();

  @Value("#{target.capacity.productId}")
  String getProductId();

  @Value("#{target.capacity.subscriptionId}")
  String getSubscriptionId();

  @Value("#{target.capacity.accountNumber}")
  String getAccountNumber();

  @Value("#{target.capacity.physicalSockets}")
  Integer getPhysicalSockets();

  @Value("#{target.capacity.physicalCores}")
  Integer getPhysicalCores();

  @Value("#{target.capacity.virtualSockets}")
  Integer getVirtualSockets();

  @Value("#{target.capacity.virtualCores}")
  Integer getVirtualCores();

  @Value("#{target.capacity.hasUnlimitedGuestSockets}")
  boolean isHasUnlimitedGuestSockets();

  @Value("#{target.capacity.beginDate}")
  OffsetDateTime getBeginDate();

  @Value("#{target.capacity.endDate}")
  OffsetDateTime getEndDate();

  @Value("#{target.capacity.sku}")
  String getSku();

  @Value("#{target.capacity.sla}")
  ServiceLevel getServiceLevel();

  @Value("#{target.capacity.usage}")
  Usage getUsage();
}
